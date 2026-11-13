package com.aresstack.threed4j.gfx.d3d11;

import com.aresstack.threed4j.ffm.Native;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

/**
 * Bindings for the parts of Direct3D 11, DXGI and the HLSL compiler the backend needs
 * (64-bit Windows). d3d11.dll and d3dcompiler_47.dll are part of Windows.
 *
 * <p>Direct3D is a COM API: apart from two exported functions, everything is a
 * method call through a virtual method table (see {@link ComObject}). The slot
 * numbers below follow the declaration order in d3d11.h and dxgi.h.</p>
 */
final class D3D11Api {

    private D3D11Api() {
    }

    private static final SymbolLookup D3D11 = Native.library("d3d11");
    private static final SymbolLookup D3DCOMPILER = Native.library("d3dcompiler_47");

    // ---------------------------------------------------------------- constants

    static final int D3D_DRIVER_TYPE_HARDWARE = 1;
    /** Microsoft's software rasterizer – works without any graphics driver. */
    static final int D3D_DRIVER_TYPE_WARP = 5;
    static final int D3D11_SDK_VERSION = 7;
    static final int D3D_FEATURE_LEVEL_10_0 = 0xa000;
    static final int D3D_FEATURE_LEVEL_10_1 = 0xa100;
    static final int D3D_FEATURE_LEVEL_11_0 = 0xb000;

    static final int DXGI_FORMAT_UNKNOWN = 0;
    static final int DXGI_FORMAT_R32G32B32A32_FLOAT = 2;
    static final int DXGI_FORMAT_R32G32B32_FLOAT = 6;
    static final int DXGI_FORMAT_R32G32_FLOAT = 16;
    static final int DXGI_FORMAT_R8G8B8A8_UNORM = 28;
    static final int DXGI_FORMAT_R32_FLOAT = 41;
    static final int DXGI_FORMAT_R32_UINT = 42;
    static final int DXGI_FORMAT_D24_UNORM_S8_UINT = 45;
    static final int DXGI_USAGE_RENDER_TARGET_OUTPUT = 0x20;
    static final int DXGI_SWAP_EFFECT_DISCARD = 0;

    static final int D3D11_USAGE_DEFAULT = 0;
    static final int D3D11_USAGE_IMMUTABLE = 1;
    static final int D3D11_USAGE_STAGING = 3;
    static final int D3D11_BIND_VERTEX_BUFFER = 0x1;
    static final int D3D11_BIND_INDEX_BUFFER = 0x2;
    static final int D3D11_BIND_CONSTANT_BUFFER = 0x4;
    static final int D3D11_BIND_SHADER_RESOURCE = 0x8;
    static final int D3D11_BIND_RENDER_TARGET = 0x20;
    static final int D3D11_BIND_DEPTH_STENCIL = 0x40;
    static final int D3D11_FILTER_MIN_MAG_MIP_POINT = 0x00;
    static final int D3D11_FILTER_MIN_MAG_LINEAR_MIP_POINT = 0x14;
    static final int D3D11_TEXTURE_ADDRESS_CLAMP = 3;
    static final int D3D11_COMPARISON_NEVER = 1;
    static final int D3D11_CPU_ACCESS_READ = 0x20000;
    static final int D3D11_MAP_READ = 1;
    static final int D3D11_CLEAR_DEPTH = 0x1;
    static final int D3D11_CLEAR_STENCIL = 0x2;
    static final int D3D11_BLEND_ZERO = 1;
    static final int D3D11_BLEND_ONE = 2;
    static final int D3D11_BLEND_SRC_ALPHA = 5;
    static final int D3D11_BLEND_INV_SRC_ALPHA = 6;
    static final int D3D11_BLEND_OP_ADD = 1;
    static final int D3D11_FILL_SOLID = 3;
    static final int D3D11_CULL_BACK = 3;
    static final int D3D11_PRIMITIVE_TOPOLOGY_TRIANGLELIST = 4;

    // ------------------------------------------------------------ struct layouts

