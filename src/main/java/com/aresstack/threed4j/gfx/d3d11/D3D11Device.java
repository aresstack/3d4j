package com.aresstack.threed4j.gfx.d3d11;

import com.aresstack.threed4j.ffm.Native;
import com.aresstack.threed4j.gfx.GraphicsDevice;
import com.aresstack.threed4j.gfx.Mesh;
import com.aresstack.threed4j.gfx.PixelImage;
import com.aresstack.threed4j.gfx.Shader;
import com.aresstack.threed4j.gfx.ShaderLayout;
import com.aresstack.threed4j.platform.win32.Win32Window;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static com.aresstack.threed4j.gfx.d3d11.D3D11Api.*;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Direct3D 11 backend for Windows.
 *
 * <p>It first asks for the hardware driver and falls back to WARP, the software
 * rasterizer that ships with Windows. It therefore also runs where no graphics
 * driver is usable: virtual machines, remote sessions, locked-down PCs.</p>
 */
public final class D3D11Device implements GraphicsDevice {

    private final Arena arena = Arena.ofConfined();
    private final boolean vsync;

    private final ComObject device;
    private final ComObject context;
    private final ComObject swapChain;
    private final MemorySegment rasterizerState;
    private final String description;

    /** {@code IID_ID3D11Texture2D}, needed to get at the back buffer. */
    private final MemorySegment texture2DId;

    // Views of the back buffer and its depth buffer; recreated when the window is resized.
    private MemorySegment renderTargetView = MemorySegment.NULL;
    private MemorySegment depthTexture = MemorySegment.NULL;
    private MemorySegment depthStencilView = MemorySegment.NULL;
    private int width;
    private int height;

    // Arguments of per-frame calls, allocated once.
    private final MemorySegment renderTargetArray;
    private final MemorySegment viewport;
    private final MemorySegment clearColor;

    public D3D11Device(Win32Window window, boolean vsync) {
        this.vsync = vsync;
        this.width = window.width();
        this.height = window.height();
        texture2DId = guid(arena, 0x6f15aaf2, 0xd208, 0x4e89, 0x9a, 0xb4, 0x48, 0x95, 0x35, 0xd3, 0x4f, 0x9c);
        renderTargetArray = arena.allocate(ADDRESS);
        viewport = Native.bytes(arena, SIZEOF_VIEWPORT);
        clearColor = Native.floats(arena, 0f, 0f, 0f, 1f);

        MemorySegment swapChainDesc = Native.bytes(arena, SIZEOF_SWAP_CHAIN_DESC);
        swapChainDesc.set(JAVA_INT, SCD_WIDTH, width);
        swapChainDesc.set(JAVA_INT, SCD_HEIGHT, height);
        swapChainDesc.set(JAVA_INT, SCD_FORMAT, DXGI_FORMAT_R8G8B8A8_UNORM);
        swapChainDesc.set(JAVA_INT, SCD_SAMPLE_COUNT, 1);
        swapChainDesc.set(JAVA_INT, SCD_BUFFER_USAGE, DXGI_USAGE_RENDER_TARGET_OUTPUT);
        swapChainDesc.set(JAVA_INT, SCD_BUFFER_COUNT, 1);
        swapChainDesc.set(ADDRESS, SCD_OUTPUT_WINDOW, window.handle());
        swapChainDesc.set(JAVA_INT, SCD_WINDOWED, 1);
        swapChainDesc.set(JAVA_INT, SCD_SWAP_EFFECT, DXGI_SWAP_EFFECT_DISCARD);

        String driver = "hardware";
        DeviceObjects objects = createDeviceAndSwapChain(D3D_DRIVER_TYPE_HARDWARE, swapChainDesc);
        if (objects == null) {
            driver = "WARP software rasterizer";
            objects = createDeviceAndSwapChain(D3D_DRIVER_TYPE_WARP, swapChainDesc);
        }
        if (objects == null) {
            arena.close();
            throw new IllegalStateException("Direct3D 11 is not available: neither a hardware device nor WARP could be created");
        }
        device = objects.device();
        context = objects.context();
        swapChain = objects.swapChain();
        description = "Direct3D 11 (feature level " + featureLevelName(objects.featureLevel()) + ", "
                + driver + ") | " + adapterName();

        // Same conventions as OpenGL: counter-clockwise triangles face the viewer, back faces are culled.
        MemorySegment rasterizerDesc = Native.bytes(arena, SIZEOF_RASTERIZER_DESC);
        rasterizerDesc.set(JAVA_INT, RD_FILL_MODE, D3D11_FILL_SOLID);
        rasterizerDesc.set(JAVA_INT, RD_CULL_MODE, D3D11_CULL_BACK);
        rasterizerDesc.set(JAVA_INT, RD_FRONT_COUNTER_CLOCKWISE, 1);
        rasterizerDesc.set(JAVA_INT, RD_DEPTH_CLIP_ENABLE, 1);
        rasterizerState = createRasterizerState(device, rasterizerDesc);

        createTargets();
    }

    private static String featureLevelName(int level) {
        return (level >> 12) + "_" + ((level >> 8) & 0xF);
    }

