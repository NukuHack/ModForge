package com.nukuhack.image;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Entry point for importing <em>any</em> texture into the KCD2 mod pipeline.
 *
 * <h3>Supported inputs</h3>
 * <ul>
 *   <li>Standard DDS: DXT1 / DXT3 / DXT5 / BC4 / BC5 / BC7 / uncompressed RGBA·BGRA</li>
 *   <li>Any format that Java's {@link ImageIO} can read: PNG, JPEG, BMP, TIFF, TGA, WebP, …</li>
 * </ul>
 *
 * <h3>Data flow</h3>
 * <pre>
 *   raw bytes
 *       │
 *       ▼
 *   decodeToRgba()          ← all formats → flat RGBA8 byte[]
 *       │
 *       ▼
 *   encode()                ← routes to the right DDSUtil.encodeXxx() method
 *       │
 *       ▼
 *   DDS bytes (KCD2-ready)
 * </pre>
 *
 * =========================================================================
 * Encode: RGBA8 → KCD2 DDS
 * All actual compression is delegated to DDSUtil.encodeXxx()
 * =========================================================================
 *
 * <p>All codec and header work is delegated to {@link DDSUtil}. This class only
 * handles format detection, validation feedback, and format-profile routing.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TextureImporter {
	
	/**
	 * Import a texture from raw bytes, auto-detecting the profile from the filename.
	 *
	 * @param data     raw file bytes (DDS, PNG, JPEG, …)
	 * @param filename original filename (used for profile detection and log messages)
	 */
	public static TextureImportResult importTexture(byte[] data, String filename) throws IOException {
		FormatProfile profile = FormatProfile.detect(filename);
		boolean autoDetected = (profile != FormatProfile.UNKNOWN);
		return run(data, filename, profile, autoDetected);
	}
	
	/**
	 * Import a texture with a caller-supplied profile (e.g. chosen by the user in a UI).
	 *
	 * @param data     raw file bytes
	 * @param filename original filename
	 * @param profile  texture role chosen by the caller
	 */
	public static TextureImportResult importTexture(byte[] data, String filename, FormatProfile profile) throws IOException {
		return run(data, filename, profile, false);
	}
	
	private static TextureImportResult run(byte[] data, String filename, FormatProfile profile, boolean profileAutoDetected) throws IOException {
		
		var result = TextureImportResult.b().file(filename).profile(profile).profileAutoDetected(profileAutoDetected);
		
		if (! profileAutoDetected) {
			result.message("Profile not detected from filename — defaulting to: " + profile.displayName);
			result.actionRequired("If '" + profile.displayName + "' is wrong, re-import and choose the correct profile for this texture.");
		} else {
			result.message("Profile auto-detected from filename: " + profile.displayName);
		}
		
		// ── Step 1: decode any input format to RGBA8 ─────────────────────────
		DecodeResult decoded = decodeToRgba(data, filename, result);
		if (decoded == null)
			return result; // decodeToRgba already added an actionRequired message
		
		result.detectedSourceFormat(decoded.sourceFormatDescription);
		result.dimensions(decoded.width, decoded.height);
		result.message("Decoded: " + decoded.width + "×" + decoded.height + " from " + decoded.sourceFormatDescription);
		
		// ── Sanity checks ─────────────────────────────────────────────────────
		if (! isPow2(decoded.width) || ! isPow2(decoded.height)) {
			result.warning("Image dimensions (" + decoded.width + "×" + decoded.height + ") are not powers of two. KCD2 expects POT textures. The image will be converted as-is, but it may not load correctly in-game.");
		}
		if (decoded.width < 4 || decoded.height < 4) {
			result.warning("Texture is smaller than 4×4 pixels — BC compression requires at least 4×4.");
		}
		if (decoded.width > 8192 || decoded.height > 8192) {
			result.warning("Texture exceeds 8192 px in one dimension. KCD2 may not support this.");
		}
		if (profile == FormatProfile.NORMAL && ! decoded.isNormalMapLike) {
			result.warning("This texture doesn't look like a tangent-space normal map (expected a predominantly blue/purple image centred around (128,128,255)). If this is a colour texture, double-check your profile selection.");
		}
		if (profile == FormatProfile.NORMAL) {
			result.message("Normal maps are stored as BC5 (RG only). The Z channel will be reconstructed by the engine at runtime.");
		}
		
		// ── Step 2: re-encode to the target KCD2 format ───────────────────────
		byte[] kcdDds = encode(decoded, profile, result);
		if (kcdDds == null)
			return result;
		
		result.succeeded(kcdDds, profile.targetFormat.description);
		result.message("Conversion complete → " + profile.targetFormat.description + " (" + kcdDds.length + " bytes)");
		result.message(profile.guidance);
		
		return result;
	}
	
	private static DecodeResult decodeToRgba(byte[] data, String filename, TextureImportResult result) throws IOException {
		
		if (data.length >= 4 && readInt32LE(data, 0) == Dds.DdsFile.MAGIC) {
			return decodeDds(data, result);
		}
		
		// ── Generic image (PNG, JPEG, BMP, TIFF, TGA, …) ─────────────────────
		BufferedImage img = null;
		try {
			img = ImageIO.read(new ByteArrayInputStream(data));
		} catch (Exception ignored) {
		}
		
		if (img != null) {
			String ext = fileExtension(filename).toUpperCase();
			result.message("Loaded as " + ext + " image via ImageIO.");
			DDSUtil.DDSImage di = DDSUtil.DDSImage.fromBufferedImage(img);
			boolean normalLike = looksLikeNormalMap(di.pixels, img.getWidth(), img.getHeight());
			return new DecodeResult(di.pixels, img.getWidth(), img.getHeight(), ext + " image", normalLike);
		}
		
		result.warning("Could not parse the input file as a DDS or a standard image format.");
		result.actionRequired("Make sure the file is a valid DDS, PNG, JPEG, BMP, TIFF, or TGA.");
		return null;
	}
	
	private static DecodeResult decodeDds(byte[] data, TextureImportResult result) throws IOException {
		
		// Parse only the header fields we need here; pixel data is passed to DDSUtil.
		ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		buf.getInt();                      // magic (already checked)
		if (buf.getInt() != 124)
			throw new IOException("Unexpected DDS header size");
		buf.getInt();                      // flags
		int height = buf.getInt();
		int width = buf.getInt();
		buf.getInt();                      // pitchOrLinearSize
		buf.getInt();                      // depth
		int mipCount = buf.getInt();
		buf.position(buf.position() + 44); // 11 reserved ints
		if (buf.getInt() != 32)
			throw new IOException("DDS pixel-format size != 32");
		buf.getInt();                      // pfFlags
		int fourCC = buf.getInt();
		// rest of legacy pixel-format + caps: not needed here
		
		if (mipCount > 1)
			result.message("Source DDS has " + mipCount + " mip levels; only mip 0 will be imported.");
		
		// ── Detect format description for feedback ────────────────────────────
		String fmtName;
		boolean needsZReconstruct;
		boolean signed;
		
		if (fourCC == Dds.Pixel.FOURCC_DX10) {
			buf.position(128); // jump past main header
			int dxgi = buf.getInt();
			fmtName = DxgiFormat.dxgiName(dxgi);
			needsZReconstruct = (dxgi == DxgiFormat.BC5_UNORM.getValue() || dxgi == DxgiFormat.BC5_SNORM.getValue());
			signed = (dxgi == DxgiFormat.BC4_SNORM.getValue() || dxgi == DxgiFormat.BC5_SNORM.getValue());
		} else {
			fmtName = DxgiFormat.legacyFourccName(fourCC);
			needsZReconstruct = (fourCC == Dds.Pixel.FOURCC_ATI2 || fourCC == Dds.Pixel.FOURCC_BC5U || fourCC == Dds.Pixel.FOURCC_BC5S);
			signed = (fourCC == Dds.Pixel.FOURCC_BC4S || fourCC == Dds.Pixel.FOURCC_BC5S);
		}
		
		if (signed)
			result.message("Signed-normalised source format — values remapped to [0..255] on decode.");
		
		DDSUtil.DDSImage img = DDSUtil.decode(data);
		
		byte[] rgba = img.pixels;
		
		// BC5 → reconstruct the Z channel so the preview image looks correct
		if (needsZReconstruct)
			reconstructZ_inplace(rgba, width, height);
		
		boolean normalLike = needsZReconstruct || looksLikeNormalMap(rgba, width, height);
		return new DecodeResult(rgba, width, height, fmtName, normalLike);
	}
	
	private static void reconstructZ_inplace(byte[] rgba, int w, int h) {
		for (int i = 0; i < w * h; i++) {
			float nr = (rgba[i * 4] & 0xFF) / 127.5f - 1f;
			float ng = (rgba[i * 4 + 1] & 0xFF) / 127.5f - 1f;
			float nz = (float) Math.sqrt(Math.max(0.0, 1.0 - nr * nr - ng * ng));
			rgba[i * 4 + 2] = (byte) Math.round((nz + 1f) * 127.5f);
		}
	}
	
	private static byte[] encode(DecodeResult decoded, FormatProfile profile, TextureImportResult result) throws IOException {
		
		byte[] rgba = decoded.rgba;
		int w = decoded.width, h = decoded.height;
		
		return switch (profile.targetFormat) {
			
			case BC1 -> {
				result.message("Encoding to BC1 / DXT1 (no alpha).");
				yield DDSUtil.encodeDXT1(rgba, w, h);
			}
			
			case BC3 -> {
				// Downgrade to DXT1 if there is no meaningful alpha
				if (! hasSignificantAlpha(rgba)) {
					result.message("No significant alpha detected — encoding to BC1 / DXT1 instead of BC3.");
					yield DDSUtil.encodeDXT1(rgba, w, h);
				}
				result.message("Encoding to BC3 / DXT5 (with alpha).");
				yield DDSUtil.encodeDXT5(rgba, w, h);
			}
			
			case BC5 -> {
				result.message("Encoding to BC5 (RG normal / specular). Only R and G are stored.");
				yield DDSUtil.encodeBC5(rgba, w, h);
			}
			
			case BC7 -> {
				result.message("Encoding to BC7 (high quality, full RGBA).");
				yield DDSUtil.encodeBC7(rgba, w, h);
			}
			
			case UNCOMPRESSED_RGBA -> {
				result.message("Encoding as uncompressed RGBA8 (ID map — no compression).");
				yield DDSUtil.encodeUncompressedRGBA(rgba, w, h);
			}
		};
	}
	
	/** Heuristic: a normal map should have high blue and RG centred near 128. */
	private static boolean looksLikeNormalMap(byte[] rgba, int w, int h) {
		long sumR = 0, sumG = 0, sumB = 0;
		int n = Math.min(w * h, 4096);
		for (int i = 0; i < n; i++) {
			sumR += rgba[i * 4] & 0xFF;
			sumG += rgba[i * 4 + 1] & 0xFF;
			sumB += rgba[i * 4 + 2] & 0xFF;
		}
		return (sumB / n > 150) && (Math.abs(sumR / n - 128) < 60) && (Math.abs(sumG / n - 128) < 60);
	}
	
	/** True when any pixel has alpha < 250. */
	private static boolean hasSignificantAlpha(byte[] rgba) {
		for (int i = 3; i < rgba.length; i += 4)
			if ((rgba[i] & 0xFF) < 250)
				return true;
		return false;
	}
	
	private static boolean isPow2(int v) {
		return v > 0 && (v & (v - 1)) == 0;
	}
	
	private static int readInt32LE(byte[] b, int off) {
		return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8) | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
	}
	
	private static String fileExtension(String name) {
		if (name == null)
			return "";
		int i = name.lastIndexOf('.');
		return i >= 0 ? name.substring(i + 1) : "";
	}
	
	private record DecodeResult(byte[] rgba, int width, int height, String sourceFormatDescription,
								boolean isNormalMapLike) {
	}
}