    /** {@code DXGI_SWAP_CHAIN_DESC}. */
    static final long SIZEOF_SWAP_CHAIN_DESC = 72;
    static final long SCD_WIDTH = 0;
    static final long SCD_HEIGHT = 4;
    static final long SCD_FORMAT = 16;
    static final long SCD_SAMPLE_COUNT = 28;
    static final long SCD_BUFFER_USAGE = 36;
    static final long SCD_BUFFER_COUNT = 40;
    static final long SCD_OUTPUT_WINDOW = 48;
    static final long SCD_WINDOWED = 56;
    static final long SCD_SWAP_EFFECT = 60;

    /** {@code D3D11_BUFFER_DESC}. */
    static final long SIZEOF_BUFFER_DESC = 24;
    static final long BD_BYTE_WIDTH = 0;
    static final long BD_USAGE = 4;
    static final long BD_BIND_FLAGS = 8;

    /** {@code D3D11_SUBRESOURCE_DATA}: pointer to the initial data, then the byte length of one row. */
    static final long SIZEOF_SUBRESOURCE_DATA = 16;
    static final long SD_ROW_PITCH = 8;

    /** {@code D3D11_SAMPLER_DESC}. */
    static final long SIZEOF_SAMPLER_DESC = 52;
    static final long SAMPLER_FILTER = 0;
    static final long SAMPLER_ADDRESS_U = 4;
    static final long SAMPLER_ADDRESS_V = 8;
    static final long SAMPLER_ADDRESS_W = 12;
    static final long SAMPLER_COMPARISON = 24;
    static final long SAMPLER_MAX_LOD = 48;

    /** {@code D3D11_TEXTURE2D_DESC}. */
    static final long SIZEOF_TEXTURE2D_DESC = 44;
    static final long TD_WIDTH = 0;
    static final long TD_HEIGHT = 4;
    static final long TD_MIP_LEVELS = 8;
    static final long TD_ARRAY_SIZE = 12;
    static final long TD_FORMAT = 16;
    static final long TD_SAMPLE_COUNT = 20;
    static final long TD_USAGE = 28;
    static final long TD_BIND_FLAGS = 32;
    static final long TD_CPU_ACCESS_FLAGS = 36;

    /** {@code D3D11_INPUT_ELEMENT_DESC}. */
    static final long SIZEOF_INPUT_ELEMENT_DESC = 32;
    static final long IED_SEMANTIC_NAME = 0;
    static final long IED_SEMANTIC_INDEX = 8;
    static final long IED_FORMAT = 12;
    static final long IED_ALIGNED_BYTE_OFFSET = 20;

    /** {@code D3D11_RASTERIZER_DESC}. */
    static final long SIZEOF_RASTERIZER_DESC = 40;
    static final long RD_FILL_MODE = 0;
    static final long RD_CULL_MODE = 4;
    static final long RD_FRONT_COUNTER_CLOCKWISE = 8;
    static final long RD_DEPTH_CLIP_ENABLE = 24;

    /** {@code D3D11_MAPPED_SUBRESOURCE}: data pointer, then the byte length of one row. */
    static final long SIZEOF_MAPPED_SUBRESOURCE = 16;
    static final long MS_ROW_PITCH = 8;

    /** {@code D3D11_VIEWPORT}: x, y, width, height, min depth, max depth as floats. */
    static final long SIZEOF_VIEWPORT = 24;

    /** {@code DXGI_ADAPTER_DESC}: {@code WCHAR Description[128]}, then vendor and device id. */
    static final long SIZEOF_ADAPTER_DESC = 304;
    static final long AD_VENDOR_ID = 256;
    static final long AD_DEVICE_ID = 260;
    /** Vendor and device id of the "Microsoft Basic Render Driver", i.e. WARP. */
    static final int VENDOR_MICROSOFT = 0x1414;
    static final int DEVICE_BASIC_RENDER_DRIVER = 0x8c;

    // ------------------------------------------------------- exported functions

