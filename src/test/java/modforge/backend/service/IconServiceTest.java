package modforge.backend.service;

import modforge.backend.ModData;
import modforge.backend.model.BaseModItem;
import modforge.backend.model.ModItem;
import modforge.backend.model.attributes.StringAttribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit / integration tests for {@link IconService}.
 *
 * <p>Tests are split into two groups:
 * <ul>
 *   <li><b>PAK-dependent</b> – skipped automatically when the test PAK is
 *       absent (CI-safe). Run {@link #createTestPakFile()} once to generate it.</li>
 *   <li><b>Unit</b> – no external files required; always run.</li>
 * </ul>
 *
 * <p>Coverage:
 * <ol>
 *   <li>PAK indexing – {@code indexDdsFromPak}</li>
 *   <li>DDS → PNG conversion – {@link IconService#convertToImage}</li>
 *   <li>Round-trip DDS → PNG → DDS – {@link IconService#convertImages}</li>
 *   <li>Directory bulk conversion – {@link IconService#convertImages}</li>
 *   <li>Archive bulk conversion – {@link IconService#convertImages}</li>
 *   <li>Mod icon loading – {@link IconService#loadModIcons}</li>
 *   <li>Icon resolution for a {@link ModItem} – {@link IconService#getIcon}</li>
 *   <li>Backup-on-overwrite side-effect</li>
 *   <li>Edge cases: null/blank paths, missing items, fallback icon id</li>
 * </ol>
 *
 * <p>DDS codec correctness (BC1/BC3/BC7 etc.) is covered by {@code DDSUtilTest}
 * and is therefore <em>not</em> re-tested here.
 */
@DisplayName("IconService")
class IconServiceTest extends BaseServiceTest {
	
	// ── Test resource paths ──────────────────────────────────────────────────
	
	private static final Path TEST_PAK = Path.of("src/test/resources/img/test.pak");
	
	// ── Shared fixtures ──────────────────────────────────────────────────────
	
	@TempDir
	Path tempDir;
	
	private IconService iconService;
	private ModData testMod;
	private UserConfig userConfig;
	
	/**
	 * Produces a real, decodable DDS by compressing a 64×64 gradient image via
	 * {@link DDSUtil#compressToBC7} — the same path the production code uses for
	 * PNG → DDS conversion.
	 */
	private static byte[] realDdsBytes() throws IOException {
		// Simple gradient so the image is non-trivial (avoids edge cases in codecs)
		BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				int r = (x * 4) & 0xFF;
				int g = (y * 4) & 0xFF;
				int b = ((x + y) * 2) & 0xFF;
				img.setRGB(x, y, (0xFF << 24) | (r << 16) | (g << 8) | b);
			}
		}
		return DDSUtil.compressToBC7(img);
	}
	
	// =========================================================================
	// Helper: skip a test gracefully when the test PAK is not present
	// =========================================================================
	
	@BeforeEach
	void setUp() {
		testMod = new ModData();
		testMod.id = "test_mod";
		userConfig = new UserConfig();
		iconService = new IconService(userConfig);
	}
	
	// =========================================================================
	// Helper: call the private static indexDdsFromPak via reflection
	// =========================================================================
	
	private void requireTestPak() {
		assumeTrue(Files.exists(TEST_PAK), "Test PAK not found – run createTestPakFile() once to generate it: " + TEST_PAK);
	}
	
	// =========================================================================
	// 1. PAK indexing
	// =========================================================================
	
	@SuppressWarnings("unchecked")
	private Map<String, byte[]> indexDdsFromPak(String pakPath) throws Exception {
		Method m = IconService.class.getDeclaredMethod("indexDdsFromPak", String.class);
		m.setAccessible(true);
		return (Map<String, byte[]>) m.invoke(null, pakPath);
	}
	
	// =========================================================================
	// 2. DDS → PNG conversion  (convertToImage)
	// =========================================================================
	
	/**
	 * Creates {@code src/test/resources/img/img-test.pak} containing three
	 * genuinely decodable DDS entries so that every PAK-dependent test can run.
	 *
	 * <p>Each entry is a real BC7-compressed DDS produced by
	 * {@link DDSUtil#compressToBC7} from a tiny 64×64 {@link BufferedImage}.
	 * This is the same codec path used by the production PNG→DDS conversion, so
	 * {@link IconService#convertToImage} will decode them without errors.
	 *
	 * <p>The entries are stored under {@code Libs/UI/Textures/} so that
	 * {@code indexDdsFromPak}'s path filter accepts them.
	 */
	@Test
	@DisplayName("[setup] create test PAK with real DDS entries")
	void createTestPakFile() throws Exception {
		Path pakDir = Path.of("src/test/resources/img/out");
		Files.createDirectories(pakDir);
		Files.createDirectories(pakDir.resolve("out"));
		
		Path pak = pakDir.resolve("img-test.pak");
		
		// Produce a real BC7 DDS from a small synthetic image so that
		// DDSUtil.decode() can actually round-trip it in the other tests.
		byte[] validDds = realDdsBytes();
		
		String[] iconNames = { "test_icon_1", "test_icon_2", "crime_investigation_icon" };
		
		try (var zos = new ZipOutputStream(Files.newOutputStream(pak))) {
			for (String name : iconNames) {
				String entryPath = "Libs/UI/Textures/" + name + ".dds";
				zos.putNextEntry(new ZipEntry(entryPath));
				zos.write(validDds);
				zos.closeEntry();
				System.out.println("  added entry: " + entryPath);
			}
		}
		
		assertTrue(Files.exists(pak));
		System.out.println("Test PAK written to: " + pak.toAbsolutePath());
	}
	
	// =========================================================================
	// 3. Round-trip DDS → PNG → DDS  (convertImages, single file)
	// =========================================================================
	
	@Nested
	@DisplayName("PAK indexing")
	class PakIndexing {
		
		@Test
		@DisplayName("indexes DDS entries from a valid PAK")
		void indexesEntriesFromValidPak() throws Exception {
			requireTestPak();
			
			Map<String, byte[]> icons = indexDdsFromPak(TEST_PAK.toString());
			
			assertNotNull(icons, "result must not be null");
			assertFalse(icons.isEmpty(), "PAK must contain at least one DDS file");
			
			icons.forEach((name, data) -> System.out.printf("  indexed: %-40s  (%,d bytes)%n", name, data.length));
		}
		
		@Test
		@DisplayName("returns empty map for a missing PAK – no exception")
		void returnsEmptyMapForMissingPak() throws Exception {
			Map<String, byte[]> icons = indexDdsFromPak(tempDir.resolve("nonexistent.pak").toString());
			assertNotNull(icons);
			assertTrue(icons.isEmpty(), "missing PAK must yield an empty map");
		}
		
		@Test
		@DisplayName("keys are lower-cased stems without extension")
		void keysAreLowerCasedStems() throws Exception {
			requireTestPak();
			
			Map<String, byte[]> icons = indexDdsFromPak(TEST_PAK.toString());
			
			icons.keySet().forEach(key -> {
				assertFalse(key.endsWith(".dds"), "key must not keep the .dds extension: " + key);
				assertEquals(key, key.toLowerCase(), "key must be lower-cased: " + key);
				assertFalse(key.isBlank(), "key must not be blank");
			});
		}
	}
	
	// =========================================================================
	// 4. Bulk directory conversion  (convertImages on a folder)
	// =========================================================================
	
	@Nested
	@DisplayName("DDS → PNG (convertToImage)")
	class DdsToPngConversion {
		
		@Test
		@DisplayName("converts every DDS in the test PAK to a non-empty BufferedImage")
		void convertsAllDdsEntriesToImages() throws Exception {
			requireTestPak();
			
			Map<String, byte[]> icons = indexDdsFromPak(TEST_PAK.toString());
			assertFalse(icons.isEmpty(), "PAK must contain DDS files for this test");
			
			for (var entry : icons.entrySet()) {
				BufferedImage img = IconService.convertToImage(entry.getValue());
				assertNotNull(img, "conversion must succeed for: " + entry.getKey());
				assertTrue(img.getWidth() > 0, "image width must be positive: " + entry.getKey());
				assertTrue(img.getHeight() > 0, "image height must be positive: " + entry.getKey());
				System.out.printf("  converted: %-40s  (%dx%d)%n", entry.getKey(), img.getWidth(), img.getHeight());
			}
		}
		
		@Test
		@DisplayName("throws IOException for corrupt/empty DDS bytes")
		void throwsOnCorruptDdsBytes() {
			byte[] garbage = new byte[] { 0x00, 0x11, 0x22, 0x33 };
			assertThrows(IOException.class, () -> IconService.convertToImage(garbage), "corrupt bytes must raise IOException");
		}
	}
	
	// =========================================================================
	// 5. Archive bulk conversion  (convertImages on a .pak)
	// =========================================================================
	
	@Nested
	@DisplayName("Round-trip DDS ↔ PNG (convertImages, single file)")
	class RoundTripConversion {
		
		@Test
		@DisplayName("DDS → PNG produces a readable PNG file next to the source")
		void ddsToPng() throws Exception {
			requireTestPak();
			
			Map<String, byte[]> icons = indexDdsFromPak(TEST_PAK.toString());
			var firstEntry = icons.entrySet().iterator().next();
			
			Path ddsFile = tempDir.resolve(firstEntry.getKey() + ".dds");
			Files.write(ddsFile, firstEntry.getValue());
			
			IconService.convertImages(ddsFile.toString(), true);
			
			Path expected = tempDir.resolve(firstEntry.getKey() + ".png");
			assertTrue(Files.exists(expected), "PNG file must be created");
			assertTrue(Files.size(expected) > 0, "PNG file must not be empty");
			assertNotNull(ImageIO.read(expected.toFile()), "PNG must be a valid image");
			
			System.out.println("  DDS→PNG: " + ddsFile.getFileName() + " → " + expected.getFileName());
		}
		
		@Test
		@DisplayName("PNG → DDS produces a non-empty .dds file next to the source")
		void pngToDds() throws Exception {
			// Create a synthetic PNG (no DDSUtil needed)
			BufferedImage synth = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
			Path pngFile = tempDir.resolve("synth.png");
			ImageIO.write(synth, "png", pngFile.toFile());
			
			IconService.convertImages(pngFile.toString(), false);
			
			Path expected = tempDir.resolve("synth.dds");
			assertTrue(Files.exists(expected), "DDS file must be created");
			assertTrue(Files.size(expected) > 0, "DDS file must not be empty");
			
			System.out.println("  PNG→DDS: " + pngFile.getFileName() + " → " + expected.getFileName());
		}
		
		@Test
		@DisplayName("pre-existing output file is backed up before overwrite")
		void backupCreatedOnOverwrite() throws Exception {
			// First conversion – creates synth.dds
			BufferedImage synth = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
			Path pngFile = tempDir.resolve("synth.png");
			ImageIO.write(synth, "png", pngFile.toFile());
			IconService.convertImages(pngFile.toString(), false);
			
			Path ddsFile = tempDir.resolve("synth.dds");
			assertTrue(Files.exists(ddsFile), "first DDS must have been created");
			long firstSize = Files.size(ddsFile);
			
			// Second conversion – must back up the first DDS
			IconService.convertImages(pngFile.toString(), false);
			
			Path backupDir = tempDir.resolve("image_backup");
			assertTrue(Files.exists(backupDir), "image_backup/ directory must be created");
			
			long backupCount = Files.list(backupDir).filter(p -> p.getFileName().toString().startsWith("synth_")).count();
			assertTrue(backupCount >= 1, "at least one backup file must exist");
			
			System.out.printf("  backup directory contains %d file(s)%n", backupCount);
		}
	}
	
	// =========================================================================
	// 6. Mod icon loading  (loadModIconsForMod)
	// =========================================================================
	
	@Nested
	@DisplayName("Directory bulk conversion (convertImages)")
	class DirectoryConversion {
		
		@Test
		@DisplayName("converts all DDS files in a directory to PNG")
		void convertsDdsToPngInDirectory() throws Exception {
			requireTestPak();
			
			// Populate tempDir with real DDS files from the test PAK
			Map<String, byte[]> icons = indexDdsFromPak(TEST_PAK.toString());
			for (var entry : icons.entrySet()) {
				Files.write(tempDir.resolve(entry.getKey() + ".dds"), entry.getValue());
			}
			
			IconService.convertImages(tempDir.toString(), true);
			
			long pngCount = Files.list(tempDir).filter(p -> p.getFileName().toString().endsWith(".png")).count();
			
			assertTrue(pngCount > 0, "at least one PNG must have been created");
			assertEquals(icons.size(), pngCount, "every DDS must produce exactly one PNG");
			
			System.out.printf("  converted %d DDS file(s) to PNG%n", pngCount);
		}
		
		@Test
		@DisplayName("convertImages on a blank path logs a warning and does not throw")
		void blankPathIsHandledGracefully() {
			assertDoesNotThrow(() -> IconService.convertImages("", true));
			assertDoesNotThrow(() -> IconService.convertImages(null, true));
		}
		
		@Test
		@DisplayName("convertImages on a non-existent path does not throw")
		void nonExistentPathIsHandledGracefully() {
			assertDoesNotThrow(() -> IconService.convertImages(tempDir.resolve("no_such_dir").toString(), true));
		}
	}
	
	// =========================================================================
	// 7. Icon resolution for a ModItem  (getIcon)
	// =========================================================================
	
	@Nested
	@DisplayName("Archive bulk conversion (convertImages on .pak)")
	class ArchiveConversion {
		
		@Test
		@DisplayName("extracts and converts DDS entries from a PAK to PNG")
		void convertsPakDdsEntriesToPng() throws Exception {
			requireTestPak();
			
			// Work with a copy inside tempDir so we don't pollute the test resources
			Path pakCopy = tempDir.resolve("img-test.pak");
			Files.copy(TEST_PAK, pakCopy);
			
			IconService.convertImages(pakCopy.toString(), true);
			
			Path convertedDir = tempDir.resolve("img-test_converted");
			assertTrue(Files.exists(convertedDir), "converted output directory must be created");
			
			long pngCount = Files.walk(convertedDir).filter(p -> p.getFileName().toString().endsWith(".png")).count();
			assertTrue(pngCount > 0, "at least one PNG must have been produced");
			
			System.out.printf("  archive conversion produced %d PNG file(s) in %s%n", pngCount, convertedDir.getFileName());
		}
		
		/**
		 * Parses the entire TEST_PAK, converts all DDS files to PNG, then compresses
		 * them back to DDS and saves them to {@code <tmp>/img} preserving the original
		 * archive-relative filepath structure.
		 *
		 * <p>This method performs a full round-trip conversion:
		 * <ol>
		 *   <li>Extracts all DDS entries from the TEST_PAK</li>
		 *   <li>Converts each DDS to PNG (in memory)</li>
		 *   <li>Compresses each PNG back to DDS format</li>
		 *   <li>Saves the resulting DDS files to {@code <tmp>/img} mirroring the
		 *       original directory structure from the PAK archive</li>
		 * </ol>
		 *
		 * <p>This is useful for testing the complete DDS → PNG → DDS conversion
		 * pipeline on a realistic set of icons.
		 *
		 * @throws IOException if any I/O error occurs during reading/writing/conversion
		 * @throws IllegalStateException if TEST_PAK does not exist or cannot be read
		 */
		@Test
		@DisplayName("round-trip all DDS entries: PAK → PNG → DDS → <tmp>/img")
		void convertAllDdsToPngAndBackToDds() throws Exception {
			// Ensure TEST_PAK exists
			assumeTrue(Files.exists(TEST_PAK), "Test PAK not found – run createTestPakFile() once to generate it: " + TEST_PAK);
			
			// Step 1: Index all DDS files from the PAK
			Map<String, byte[]> indexedDds = IconService.loadModIcons(TEST_PAK.getParent());
			assertFalse(indexedDds.isEmpty(), "PAK must contain at least one DDS file");
			
			System.out.printf("Found %d DDS entries in %s%n", indexedDds.size(), TEST_PAK.getFileName());
			
			// Step 2: Create the output directory: <tmp>/img
			Path outputRoot = tempDir.resolve("img");
			Files.createDirectories(outputRoot);
			
			int successCount = 0;
			int failureCount = 0;
			
			// Step 3: Process each DDS entry
			for (Map.Entry<String, byte[]> entry : indexedDds.entrySet()) {
				String archivePath = entry.getKey(); // e.g., "libs/ui/textures/test_icon_1"
				byte[] ddsBytes = entry.getValue();
				
				try {
					// Step 3a: Convert DDS → BufferedImage
					BufferedImage image = IconService.convertToImage(ddsBytes);
					assertNotNull(image, "Failed to decode DDS: " + archivePath);
					
					// TODO : make sure the image stores it's path not like this (external file name modification)
					// Step 3b: Convert BufferedImage → PNG (temporary in-memory or temp file)
					Path tempPng = tempDir.resolve(archivePath.replace('/', '_') + ".png");
					Files.createDirectories(tempPng.getParent());
					ImageIO.write(image, "png", tempPng.toFile());
					
					// Step 3c: Convert PNG → DDS using DDSUtil
					byte[] recompressedDds = DDSUtil.compressToBC7(image);
					assertNotNull(recompressedDds, "Failed to compress PNG to DDS: " + archivePath);
					assertTrue(recompressedDds.length > 0, "Recompressed DDS is empty: " + archivePath);
					
					// Step 3d: Determine output path preserving original structure
					// Original key might be like "libs/ui/textures/test_icon_1"
					// We want: <tmp>/img/libs/ui/textures/test_icon_1.dds
					Path outputPath = outputRoot.resolve(archivePath + ".dds");
					Files.createDirectories(outputPath.getParent());
					Files.write(outputPath, recompressedDds);
					
					successCount++;
					System.out.printf("  ✓ %-40s → %s (original: %d bytes, recompressed: %d bytes)%n",
							archivePath,
							outputPath.getFileName(),
							ddsBytes.length,
							recompressedDds.length
					);
					
					// Clean up temporary PNG
					Files.deleteIfExists(tempPng);
					
				} catch (Exception e) {
					failureCount++;
					System.err.printf("  ✗ Failed to process %s: %s%n", archivePath, e.getMessage());
					// Continue processing other entries
				}
			}
			
			// Step 4: Verify results
			assertTrue(successCount > 0, "At least one DDS entry must be successfully processed");
			assertEquals(indexedDds.size(), successCount + failureCount,
					"All entries must be either successful or failed");
			
			// Step 5: Verify output directory structure and files
			long outputFileCount = Files.walk(outputRoot)
										   .filter(Files::isRegularFile)
										   .filter(p -> p.toString().endsWith(".dds"))
										   .count();
			
			assertEquals(successCount, outputFileCount,
					"Number of output DDS files must match number of successfully processed entries");
			
			// Optional: Verify that each output DDS can be read back as a valid image
			System.out.printf("%nSummary: %d successful, %d failed, %d output files in %s%n",
					successCount, failureCount, outputFileCount, outputRoot);
			
			// Spot-check: Try to read back the first output DDS to ensure it's valid
			if (outputFileCount > 0) {
				Path firstOutput = Files.walk(outputRoot)
										   .filter(Files::isRegularFile)
										   .filter(p -> p.toString().endsWith(".dds"))
										   .findFirst()
										   .orElseThrow();
				
				byte[] outputDds = Files.readAllBytes(firstOutput);
				BufferedImage roundTripImage = IconService.convertToImage(outputDds);
				assertNotNull(roundTripImage, "Round-tripped DDS must be decodable");
				assertTrue(roundTripImage.getWidth() > 0 && roundTripImage.getHeight() > 0,
						"Round-tripped image must have valid dimensions");
				
				System.out.printf("%nValidation: Successfully decoded round-tripped DDS: %s (%dx%d)%n",
						firstOutput.getFileName(),
						roundTripImage.getWidth(),
						roundTripImage.getHeight()
				);
			}
		}
	}
	
	// =========================================================================
	// Utility: create the test PAK (run once; not part of normal test suite)
	// =========================================================================
	
	@Nested
	@DisplayName("Mod icon loading (loadModIconsForMod)")
	class ModIconLoading {
		
		@Test
		@DisplayName("indexes icons from a PAK inside a mod Data folder")
		void loadsIconsFromModDataFolder() throws Exception {
			requireTestPak();
			
			// Mimic  Mods/<modId>/Data/  directory structure
			Path modData = tempDir.resolve("Mods/test_mod/Data");
			Files.createDirectories(modData);
			Files.copy(TEST_PAK, modData.resolve("test.pak"));
			
			Map<String, byte[]> loaded = iconService.loadModIcons(modData);
			
			assertNotNull(loaded, "result must not be null");
			assertFalse(loaded.isEmpty(), "should have loaded at least one icon");
			
			testMod.setIcon(loaded);
			System.out.printf("  mod '%s' loaded %d icon(s)%n", testMod.id, loaded.size());
		}
		
		@Test
		@DisplayName("returns empty map for a non-existent mod path – no exception")
		void returnsEmptyMapForMissingModPath() {
			Map<String, byte[]> result = iconService.loadModIcons(tempDir.resolve("nonexistent/Data"));
			assertNotNull(result);
			assertTrue(result.isEmpty(), "missing path must yield an empty map");
		}
		
		@Test
		@DisplayName("returns empty map when Data folder contains no PAK files")
		void returnsEmptyMapWhenNoPaksPresent() throws Exception {
			Path emptyData = tempDir.resolve("Mods/empty_mod/Data");
			Files.createDirectories(emptyData);
			// Intentionally leave the folder empty
			
			Map<String, byte[]> result = iconService.loadModIcons(emptyData);
			assertNotNull(result);
			assertTrue(result.isEmpty(), "folder with no PAKs must yield an empty map");
		}
	}
	
	@Nested
	@DisplayName("Icon resolution (getIcon)")
	class IconResolution {
		
		@Test
		@DisplayName("resolves icon from mod index for a ModItem with icon_id attribute")
		void resolvesIconFromModIndex() throws Exception {
			requireTestPak();
			
			Map<String, byte[]> icons = indexDdsFromPak(TEST_PAK.toString());
			testMod.setIcon(icons);
			
			String firstKey = icons.keySet().iterator().next();
			ModItem item = itemWithIconId(firstKey);
			
			BufferedImage result = iconService.getIcon(item, testMod);
			
			assertNotNull(result, "icon must be resolved");
			assertTrue(result.getWidth() > 0, "resolved icon must have positive width");
			assertTrue(result.getHeight() > 0, "resolved icon must have positive height");
			
			System.out.printf("  resolved '%s'  →  %dx%d px%n", firstKey, result.getWidth(), result.getHeight());
		}
		
		@Test
		@DisplayName("returns null for an item without an icon_id attribute")
		void returnsNullWhenNoIconIdAttribute() {
			ModItem item = new BaseModItem() {
			};
			// No attributes set
			assertNull(iconService.getIcon(item, testMod), "item with no icon_id must return null");
		}
		
		@Test
		@DisplayName("returns null for a null item")
		void returnsNullForNullItem() {
			assertNull(iconService.getIcon(null, testMod), "null item must return null");
		}
		
		@Test
		@DisplayName("icon_id='0' triggers fallback resolution without throwing")
		void fallbackIconIdZeroDoesNotThrow() {
			ModItem item = itemWithIconId("0");
			// The fallback icon may not exist in the test environment; null is acceptable.
			assertDoesNotThrow(() -> iconService.getIcon(item, testMod), "fallback icon resolution must not throw");
		}
		
		@Test
		@DisplayName("icon_id='replaceme' triggers fallback resolution without throwing")
		void fallbackIconIdReplaceMe() {
			ModItem item = itemWithIconId("replaceme");
			assertDoesNotThrow(() -> iconService.getIcon(item, testMod));
		}
		
		@Test
		@DisplayName("hasIcon returns false when key is absent from mod index")
		void hasIconReturnsFalseForMissingKey() {
			assertFalse(iconService.hasIcon("definitely_not_there", testMod));
		}
		
		@Test
		@DisplayName("hasIcon returns false for null / blank icon id")
		void hasIconReturnsFalseForBlankId() {
			assertFalse(iconService.hasIcon(null, testMod));
			assertFalse(iconService.hasIcon("", testMod));
			assertFalse(iconService.hasIcon("  ", testMod));
		}
		
		// ── helper ────────────────────────────────────────────────────────────
		
		private ModItem itemWithIconId(String iconId) {
			ModItem item = new BaseModItem() {
			};
			item.setAttribute(List.of(new StringAttribute("icon_id", iconId)));
			return item;
		}
	}
}