    /** Name of the graphics adapter the device runs on, e.g. the GPU model. */
    private String adapterName() {
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment dxgiDeviceId = guid(temp, 0x54ec77fa, 0x1377, 0x44e6,
                    0x8c, 0x32, 0x88, 0xfd, 0x5f, 0x44, 0xc8, 0x4c);
            ComObject dxgiDevice = new ComObject(queryInterface(device, dxgiDeviceId), 8);
            ComObject adapter = new ComObject(getAdapter(dxgiDevice), 9);
            MemorySegment desc = Native.bytes(temp, SIZEOF_ADAPTER_DESC);
            getAdapterDesc(adapter, desc);
            adapter.release();
            dxgiDevice.release();
            return Native.readWideString(desc, 128);
        } catch (RuntimeException e) {
            return "unknown adapter";
        }
    }

    /** Creates the render target view of the back buffer and a matching depth buffer. */
    private void createTargets() {
        MemorySegment backBuffer = getBuffer(swapChain, texture2DId);
        renderTargetView = createRenderTargetView(device, backBuffer);
        ComObject.release(backBuffer);

        try (Arena temp = Arena.ofConfined()) {
            MemorySegment desc = textureDesc(temp, DXGI_FORMAT_D24_UNORM_S8_UINT);
            desc.set(JAVA_INT, TD_USAGE, D3D11_USAGE_DEFAULT);
            desc.set(JAVA_INT, TD_BIND_FLAGS, D3D11_BIND_DEPTH_STENCIL);
            depthTexture = createTexture2D(device, desc);
        }
        depthStencilView = createDepthStencilView(device, depthTexture);
        renderTargetArray.set(ADDRESS, 0, renderTargetView);
    }

    private MemorySegment textureDesc(Arena temp, int format) {
        MemorySegment desc = Native.bytes(temp, SIZEOF_TEXTURE2D_DESC);
        desc.set(JAVA_INT, TD_WIDTH, width);
        desc.set(JAVA_INT, TD_HEIGHT, height);
        desc.set(JAVA_INT, TD_MIP_LEVELS, 1);
        desc.set(JAVA_INT, TD_ARRAY_SIZE, 1);
        desc.set(JAVA_INT, TD_FORMAT, format);
        desc.set(JAVA_INT, TD_SAMPLE_COUNT, 1);
        return desc;
    }

    private void releaseTargets() {
        omSetRenderTarget(context, MemorySegment.NULL, MemorySegment.NULL);
        ComObject.release(depthStencilView);
        ComObject.release(depthTexture);
        ComObject.release(renderTargetView);
        depthStencilView = depthTexture = renderTargetView = MemorySegment.NULL;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public void beginFrame(int newWidth, int newHeight) {
        if (newWidth != width || newHeight != height) {
            // The swap chain refuses to resize while anything still references its buffers.
            releaseTargets();
            width = newWidth;
            height = newHeight;
            resizeBuffers(swapChain, width, height);
            createTargets();
        }
        viewport.set(JAVA_FLOAT, 0, 0f);
        viewport.set(JAVA_FLOAT, 4, 0f);
        viewport.set(JAVA_FLOAT, 8, width);
        viewport.set(JAVA_FLOAT, 12, height);
        viewport.set(JAVA_FLOAT, 16, 0f);
        viewport.set(JAVA_FLOAT, 20, 1f);
        omSetRenderTarget(context, renderTargetArray, depthStencilView);
        rsSetViewport(context, viewport);
        rsSetState(context, rasterizerState);
    }

    @Override
    public void clear(float red, float green, float blue) {
        clearColor.set(JAVA_FLOAT, 0, red);
        clearColor.set(JAVA_FLOAT, 4, green);
        clearColor.set(JAVA_FLOAT, 8, blue);
        clearRenderTargetView(context, renderTargetView, clearColor);
        clearDepthStencilView(context, depthStencilView);
    }

    @Override
    public Shader createShader(String name, ShaderLayout layout) {
        return new D3D11Shader(device, context, name, layout);
    }

    @Override
    public Mesh createMesh(float[] vertices, int[] indices, int... attributeSizes) {
        return new D3D11Mesh(device, context, vertices, indices, attributeSizes);
    }

    @Override
    public void draw(Mesh mesh, Shader shader) {
        ((D3D11Shader) shader).bind();
        ((D3D11Mesh) mesh).draw();
    }

    /** The GPU cannot hand out the back buffer directly: copy it to a CPU-readable texture first. */
    @Override
    public PixelImage readPixels() {
        int[] rgb = new int[width * height];
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment desc = textureDesc(temp, DXGI_FORMAT_R8G8B8A8_UNORM);
            desc.set(JAVA_INT, TD_USAGE, D3D11_USAGE_STAGING);
            desc.set(JAVA_INT, TD_CPU_ACCESS_FLAGS, D3D11_CPU_ACCESS_READ);
            MemorySegment staging = createTexture2D(device, desc);
            MemorySegment backBuffer = getBuffer(swapChain, texture2DId);
            copyResource(context, staging, backBuffer);
            ComObject.release(backBuffer);

            MemorySegment mapped = Native.bytes(temp, SIZEOF_MAPPED_SUBRESOURCE);
            mapForRead(context, staging, mapped);
            int rowPitch = mapped.get(JAVA_INT, MS_ROW_PITCH);
            MemorySegment pixels = Native.sized(mapped.get(ADDRESS, 0), (long) rowPitch * height);
            for (int y = 0; y < height; y++) {
                long row = (long) y * rowPitch; // Direct3D rows start at the top
                for (int x = 0; x < width; x++) {
                    long offset = row + x * 4L;
                    int r = pixels.get(JAVA_BYTE, offset) & 0xFF;
                    int g = pixels.get(JAVA_BYTE, offset + 1) & 0xFF;
                    int b = pixels.get(JAVA_BYTE, offset + 2) & 0xFF;
                    rgb[y * width + x] = (r << 16) | (g << 8) | b;
                }
            }
            unmap(context, staging);
            ComObject.release(staging);
        }
        return new PixelImage(width, height, rgb);
    }

    @Override
    public void present() {
        D3D11Api.present(swapChain, vsync ? 1 : 0);
    }

    @Override
    public void close() {
        releaseTargets();
        ComObject.release(rasterizerState);
        swapChain.release();
        context.release();
        device.release();
        arena.close();
    }
}
