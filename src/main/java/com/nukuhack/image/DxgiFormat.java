package com.nukuhack.image;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * DXGI_FORMAT values used by DDS textures.
 * Integer values match the DirectX specification exactly.
 */
@Getter
@Slf4j
@RequiredArgsConstructor
public enum DxgiFormat {
	UNKNOWN(0),
	R32G32B32A32_FLOAT(2),
	R16G16B16A16_FLOAT(10),
	R16G16B16A16_UNORM(11),
	R32G32_FLOAT(16),
	R10G10B10A2_UNORM(24),
	R8G8B8A8_UNORM(28),
	R8G8B8A8_UNORM_SRGB(29),
	R16G16_UNORM(35),
	R16G16_SNORM(37),
	R32_FLOAT(41),
	R8G8_UNORM(49),
	R8G8_SNORM(50),
	R16_UNORM(56),
	R8_UNORM(61),
	BC1_UNORM(71),
	BC1_UNORM_SRGB(72),
	BC2_UNORM(74),
	BC2_UNORM_SRGB(75),
	BC3_UNORM(77),
	BC3_UNORM_SRGB(78),
	BC4_UNORM(80),
	BC4_SNORM(81),
	BC5_UNORM(83),
	BC5_SNORM(84),
	BC6H_UF16(95),
	BC6H_SF16(96),
	BC7_UNORM(98),
	BC7_UNORM_SRGB(99),
	B8G8R8A8_UNORM(87);
	
	private final int value;
	
	public static DxgiFormat fromValue(int v) {
		for (DxgiFormat f : values())
			if (f.value == v)
				return f;
		return UNKNOWN;
	}
	
	/** Map a DXGI integer to our internal code (either a FourCC or a special constant). */
	static int toValue(int dxgi) {
		var f = fromValue(dxgi);
		if (f == UNKNOWN)
			throw new UnsupportedOperationException("Unsupported DXGI format: " + dxgi);
		return f.getValue();
	}
	
	static String dxgiName(int dxgi) {
		return switch (DxgiFormat.fromValue(dxgi)) {
			case BC1_UNORM -> "BC1 / DXT1 (DXGI 71)";
			case BC2_UNORM -> "BC2 / DXT3 (DXGI 74)";
			case BC3_UNORM -> "BC3 / DXT5 (DXGI 77)";
			case BC4_UNORM -> "BC4_UNORM (DXGI 80)";
			case BC4_SNORM -> "BC4_SNORM (DXGI 81)";
			case BC5_UNORM -> "BC5_UNORM — RG normal map (DXGI 83)";
			case BC5_SNORM -> "BC5_SNORM — RG normal map signed (DXGI 84)";
			case BC7_UNORM -> "BC7 (DXGI 98)";
			case R8G8B8A8_UNORM -> "Uncompressed RGBA8 (DXGI 28)";
			case B8G8R8A8_UNORM -> "Uncompressed BGRA8 (DXGI 87)";
			default -> "DXGI format " + dxgi;
		};
	}
	
	static String legacyFourccName(int fourCC) {
		return switch (fourCC) {
			case Dds.Pixel.FOURCC_DXT1 -> "BC1 / DXT1 (legacy FourCC)";
			case Dds.Pixel.FOURCC_DXT3 -> "BC2 / DXT3 (legacy FourCC)";
			case Dds.Pixel.FOURCC_DXT5 -> "BC3 / DXT5 (legacy FourCC)";
			case Dds.Pixel.FOURCC_ATI1, Dds.Pixel.FOURCC_BC4U -> "BC4_UNORM (ATI1/BC4U FourCC)";
			case Dds.Pixel.FOURCC_BC4S -> "BC4_SNORM (BC4S FourCC)";
			case Dds.Pixel.FOURCC_ATI2, Dds.Pixel.FOURCC_BC5U -> "BC5_UNORM (ATI2/BC5U FourCC)";
			case Dds.Pixel.FOURCC_BC5S -> "BC5_SNORM (BC5S FourCC)";
			default -> "FourCC 0x" + Integer.toHexString(fourCC);
		};
	}
	
	/** Map a legacy DDS FourCC to our internal code. */
	static int fromLegacyFourcc(int fourCC) throws IOException {
		return switch (fourCC) {
			case Dds.Pixel.FOURCC_DXT1 -> Dds.Pixel.FOURCC_DXT1;
			case Dds.Pixel.FOURCC_DXT3 -> Dds.Pixel.FOURCC_DXT3;
			case Dds.Pixel.FOURCC_DXT5 -> Dds.Pixel.FOURCC_DXT5;
			case Dds.Pixel.FOURCC_BC7 -> Dds.Pixel.FOURCC_BC7;
			case Dds.Pixel.FOURCC_ATI1, Dds.Pixel.FOURCC_BC4U -> DxgiFormat.BC4_UNORM.getValue();
			case Dds.Pixel.FOURCC_BC4S -> DxgiFormat.BC4_SNORM.getValue();
			case Dds.Pixel.FOURCC_ATI2, Dds.Pixel.FOURCC_BC5U -> DxgiFormat.BC5_UNORM.getValue();
			case Dds.Pixel.FOURCC_BC5S -> DxgiFormat.BC5_SNORM.getValue();
			default -> throw new IOException("Unsupported DDS FourCC: 0x" + Integer.toHexString(fourCC));
		};
	}
	
	/** True for BC-compressed formats */
	public boolean isCompressed() {
		return switch (this) {
			case BC1_UNORM, BC1_UNORM_SRGB, BC2_UNORM, BC2_UNORM_SRGB, BC3_UNORM, BC3_UNORM_SRGB, BC4_UNORM, BC4_SNORM,
				 BC5_UNORM, BC5_SNORM, BC6H_UF16, BC6H_SF16, BC7_UNORM, BC7_UNORM_SRGB -> true;
			default -> false;
		};
	}
	
	/** True for SRGB formats */
	public boolean isSRGB() {
		return switch (this) {
			case R8G8B8A8_UNORM_SRGB, BC1_UNORM_SRGB, BC2_UNORM_SRGB, BC3_UNORM_SRGB, BC7_UNORM_SRGB -> true;
			default -> false;
		};
	}
	
	/** True for BC5 normal-map formats */
	public boolean isNormalMap() {
		return this == BC5_SNORM || this == BC5_UNORM;
	}
	
	/**
	 * Returns bits-per-pixel for this format.
	 * Only covers formats actually found in KCD DDS files.
	 */
	public int bitsPerPixel() {
		return switch (this) {
			case R32G32B32A32_FLOAT -> 128;
			case R16G16B16A16_FLOAT, R16G16B16A16_UNORM, R32G32_FLOAT -> 64;
			case R10G10B10A2_UNORM, R8G8B8A8_UNORM, R8G8B8A8_UNORM_SRGB, R16G16_UNORM, R16G16_SNORM, R32_FLOAT -> 32;
			case R8G8_UNORM, R8G8_SNORM, R16_UNORM -> 16;
			// BC1, BC4 = 4 bpp
			case BC1_UNORM, BC1_UNORM_SRGB, BC4_UNORM, BC4_SNORM -> 4;
			// BC2, BC3, BC5, BC6H, BC7 = 8 bpp
			case BC2_UNORM, BC2_UNORM_SRGB, BC3_UNORM, BC3_UNORM_SRGB, BC5_UNORM, BC5_SNORM, BC6H_UF16, BC6H_SF16,
				 BC7_UNORM, BC7_UNORM_SRGB, R8_UNORM -> 8;
			default -> 0;
		};
	}
}