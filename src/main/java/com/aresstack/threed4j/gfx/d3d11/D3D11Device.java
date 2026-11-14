package com.aresstack.threed4j.gfx.d3d11;

import com.aresstack.threed4j.ffm.Native;
import com.aresstack.threed4j.gfx.GraphicsDevice;
import com.aresstack.threed4j.gfx.Mesh;
import com.aresstack.threed4j.gfx.PixelImage;
import com.aresstack.threed4j.gfx.Shader;
import com.aresstack.threed4j.gfx.ShaderLayout;
import com.aresstack.threed4j.gfx.Texture;
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
 * <p>It asks for the hardware driver first. Where no graphics driver is usable
 * (virtual machines, remote sessions, locked-down PCs) Windows answers with WARP,
 * its software rasterizer, so the engine still runs – on the CPU.</p>
 */
public final class D3D11Device implements GraphicsDevice {

    private final Arena arena = Arena.ofConfined();
    private final boolean vsync;

    private final ComObject device;
    private final ComObject context;
    private final ComObject swapChain;
    private final MemorySegment rasterizerState;
    private final MemorySegment overlayBlendState;
    private final MemorySegment smoothSampler;
    private final MemorySegment blockySampler;
    private final String description;

    /** {@code IID_ID3D11Texture2D}, needed to get at the back buffer. */
    private final MemorySegment texture2DId;

    // The window's back buffer; its view is recreated when the window is resized.
    private MemorySegment backBufferView = MemorySegment.NULL;
    private int width;
    private int height;

    // The scene image with its depth buffer; recreated when its size changes.
    private MemorySegment sceneColor = MemorySegment.NULL;
    private MemorySegment sceneColorView = MemorySegment.NULL;
    private MemorySegment sceneDepth = MemorySegment.NULL;
    private MemorySegment sceneDepthView = MemorySegment.NULL;
    private D3D11Texture sceneTexture;
    private int sceneWidth;
    private int sceneHeight;

    private float renderScale = 1f;
    private boolean smoothUpscale = true;
    private boolean overlay;

    // Stretches the scene image over the window: a quad covering the screen and a shader that samples.
    private final D3D11Shader blitShader;
    private final D3D11Mesh blitQuad;

    // Arguments of per-frame calls, allocated once.
    private final MemorySegment renderTargetArray;
    private final MemorySegment nullArray;
    private final MemorySegment viewport;
    private final MemorySegment clearColor;

    public D3D11Device(Win32Window window, boolean vsync) {
        this.vsync = vsync;
        this.width = window.width();
        this.height = window.height();
        texture2DId = guid(arena, 0x6f15aaf2, 0xd208, 0x4e89, 0x9a, 0xb4, 0x48, 0x95, 0x35, 0xd3, 0x4f, 0x9c);
        renderTargetArray = arena.allocate(ADDRESS);
        nullArray = arena.allocate(ADDRESS);
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

        DeviceObjects objects = createDeviceAndSwapChain(D3D_DRIVER_TYPE_HARDWARE, swapChainDesc);
        if (objects == null) {
            objects = createDeviceAndSwapChain(D3D_DRIVER_TYPE_WARP, swapChainDesc);
        }
        if (objects == null) {
            arena.close();
            throw new IllegalStateException("Direct3D 11 is not available: neither a hardware device nor WARP could be created");
        }
        device = objects.device();
        context = objects.context();
        swapChain = objects.swapChain();
        leaveAltEnterToTheWindow(swapChain, window.handle());
        description = "Direct3D 11 (feature level " + featureLevelName(objects.featureLevel()) + ") | "
                + describeAdapter();

        // Same conventions as OpenGL: counter-clockwise triangles face the viewer, back faces are culled.
        MemorySegment rasterizerDesc = Native.bytes(arena, SIZEOF_RASTERIZER_DESC);
        rasterizerDesc.set(JAVA_INT, RD_FILL_MODE, D3D11_FILL_SOLID);
        rasterizerDesc.set(JAVA_INT, RD_CULL_MODE, D3D11_CULL_BACK);
        rasterizerDesc.set(JAVA_INT, RD_FRONT_COUNTER_CLOCKWISE, 1);
        rasterizerDesc.set(JAVA_INT, RD_DEPTH_CLIP_ENABLE, 1);
        rasterizerState = createRasterizerState(device, rasterizerDesc);
        overlayBlendState = createAlphaBlendState(device);

        smoothSampler = createSampler(D3D11_FILTER_MIN_MAG_LINEAR_MIP_POINT);
        blockySampler = createSampler(D3D11_FILTER_MIN_MAG_MIP_POINT);

        blitShader = new D3D11Shader(device, context, "blit", ShaderLayout.attributes(2, 2));
        blitQuad = new D3D11Mesh(device, context,
                new float[]{ // position in clip space, texture coordinate
                        -1, -1, 0, 1,
                        1, -1, 1, 1,
                        1, 1, 1, 0,
                        -1, 1, 0, 0},
                new int[]{0, 1, 2, 0, 2, 3}, 2, 2);

        createBackBufferView();
    }