    private static final MethodHandle D3D11CreateDeviceAndSwapChain = Native.downcall(D3D11,
            "D3D11CreateDeviceAndSwapChain",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT,
                    ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    private static final MethodHandle D3DCompile = Native.downcall(D3DCOMPILER, "D3DCompile",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS,
                    JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));

    // ----------------------------------------------------- COM method signatures
    // The first two parameters are always the function pointer and the object itself.

    private static final MethodHandle INT_P = com(JAVA_INT, ADDRESS);
    private static final MethodHandle INT_PP = com(JAVA_INT, ADDRESS, ADDRESS);
    private static final MethodHandle INT_PPP = com(JAVA_INT, ADDRESS, ADDRESS, ADDRESS);
    private static final MethodHandle INT_IPP = com(JAVA_INT, JAVA_INT, ADDRESS, ADDRESS);
    private static final MethodHandle INT_II = com(JAVA_INT, JAVA_INT, JAVA_INT);
    private static final MethodHandle INT_IIIII = com(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT);
    private static final MethodHandle INT_PLPP = com(JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS);
    private static final MethodHandle INT_PIPLP = com(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS);
    private static final MethodHandle INT_PIIIP = com(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS);
    private static final MethodHandle PTR = com(ADDRESS);
    private static final MethodHandle LONG = com(JAVA_LONG);
    private static final MethodHandle VOID_P = comVoid(ADDRESS);
    private static final MethodHandle VOID_I = comVoid(JAVA_INT);
    private static final MethodHandle VOID_PP = comVoid(ADDRESS, ADDRESS);
    private static final MethodHandle VOID_PI = comVoid(ADDRESS, JAVA_INT);
    private static final MethodHandle VOID_IP = comVoid(JAVA_INT, ADDRESS);
    private static final MethodHandle VOID_IIP = comVoid(JAVA_INT, JAVA_INT, ADDRESS);
    private static final MethodHandle VOID_IPP = comVoid(JAVA_INT, ADDRESS, ADDRESS);
    private static final MethodHandle VOID_PPI = comVoid(ADDRESS, ADDRESS, JAVA_INT);
    private static final MethodHandle VOID_PII = comVoid(ADDRESS, JAVA_INT, JAVA_INT);
    private static final MethodHandle VOID_III = comVoid(JAVA_INT, JAVA_INT, JAVA_INT);
    private static final MethodHandle VOID_IIPPP = comVoid(JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS);
    private static final MethodHandle VOID_PIPPII = comVoid(ADDRESS, JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT);
    private static final MethodHandle VOID_PIFB = comVoid(ADDRESS, JAVA_INT, JAVA_FLOAT, JAVA_BYTE);

    private static MethodHandle com(MemoryLayout result, MemoryLayout... parameters) {
        return Native.functionPointer(FunctionDescriptor.of(result, withThis(parameters)));
    }

    private static MethodHandle comVoid(MemoryLayout... parameters) {
        return Native.functionPointer(FunctionDescriptor.ofVoid(withThis(parameters)));
    }

    private static MemoryLayout[] withThis(MemoryLayout[] parameters) {
        MemoryLayout[] all = new MemoryLayout[parameters.length + 1];
        all[0] = ADDRESS;
        System.arraycopy(parameters, 0, all, 1, parameters.length);
        return all;
    }

    /** Throws if a COM call failed. Negative {@code HRESULT} values are errors. */
    static void check(int hresult, String call) {
        if (hresult < 0) {
            throw new IllegalStateException(call + " failed (HRESULT 0x" + String.format("%08X", hresult) + ")");
        }
    }

    /** Allocates a GUID, e.g. {@code guid(a, 0x6f15aaf2, 0xd208, 0x4e89, 0x9a, 0xb4, ...)}. */
    static MemorySegment guid(SegmentAllocator allocator, int data1, int data2, int data3, int... data4) {
        MemorySegment guid = Native.bytes(allocator, 16);
        guid.set(JAVA_INT, 0, data1);
        guid.set(JAVA_SHORT, 4, (short) data2);
        guid.set(JAVA_SHORT, 6, (short) data3);
        for (int i = 0; i < 8; i++) {
            guid.set(JAVA_BYTE, 8 + i, (byte) data4[i]);
        }
        return guid;
    }

