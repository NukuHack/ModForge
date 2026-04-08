package image;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class TextureImporterTest {
	
	// ── Profile detection ─────────────────────────────────────────────────────
	
	private static void assertDdsMagic(byte[] dds) {
		assertNotNull(dds);
		assertTrue(dds.length >= 128, "DDS too short");
		assertEquals(0x44, dds[0] & 0xFF);
		assertEquals(0x44, dds[1] & 0xFF);
		assertEquals(0x53, dds[2] & 0xFF);
		assertEquals(0x20, dds[3] & 0xFF);
	}
	
	/** Create a solid-colour PNG via BufferedImage + ImageIO. */
	private static byte[] makeSolidPng(int w, int h, int r, int g, int b, int a) throws IOException {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		int argb = (a << 24) | (r << 16) | (g << 8) | b;
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++)
				img.setRGB(x, y, argb);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		javax.imageio.ImageIO.write(img, "PNG", baos);
		return baos.toByteArray();
	}
	
	/** Create a synthetic flat normal-map PNG (RGB = 128,128,220). */
	private static byte[] makeNormalMapPng(int w, int h) throws IOException {
		return makeSolidPng(w, h, 128, 128, 220, 255);
	}
	
	/** Create a flat RGBA8 byte array (for DDSUtil direct input). */
	private static byte[] makeSolidRgba(int w, int h, int r, int g, int b, int a) {
		byte[] out = new byte[w * h * 4];
		for (int i = 0; i < w * h; i++) {
			out[i * 4] = (byte) r;
			out[i * 4 + 1] = (byte) g;
			out[i * 4 + 2] = (byte) b;
			out[i * 4 + 3] = (byte) a;
		}
		return out;
	}
	
	@Test
	void detectDiffuse() {
		assertEquals(FormatProfile.DIFFUSE, FormatProfile.detect("body_dif.dds"));
	}
	
	@Test
	void detectNormal() {
		assertEquals(FormatProfile.NORMAL, FormatProfile.detect("body_nrm.dds"));
	}
	
	@Test
	void detectSpecular() {
		assertEquals(FormatProfile.SPECULAR, FormatProfile.detect("body_spec.dds"));
	}
	
	@Test
	void detectGloss() {
		assertEquals(FormatProfile.GLOSS, FormatProfile.detect("body_gloss.dds"));
	}
	
	@Test
	void detectAO() {
		assertEquals(FormatProfile.AMBIENT_OCC, FormatProfile.detect("body_ao.dds"));
	}
	
	@Test
	void detectEmissive() {
		assertEquals(FormatProfile.EMISSIVE, FormatProfile.detect("body_emissive.dds"));
	}
	
	@Test
	void detectMask() {
		assertEquals(FormatProfile.MASK, FormatProfile.detect("body_mask.dds"));
	}
	
	// ── PNG round-trip ────────────────────────────────────────────────────────
	
	@Test
	void detectIdMap() {
		assertEquals(FormatProfile.ID_MAP, FormatProfile.detect("body_id.dds"));
	}
	
	@Test
	void detectUnknown() {
		assertEquals(FormatProfile.UNKNOWN, FormatProfile.detect("body.dds"));
	}
	
	@Test
	void detectNullSafe() {
		assertEquals(FormatProfile.UNKNOWN, FormatProfile.detect(null));
	}
	
	@Test
	void detectCaseInsensitive() {
		assertEquals(FormatProfile.NORMAL, FormatProfile.detect("BODY_NRM.DDS"));
	}
	
	@Test
	void importPng_diffuse_producesBC1OrBC3() throws IOException {
		byte[] png = makeSolidPng(64, 64, 200, 150, 100, 255);
		TextureImportResult r = TextureImporter.importTexture(png, "texture_dif.png");
		
		assertTrue(r.isConversionSucceeded(), "Expected success: " + r.getWarnings());
		assertNotNull(r.getConvertedDdsBytes());
		assertEquals(FormatProfile.DIFFUSE, r.getProfile());
		assertTrue(r.isProfileAutoDetected());
		// Opaque diffuse → should pick BC1 (smaller)
		assertTrue(r.getOutputFormatDescription().contains("BC1") || r.getOutputFormatDescription().contains("BC3"), "Unexpected format: " + r.getOutputFormatDescription());
		// Valid DDS magic
		assertDdsMagic(r.getConvertedDdsBytes());
	}
	
	// ── Existing DDS formats pass through ────────────────────────────────────
	
	@Test
	void importPng_withAlpha_producesBC3() throws IOException {
		// Semi-transparent pixel (alpha=128) should force BC3
		byte[] png = makeSolidPng(32, 32, 100, 100, 100, 128);
		TextureImportResult r = TextureImporter.importTexture(png, "icon_dif.png");
		
		assertTrue(r.isConversionSucceeded());
		assertTrue(r.getOutputFormatDescription().contains("BC3") || r.getOutputFormatDescription().contains("DXT5"), "Transparent diffuse should use BC3, got: " + r.getOutputFormatDescription());
	}
	
	@Test
	void importPng_normalMap_producesBC5() throws IOException {
		// Synthetic normal map: average blue ~200, RG ~128
		byte[] png = makeNormalMapPng(64, 64);
		TextureImportResult r = TextureImporter.importTexture(png, "skin_nrm.png");
		
		assertTrue(r.isConversionSucceeded(), r.summary());
		assertEquals(FormatProfile.NORMAL, r.getProfile());
		assertTrue(r.getOutputFormatDescription().contains("BC5"), "Normal map should produce BC5, got: " + r.getOutputFormatDescription());
	}
	
	// ── Invalid input ─────────────────────────────────────────────────────────
	
	@Test
	void importPng_idMap_producesUncompressed() throws IOException {
		byte[] png = makeSolidPng(32, 32, 255, 0, 0, 255);
		TextureImportResult r = TextureImporter.importTexture(png, "body_id.png");
		
		assertTrue(r.isConversionSucceeded());
		assertEquals(FormatProfile.ID_MAP, r.getProfile());
		assertTrue(r.getOutputFormatDescription().toLowerCase().contains("uncompressed"), "ID map should be uncompressed, got: " + r.getOutputFormatDescription());
	}
	
	// ── TextureImportResult.summary() ────────────────────────────────────────
	
	@Test
	void importPng_bc7_manualProfile() throws IOException {
		byte[] png = makeSolidPng(16, 16, 80, 80, 80, 255);
		TextureImportResult r = TextureImporter.importTexture(png, "generic.png", FormatProfile.UNKNOWN);
		
		assertTrue(r.isConversionSucceeded(), r.summary());
		assertFalse(r.isProfileAutoDetected());
		assertTrue(r.getOutputFormatDescription().contains("BC7"), "UNKNOWN profile should use BC7, got: " + r.getOutputFormatDescription());
	}
	
	// ── Helpers ───────────────────────────────────────────────────────────────
	
	@Test
	void importDxt1_diffuse_recompresses() throws IOException {
		byte[] dxt1 = DDSUtil.encodeDXT1(makeSolidRgba(32, 32, 200, 100, 50, 255), 32, 32);
		TextureImportResult r = TextureImporter.importTexture(dxt1, "rock_dif.dds");
		
		assertTrue(r.isConversionSucceeded(), r.summary());
		assertDdsMagic(r.getConvertedDdsBytes());
		assertEquals(FormatProfile.DIFFUSE, r.getProfile());
	}
	
	@Test
	void importDxt5_mask_recompresses() throws IOException {
		byte[] dxt5 = DDSUtil.encodeDXT5(makeSolidRgba(32, 32, 255, 255, 255, 200), 32, 32);
		TextureImportResult r = TextureImporter.importTexture(dxt5, "armor_mask.dds");
		
		assertTrue(r.isConversionSucceeded(), r.summary());
		assertEquals(FormatProfile.MASK, r.getProfile());
		assertDdsMagic(r.getConvertedDdsBytes());
	}
	
	@Test
	void importGarbage_doesNotThrow() throws IOException {
		byte[] garbage = new byte[] { 1, 2, 3, 4, 5 };
		TextureImportResult r = TextureImporter.importTexture(garbage, "bad.dds");
		
		assertFalse(r.isConversionSucceeded());
		assertFalse(r.getWarnings().isEmpty());
		assertNotNull(r.getActionRequired());
	}
	
	@Test
	void summary_containsKeyInfo() throws IOException {
		byte[] png = makeSolidPng(16, 16, 128, 128, 128, 255);
		TextureImportResult r = TextureImporter.importTexture(png, "test_ao.png");
		
		String s = r.summary();
		assertTrue(s.contains("16×16") || s.contains("16x16"), "Summary should have dimensions");
		assertTrue(s.contains("Ambient"), "Summary should mention AO profile");
	}
}