    private static String featureLevelName(int level) {
        return (level >> 12) + "_" + ((level >> 8) & 0xF);
    }

    /**
     * Name of the adapter the device really runs on. A hardware device can be
     * requested successfully and still end up on the "Microsoft Basic Render
     * Driver", which is WARP – so this is the only reliable source.
     */
    private String describeAdapter() {
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment dxgiDeviceId = guid(temp, 0x54ec77fa, 0x1377, 0x44e6,
                    0x8c, 0x32, 0x88, 0xfd, 0x5f, 0x44, 0xc8, 0x4c);
            ComObject dxgiDevice = new ComObject(queryInterface(device, dxgiDeviceId), 8);
            ComObject adapter = new ComObject(getAdapter(dxgiDevice), 9);
            MemorySegment desc = Native.bytes(temp, SIZEOF_ADAPTER_DESC);
            getAdapterDesc(adapter, desc);
            adapter.release();
            dxgiDevice.release();
            boolean software = desc.get(JAVA_INT, AD_VENDOR_ID) == VENDOR_MICROSOFT
                    && desc.get(JAVA_INT, AD_DEVICE_ID) == DEVICE_BASIC_RENDER_DRIVER;
            return Native.readWideString(desc, 128)
                    + (software ? " | software rendering on the CPU (WARP)" : " | hardware");
        } catch (RuntimeException e) {
            return "unknown adapter";
        }
    }

    private MemorySegment createSampler(int filter) {
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment desc = Native.bytes(temp, SIZEOF_SAMPLER_DESC);
            desc.set(JAVA_INT, SAMPLER_FILTER, filter);
            desc.set(JAVA_INT, SAMPLER_ADDRESS_U, D3D11_TEXTURE_ADDRESS_CLAMP);
            desc.set(JAVA_INT, SAMPLER_ADDRESS_V, D3D11_TEXTURE_ADDRESS_CLAMP);
            desc.set(JAVA_INT, SAMPLER_ADDRESS_W, D3D11_TEXTURE_ADDRESS_CLAMP);
            desc.set(JAVA_INT, SAMPLER_COMPARISON, D3D11_COMPARISON_NEVER);
            desc.set(JAVA_FLOAT, SAMPLER_MAX_LOD, Float.MAX_VALUE);
            return createSamplerState(device, desc);
        }
    }

    private void createBackBufferView() {
        MemorySegment backBuffer = getBuffer(swapChain, texture2DId);
        backBufferView = createRenderTargetView(device, backBuffer);
        ComObject.release(backBuffer);
    }

    private MemorySegment textureDesc(Arena temp, int textureWidth, int textureHeight, int format) {
        MemorySegment desc = Native.bytes(temp, SIZEOF_TEXTURE2D_DESC);
        desc.set(JAVA_INT, TD_WIDTH, textureWidth);
        desc.set(JAVA_INT, TD_HEIGHT, textureHeight);
        desc.set(JAVA_INT, TD_MIP_LEVELS, 1);
        desc.set(JAVA_INT, TD_ARRAY_SIZE, 1);
        desc.set(JAVA_INT, TD_FORMAT, format);
        desc.set(JAVA_INT, TD_SAMPLE_COUNT, 1);
        return desc;
    }

    private void createSceneTarget(int newWidth, int newHeight) {
        releaseSceneTarget();
        sceneWidth = newWidth;
        sceneHeight = newHeight;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment colorDesc = textureDesc(temp, sceneWidth, sceneHeight, DXGI_FORMAT_R8G8B8A8_UNORM);
            colorDesc.set(JAVA_INT, TD_USAGE, D3D11_USAGE_DEFAULT);
            colorDesc.set(JAVA_INT, TD_BIND_FLAGS, D3D11_BIND_RENDER_TARGET | D3D11_BIND_SHADER_RESOURCE);
            sceneColor = createTexture2D(device, colorDesc, MemorySegment.NULL);
            sceneColorView = createRenderTargetView(device, sceneColor);

            MemorySegment depthDesc = textureDesc(temp, sceneWidth, sceneHeight, DXGI_FORMAT_D24_UNORM_S8_UINT);
            depthDesc.set(JAVA_INT, TD_USAGE, D3D11_USAGE_DEFAULT);
            depthDesc.set(JAVA_INT, TD_BIND_FLAGS, D3D11_BIND_DEPTH_STENCIL);
            sceneDepth = createTexture2D(device, depthDesc, MemorySegment.NULL);
            sceneDepthView = createDepthStencilView(device, sceneDepth);
        }
        // The texture wrapper owns the shader view; the color texture is released separately.
        sceneTexture = new D3D11Texture(sceneWidth, sceneHeight, MemorySegment.NULL,
                createShaderResourceView(device, sceneColor), smoothUpscale ? smoothSampler : blockySampler);
    }

    private void releaseSceneTarget() {
        if (sceneTexture == null) {
            return;
        }
        unbindTargets();
        sceneTexture.close();
        sceneTexture = null;
        ComObject.release(sceneDepthView);
        ComObject.release(sceneDepth);
        ComObject.release(sceneColorView);
        ComObject.release(sceneColor);
    }

    /** Nothing may be bound while it is released or resized. */
    private void unbindTargets() {
        omSetRenderTarget(context, MemorySegment.NULL, MemorySegment.NULL);
        psSetShaderResource(context, nullArray);
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public void beginFrame(int windowWidth, int windowHeight) {
        if (windowWidth != width || windowHeight != height) {
            // The swap chain refuses to resize while anything still references its buffers.
            unbindTargets();
            ComObject.release(backBufferView);
            width = windowWidth;
            height = windowHeight;
            resizeBuffers(swapChain, width, height);
            createBackBufferView();
        }
        int wantedWidth = Math.max(1, Math.round(width * renderScale));
        int wantedHeight = Math.max(1, Math.round(height * renderScale));
        if (sceneTexture == null || wantedWidth != sceneWidth || wantedHeight != sceneHeight) {
            createSceneTarget(wantedWidth, wantedHeight);
        }
        // The scene image must not be bound as a texture while it is rendered into.
        psSetShaderResource(context, nullArray);
        renderTargetArray.set(ADDRESS, 0, sceneColorView);
        omSetRenderTarget(context, renderTargetArray, sceneDepthView);
        setViewport(sceneWidth, sceneHeight);
        rsSetState(context, rasterizerState);
        omSetBlendState(context, MemorySegment.NULL); // the scene is opaque (sprites are cut out, not blended)
        overlay = false;
    }

    private void setViewport(int viewportWidth, int viewportHeight) {
        viewport.set(JAVA_FLOAT, 0, 0f);
        viewport.set(JAVA_FLOAT, 4, 0f);
        viewport.set(JAVA_FLOAT, 8, viewportWidth);
        viewport.set(JAVA_FLOAT, 12, viewportHeight);
        viewport.set(JAVA_FLOAT, 16, 0f);
        viewport.set(JAVA_FLOAT, 20, 1f);
        rsSetViewport(context, viewport);
    }

    @Override
    public void clear(float red, float green, float blue) {
        clearColor.set(JAVA_FLOAT, 0, red);
        clearColor.set(JAVA_FLOAT, 4, green);
        clearColor.set(JAVA_FLOAT, 8, blue);
        if (overlay) {
            clearRenderTargetView(context, backBufferView, clearColor);
        } else {
            clearRenderTargetView(context, sceneColorView, clearColor);
            clearDepthStencilView(context, sceneDepthView);
        }
    }

    @Override
    public void beginOverlay() {
        if (overlay) {
            return;
        }
        overlay = true;
        // Without a depth buffer bound, the depth test is off: overlay drawing is plain 2D.
        renderTargetArray.set(ADDRESS, 0, backBufferView);
        omSetRenderTarget(context, renderTargetArray, MemorySegment.NULL);
        setViewport(width, height);
        sceneTexture.setSampler(smoothUpscale ? smoothSampler : blockySampler);
        blitShader.setTexture(sceneTexture);
        blitShader.bind();
        blitQuad.draw();
        psSetShaderResource(context, nullArray);
        // The overlay is 2D drawn back to front: transparent pixels let show what is below.
        omSetBlendState(context, overlayBlendState);
    }

    /** The GPU cannot hand out the back buffer directly: copy it to a CPU-readable texture first. */
    @Override
    public PixelImage readPixels() {
        beginOverlay();
        int[] rgb = new int[width * height];
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment desc = textureDesc(temp, width, height, DXGI_FORMAT_R8G8B8A8_UNORM);
            desc.set(JAVA_INT, TD_USAGE, D3D11_USAGE_STAGING);
            desc.set(JAVA_INT, TD_CPU_ACCESS_FLAGS, D3D11_CPU_ACCESS_READ);
            MemorySegment staging = createTexture2D(device, desc, MemorySegment.NULL);
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
        beginOverlay();
        D3D11Api.present(swapChain, vsync ? 1 : 0);
    }

    @Override
    public void setRenderScale(float scale) {
        renderScale = Math.max(0.1f, Math.min(1f, scale));
    }

    @Override
    public float renderScale() {
        return renderScale;
    }

    @Override
    public void setSmoothUpscale(boolean smooth) {
        smoothUpscale = smooth;
    }

    @Override
    public int sceneWidth() {
        return sceneWidth;
    }

    @Override
    public int sceneHeight() {
        return sceneHeight;
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
    public Texture createTexture(int textureWidth, int textureHeight, int[] argb, boolean smooth) {
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment pixels = Native.bytes(temp, (long) textureWidth * textureHeight * 4);
            for (int i = 0; i < argb.length; i++) {
                int color = argb[i];
                long offset = i * 4L;
                pixels.set(JAVA_BYTE, offset, (byte) (color >> 16));
                pixels.set(JAVA_BYTE, offset + 1, (byte) (color >> 8));
                pixels.set(JAVA_BYTE, offset + 2, (byte) color);
                pixels.set(JAVA_BYTE, offset + 3, (byte) (color >>> 24));
            }
            MemorySegment desc = textureDesc(temp, textureWidth, textureHeight, DXGI_FORMAT_R8G8B8A8_UNORM);
            desc.set(JAVA_INT, TD_USAGE, D3D11_USAGE_IMMUTABLE);
            desc.set(JAVA_INT, TD_BIND_FLAGS, D3D11_BIND_SHADER_RESOURCE);
            MemorySegment initialData = Native.bytes(temp, SIZEOF_SUBRESOURCE_DATA);
            initialData.set(ADDRESS, 0, pixels);
            initialData.set(JAVA_INT, SD_ROW_PITCH, textureWidth * 4);
            MemorySegment texture = createTexture2D(device, desc, initialData);
            return new D3D11Texture(textureWidth, textureHeight, texture,
                    createShaderResourceView(device, texture), smooth ? smoothSampler : blockySampler);
        }
    }

    @Override
    public void draw(Mesh mesh, Shader shader) {
        ((D3D11Shader) shader).bind();
        ((D3D11Mesh) mesh).draw();
    }

    @Override
    public void close() {
        releaseSceneTarget();
        unbindTargets();
        blitQuad.close();
        blitShader.close();
        ComObject.release(backBufferView);
        ComObject.release(blockySampler);
        ComObject.release(smoothSampler);
        ComObject.release(overlayBlendState);
        ComObject.release(rasterizerState);
        swapChain.release();
        context.release();
        device.release();
        arena.close();
    }
}