    // ---------------------------------------------------------- device creation

    /** The three objects {@code D3D11CreateDeviceAndSwapChain} hands out. */
    record DeviceObjects(ComObject device, ComObject context, ComObject swapChain, int featureLevel) {
    }

    /** @return {@code null} if this driver type is not available */
    static DeviceObjects createDeviceAndSwapChain(int driverType, MemorySegment swapChainDesc) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment featureLevels = Native.ints(arena,
                    D3D_FEATURE_LEVEL_11_0, D3D_FEATURE_LEVEL_10_1, D3D_FEATURE_LEVEL_10_0);
            MemorySegment swapChain = arena.allocate(ADDRESS);
            MemorySegment device = arena.allocate(ADDRESS);
            MemorySegment featureLevel = arena.allocate(JAVA_INT);
            MemorySegment context = arena.allocate(ADDRESS);
            int result = (int) D3D11CreateDeviceAndSwapChain.invokeExact(MemorySegment.NULL, driverType,
                    MemorySegment.NULL, 0, featureLevels, 3, D3D11_SDK_VERSION, swapChainDesc,
                    swapChain, device, featureLevel, context);
            if (result < 0) {
                return null;
            }
            return new DeviceObjects(
                    new ComObject(device.get(ADDRESS, 0), 44),
                    new ComObject(context.get(ADDRESS, 0), 115),
                    new ComObject(swapChain.get(ADDRESS, 0), 18),
                    featureLevel.get(JAVA_INT, 0));
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    // ------------------------------------------------------------ HLSL compiler

