package image;

import lombok.Getter;

/**
 * DXGI_FORMAT values used by DDS textures.
 * Integer values match the DirectX specification exactly.
 */
@Getter
@lombok.extern.slf4j.Slf4j
public enum DxgiFormat {
	UNKNOWN(0), R32G32B32A32_FLOAT(2), R16G16B16A16_FLOAT(10), R16G16B16A16_UNORM(11), R32G32_FLOAT(16), R10G10B10A2_UNORM(24), R8G8B8A8_UNORM(28), R8G8B8A8_UNORM_SRGB(29), R16G16_UNORM(35), R16G16_SNORM(37), R32_FLOAT(41), R8G8_UNORM(49), R8G8_SNORM(50), R16_UNORM(56), R8_UNORM(61), BC1_UNORM(71), BC1_UNORM_SRGB(72), BC2_UNORM(74), BC2_UNORM_SRGB(75), BC3_UNORM(77), BC3_UNORM_SRGB(78), BC4_UNORM(80), BC4_SNORM(81), BC5_UNORM(83), BC5_SNORM(84), BC6H_UF16(95), BC6H_SF16(96), BC7_UNORM(98), BC7_UNORM_SRGB(99);
	
	private final int value;
	
	DxgiFormat(int value) {
		this.value = value;
	}
	
	public static DxgiFormat fromValue(int v) {
		for (DxgiFormat f : values()) {
			if (f.value == v)
				return f;
		}
		return UNKNOWN;
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