    /**
     * Compiles HLSL source to bytecode.
     *
     * @param target shader model, e.g. {@code "vs_4_0"} or {@code "ps_4_0"}
     * @return an {@code ID3DBlob}; read it with {@link #blobPointer} and {@link #blobSize}
     */
    static ComObject compile(String source, String sourceName, String entryPoint, String target) {
        try (Arena arena = Arena.ofConfined()) {
            byte[] utf8 = source.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            MemorySegment text = Native.bytes(arena, utf8.length + 1L);
            MemorySegment.copy(utf8, 0, text, JAVA_BYTE, 0, utf8.length);
            MemorySegment code = arena.allocate(ADDRESS);
            MemorySegment errors = arena.allocate(ADDRESS);
            int result = (int) D3DCompile.invokeExact(text, (long) utf8.length, Native.cString(arena, sourceName),
                    MemorySegment.NULL, MemorySegment.NULL, Native.cString(arena, entryPoint),
                    Native.cString(arena, target), 0, 0, code, errors);
            MemorySegment errorBlob = errors.get(ADDRESS, 0);
            String log = "";
            if (!Native.isNull(errorBlob)) {
                ComObject blob = new ComObject(errorBlob, 5);
                log = Native.readCString(blobPointer(blob));
                blob.release();
            }
            if (result < 0) {
                throw new IllegalStateException("HLSL shader " + sourceName + " (" + entryPoint
                        + ") does not compile:\n" + log);
            }
            return new ComObject(code.get(ADDRESS, 0), 5);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** {@code ID3DBlob::GetBufferPointer}. */
    static MemorySegment blobPointer(ComObject blob) {
        try {
            return (MemorySegment) PTR.invokeExact(blob.method(3), blob.pointer);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** {@code ID3DBlob::GetBufferSize}. */
    static long blobSize(ComObject blob) {
        try {
            return (long) LONG.invokeExact(blob.method(4), blob.pointer);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    // ------------------------------------------------------------------ IUnknown

    /** {@code IUnknown::QueryInterface}: asks the object for another of its interfaces. */
    static MemorySegment queryInterface(ComObject object, MemorySegment interfaceId) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(ADDRESS);
            check((int) INT_PP.invokeExact(object.method(0), object.pointer, interfaceId, out),
                    "IUnknown::QueryInterface");
            return out.get(ADDRESS, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    // ---------------------------------------------------------------------- DXGI

    /** {@code IDXGISwapChain::Present}. */
    static void present(ComObject swapChain, int syncInterval) {
        try {
            check((int) INT_II.invokeExact(swapChain.method(8), swapChain.pointer, syncInterval, 0),
                    "IDXGISwapChain::Present");
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** {@code IDXGISwapChain::GetBuffer}: the back buffer as the requested interface. */
    static MemorySegment getBuffer(ComObject swapChain, MemorySegment interfaceId) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(ADDRESS);
            check((int) INT_IPP.invokeExact(swapChain.method(9), swapChain.pointer, 0, interfaceId, out),
                    "IDXGISwapChain::GetBuffer");
            return out.get(ADDRESS, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** {@code IDXGISwapChain::ResizeBuffers}, keeping buffer count and format. */
    static void resizeBuffers(ComObject swapChain, int width, int height) {
        try {
            check((int) INT_IIIII.invokeExact(swapChain.method(13), swapChain.pointer, 0, width, height,
                    DXGI_FORMAT_UNKNOWN, 0), "IDXGISwapChain::ResizeBuffers");
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** {@code IDXGIDevice::GetAdapter}. */
    static MemorySegment getAdapter(ComObject dxgiDevice) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(ADDRESS);
            check((int) INT_P.invokeExact(dxgiDevice.method(7), dxgiDevice.pointer, out), "IDXGIDevice::GetAdapter");
            return out.get(ADDRESS, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** {@code IDXGIAdapter::GetDesc}. */
    static void getAdapterDesc(ComObject adapter, MemorySegment desc) {
        try {
            check((int) INT_P.invokeExact(adapter.method(8), adapter.pointer, desc), "IDXGIAdapter::GetDesc");
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    // -------------------------------------------------------------- ID3D11Device

    /** {@code CreateBuffer}; {@code initialData} may be NULL. */
    static MemorySegment createBuffer(ComObject device, MemorySegment desc, MemorySegment initialData) {
        return create(device, 3, desc, initialData, "ID3D11Device::CreateBuffer");
    }

    /** {@code CreateTexture2D}; {@code initialData} may be NULL. */
    static MemorySegment createTexture2D(ComObject device, MemorySegment desc, MemorySegment initialData) {
        return create(device, 5, desc, initialData, "ID3D11Device::CreateTexture2D");
    }

    /** With a NULL description: a view of the whole texture, as shaders sample it. */
    static MemorySegment createShaderResourceView(ComObject device, MemorySegment resource) {
        return create(device, 7, resource, MemorySegment.NULL, "ID3D11Device::CreateShaderResourceView");
    }

    /** With a NULL description: a view of the whole resource in its own format. */
    static MemorySegment createRenderTargetView(ComObject device, MemorySegment resource) {
        return create(device, 9, resource, MemorySegment.NULL, "ID3D11Device::CreateRenderTargetView");
    }

    static MemorySegment createDepthStencilView(ComObject device, MemorySegment resource) {
        return create(device, 10, resource, MemorySegment.NULL, "ID3D11Device::CreateDepthStencilView");
    }

    private static MemorySegment create(ComObject device, int slot, MemorySegment first, MemorySegment second,
                                        String call) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(ADDRESS);
            check((int) INT_PPP.invokeExact(device.method(slot), device.pointer, first, second, out), call);
            return out.get(ADDRESS, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static MemorySegment createInputLayout(ComObject device, MemorySegment elements, int count,
                                           MemorySegment bytecode, long bytecodeSize) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(ADDRESS);
            check((int) INT_PIPLP.invokeExact(device.method(11), device.pointer, elements, count, bytecode,
                    bytecodeSize, out), "ID3D11Device::CreateInputLayout");
            return out.get(ADDRESS, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static MemorySegment createVertexShader(ComObject device, MemorySegment bytecode, long size) {
        return createShader(device, 12, bytecode, size, "ID3D11Device::CreateVertexShader");
    }

    static MemorySegment createPixelShader(ComObject device, MemorySegment bytecode, long size) {
        return createShader(device, 15, bytecode, size, "ID3D11Device::CreatePixelShader");
    }

    private static MemorySegment createShader(ComObject device, int slot, MemorySegment bytecode, long size,
                                              String call) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(ADDRESS);
            check((int) INT_PLPP.invokeExact(device.method(slot), device.pointer, bytecode, size,
                    MemorySegment.NULL, out), call);
            return out.get(ADDRESS, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /**
     * A blend state for ordinary transparency in the first render target:
     * {@code source * alpha + destination * (1 - alpha)}.
     */
    static MemorySegment createAlphaBlendState(ComObject device) {
        try (Arena arena = Arena.ofConfined()) {
            // D3D11_BLEND_DESC: two BOOLs, then eight D3D11_RENDER_TARGET_BLEND_DESC of 32 bytes
            // (BOOL enable, six enums, one byte write mask, padding).
            MemorySegment desc = Native.bytes(arena, 8 + 8 * 32);
            for (int target = 0; target < 8; target++) {
                long at = 8 + target * 32L;
                desc.set(JAVA_INT, at, target == 0 ? 1 : 0);
                desc.set(JAVA_INT, at + 4, target == 0 ? D3D11_BLEND_SRC_ALPHA : D3D11_BLEND_ONE);
                desc.set(JAVA_INT, at + 8, target == 0 ? D3D11_BLEND_INV_SRC_ALPHA : D3D11_BLEND_ZERO);
                desc.set(JAVA_INT, at + 12, D3D11_BLEND_OP_ADD);
                desc.set(JAVA_INT, at + 16, D3D11_BLEND_ONE);
                desc.set(JAVA_INT, at + 20, target == 0 ? D3D11_BLEND_INV_SRC_ALPHA : D3D11_BLEND_ZERO);
                desc.set(JAVA_INT, at + 24, D3D11_BLEND_OP_ADD);
                desc.set(JAVA_BYTE, at + 28, (byte) 0x0F); // write red, green, blue and alpha
            }
            MemorySegment out = arena.allocate(ADDRESS);
            check((int) INT_PP.invokeExact(device.method(20), device.pointer, desc, out),
                    "ID3D11Device::CreateBlendState");
            return out.get(ADDRESS, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** @param blendState a blend state, or NULL for the default (no blending) */
    static void omSetBlendState(ComObject context, MemorySegment blendState) {
        try {
            // No blend factor (NULL means 1, 1, 1, 1), all samples.
            VOID_PPI.invokeExact(context.method(35), context.pointer, blendState, MemorySegment.NULL, 0xFFFFFFFF);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static MemorySegment createRasterizerState(ComObject device, MemorySegment desc) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(ADDRESS);
            check((int) INT_PP.invokeExact(device.method(22), device.pointer, desc, out),
                    "ID3D11Device::CreateRasterizerState");
            return out.get(ADDRESS, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static MemorySegment createSamplerState(ComObject device, MemorySegment desc) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(ADDRESS);
            check((int) INT_PP.invokeExact(device.method(23), device.pointer, desc, out),
                    "ID3D11Device::CreateSamplerState");
            return out.get(ADDRESS, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    // ------------------------------------------------------- ID3D11DeviceContext
    // These run every frame, so they take prepared native memory and allocate nothing.

    /** @param buffers native array holding one {@code ID3D11Buffer*} */
    static void vsSetConstantBuffer(ComObject context, MemorySegment buffers) {
        try {
            VOID_IIP.invokeExact(context.method(7), context.pointer, 0, 1, buffers);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void psSetConstantBuffer(ComObject context, MemorySegment buffers) {
        try {
            VOID_IIP.invokeExact(context.method(16), context.pointer, 0, 1, buffers);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** @param views native array holding one shader resource view (the entry may be NULL to unbind) */
    static void psSetShaderResource(ComObject context, MemorySegment views) {
        try {
            VOID_IIP.invokeExact(context.method(8), context.pointer, 0, 1, views);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** @param samplers native array holding one sampler state */
    static void psSetSampler(ComObject context, MemorySegment samplers) {
        try {
            VOID_IIP.invokeExact(context.method(10), context.pointer, 0, 1, samplers);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void psSetShader(ComObject context, MemorySegment shader) {
        try {
            VOID_PPI.invokeExact(context.method(9), context.pointer, shader, MemorySegment.NULL, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void vsSetShader(ComObject context, MemorySegment shader) {
        try {
            VOID_PPI.invokeExact(context.method(11), context.pointer, shader, MemorySegment.NULL, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void drawIndexed(ComObject context, int indexCount) {
        try {
            VOID_III.invokeExact(context.method(12), context.pointer, indexCount, 0, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** {@code Map} for reading; fills a {@code D3D11_MAPPED_SUBRESOURCE}. */
    static void mapForRead(ComObject context, MemorySegment resource, MemorySegment mapped) {
        try {
            check((int) INT_PIIIP.invokeExact(context.method(14), context.pointer, resource, 0, D3D11_MAP_READ, 0,
                    mapped), "ID3D11DeviceContext::Map");
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void unmap(ComObject context, MemorySegment resource) {
        try {
            VOID_PI.invokeExact(context.method(15), context.pointer, resource, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void iaSetInputLayout(ComObject context, MemorySegment inputLayout) {
        try {
            VOID_P.invokeExact(context.method(17), context.pointer, inputLayout);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** @param buffers one {@code ID3D11Buffer*}; {@code strides} and {@code offsets} one UINT each */
    static void iaSetVertexBuffer(ComObject context, MemorySegment buffers, MemorySegment strides,
                                  MemorySegment offsets) {
        try {
            VOID_IIPPP.invokeExact(context.method(18), context.pointer, 0, 1, buffers, strides, offsets);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void iaSetIndexBuffer(ComObject context, MemorySegment buffer, int format) {
        try {
            VOID_PII.invokeExact(context.method(19), context.pointer, buffer, format, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void iaSetPrimitiveTopology(ComObject context, int topology) {
        try {
            VOID_I.invokeExact(context.method(24), context.pointer, topology);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** @param views native array holding one render target view, or NULL to unbind everything */
    static void omSetRenderTarget(ComObject context, MemorySegment views, MemorySegment depthStencilView) {
        try {
            VOID_IPP.invokeExact(context.method(33), context.pointer, Native.isNull(views) ? 0 : 1, views,
                    depthStencilView);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void rsSetState(ComObject context, MemorySegment rasterizerState) {
        try {
            VOID_P.invokeExact(context.method(43), context.pointer, rasterizerState);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void rsSetViewport(ComObject context, MemorySegment viewport) {
        try {
            VOID_IP.invokeExact(context.method(44), context.pointer, 1, viewport);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void copyResource(ComObject context, MemorySegment destination, MemorySegment source) {
        try {
            VOID_PP.invokeExact(context.method(47), context.pointer, destination, source);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** Replaces the whole content of a buffer. */
    static void updateSubresource(ComObject context, MemorySegment resource, MemorySegment data) {
        try {
            VOID_PIPPII.invokeExact(context.method(48), context.pointer, resource, 0, MemorySegment.NULL, data, 0, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** @param color four floats: red, green, blue, alpha */
    static void clearRenderTargetView(ComObject context, MemorySegment view, MemorySegment color) {
        try {
            VOID_PP.invokeExact(context.method(50), context.pointer, view, color);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void clearDepthStencilView(ComObject context, MemorySegment view) {
        try {
            VOID_PIFB.invokeExact(context.method(53), context.pointer, view,
                    D3D11_CLEAR_DEPTH | D3D11_CLEAR_STENCIL, 1f, (byte) 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }
}
