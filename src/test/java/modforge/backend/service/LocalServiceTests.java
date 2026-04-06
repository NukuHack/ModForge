package modforge.backend.service;

import modforge.Util;
import modforge.backend.ModData;
import modforge.backend.model.Language;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConfigService, ItemService, LocalService, JsonIO.
 *
 * Resource layout (mirroring DDSUtilTest.readResourceBytes):
 *   resources/
 *     cfg/
 *       mod.cfg
 *       autoexec.cfg
 *     item_xml/
 *       item__lootinfo.xml
 *       perk-data.pak          ← ZIP containing XML entries
 *     json/
 *       userconfig.json
 *     lang_xml/
 *       English_xml.pak        ← ZIP containing localization XML
 *       eng-local.xml
 *
 * Most write-tests operate entirely in-memory or in JUnit temp dirs.
 * Only the "full" integration tests write into resources/.../output/.
 */
class LocalServiceTests {
	
	@BeforeAll
	public static void setTempDir() {
		tmp = Path.of("src/test/resources/tmp");
	}
	
	@AfterAll
	public static void remTempDir() {
		Util.deleteRecursively(tmp);
	}
	
	// ---------------------------------------------------------------
	// Resource helpers (same pattern as DDSUtilTest)
	// ---------------------------------------------------------------
	
	private static byte[] readResourceBytes(String resourceName) throws IOException {
		try (InputStream is = ServiceTests.class.getClassLoader().getResourceAsStream(resourceName)) {
			if (is != null)
				return is.readAllBytes();
		}
		Path path = Paths.get("src/test/resources/" + resourceName);
		if (Files.exists(path))
			return Files.readAllBytes(path);
		throw new FileNotFoundException("Resource not found: " + resourceName);
	}
	
	private static String readResourceString(String resourceName) throws IOException {
		return new String(readResourceBytes(resourceName), StandardCharsets.UTF_8);
	}
	
	// ---------------------------------------------------------------
	// Static test data loaded once
	// ---------------------------------------------------------------
	
	private static byte[] modCfgBytes;
	private static byte[] autoexecCfgBytes;
	private static byte[] itemXmlBytes;         // item__lootinfo.xml
	private static byte[] perkDataPakBytes;     // perk-data.pak (ZIP)
	private static byte[] userConfigJsonBytes;
	private static byte[] englishPakBytes;      // English_xml.pak (ZIP)
	private static byte[] engLocalXmlBytes;     // eng-local.xml (raw localization XML)
	
	private static Path tmp;
	
	@BeforeAll
	static void loadResources() throws IOException {
		modCfgBytes = readResourceBytes("cfg/mod.cfg");
		autoexecCfgBytes = readResourceBytes("cfg/autoexec.cfg");
		itemXmlBytes = readResourceBytes("item_xml/item__lootinfo.xml");
		perkDataPakBytes = readResourceBytes("item_xml/perk-data.pak");
		userConfigJsonBytes = readResourceBytes("json/userconfig.json");
		englishPakBytes = readResourceBytes("lang_xml/English_xml.pak");
		engLocalXmlBytes = readResourceBytes("lang_xml/eng-local.xml");
	}
	// ================================================================
	// LocalService tests
	// ================================================================
	
	@Nested
	@DisplayName("LocalService localization")
	class LocalServiceTest {
		
		@Test
		@DisplayName("parseLocalizationXml: in-memory minimal table")
		void parseMinimalTable() throws Exception {
			String xml = """
					<Table>
					  <Row><Cell>ui_sword</Cell><Cell/><Cell>Iron Sword</Cell></Row>
					  <Row><Cell>ui_shield</Cell><Cell/><Cell>Wooden Shield</Cell></Row>
					</Table>
					""";
			LocalService ls = makeLocalService(null);
			Map<String, String> result = ls.parseLocalizationXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
			assertEquals("Iron Sword", result.get("ui_sword"));
			assertEquals("Wooden Shield", result.get("ui_shield"));
		}
		
		@Test
		@DisplayName("parseLocalizationXml: empty table returns empty map")
		void parseEmptyTable() throws Exception {
			String xml = "<Table></Table>";
			LocalService ls = makeLocalService(null);
			Map<String, String> result = ls.parseLocalizationXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
			assertTrue(result.isEmpty());
		}
		
		@Test
		@DisplayName("parseLocalizationXml: rows with blank keys are skipped")
		void parseBlankKeySkipped() throws Exception {
			String xml = """
					<Table>
					  <Row><Cell>   </Cell><Cell/><Cell>ShouldBeSkipped</Cell></Row>
					  <Row><Cell>ui_good</Cell><Cell/><Cell>GoodValue</Cell></Row>
					</Table>
					""";
			LocalService ls = makeLocalService(null);
			Map<String, String> result = ls.parseLocalizationXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
			assertFalse(result.containsKey(""));
			assertEquals("GoodValue", result.get("ui_good"));
		}
		
		@Test
		@DisplayName("parseLocalizationXml: parses eng-local.xml resource")
		void parseEngLocalResource() throws Exception {
			LocalService ls = makeLocalService(null);
			Map<String, String> result = ls.parseLocalizationXml(new ByteArrayInputStream(engLocalXmlBytes));
			assertFalse(result.isEmpty(), "eng-local.xml should contain localization entries");
		}
		
		@Test
		@DisplayName("makeLocalizationXml: roundtrip through parse")
		void makeAndReparseLocalizationXml() throws Exception {
			Map<String, String> entries = new LinkedHashMap<>();
			entries.put("ui_sword", "Iron Sword");
			entries.put("ui_potion", "Health <Potion> & Co");
			
			LocalService ls = makeLocalService(null);
			String xml = ls.makeLocalizationXml(entries);
			
			// Re-parse
			Map<String, String> reparsed = ls.parseLocalizationXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
			assertEquals("Iron Sword", reparsed.get("ui_sword"));
			assertEquals("Health <Potion> & Co", reparsed.get("ui_potion"));
		}
		
		@Test
		@DisplayName("makeLocalizationXml: blank keys are omitted")
		void makeLocalizationXmlBlankKeyOmitted() throws Exception {
			Map<String, String> entries = new LinkedHashMap<>();
			entries.put("", "should be skipped");
			entries.put("   ", "also skipped");
			entries.put("ui_ok", "Present");
			
			LocalService ls = makeLocalService(null);
			String xml = ls.makeLocalizationXml(entries);
			
			Map<String, String> reparsed = ls.parseLocalizationXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
			assertEquals(1, reparsed.size());
			assertEquals("Present", reparsed.get("ui_ok"));
		}
		
		@Test
		@DisplayName("loadLocalization: returns empty map when game dir is null")
		void loadLocalizationNullDir() {
			LocalService ls = makeLocalService(null);
			Map<Language, Map<String, String>> result = ls.loadLocalization(null);
			assertTrue(result.isEmpty());
		}
		
		@Test
		@DisplayName("loadLocalization: loads English_xml.pak resource")
		void loadLocalizationFromPak() throws IOException {
			// Set up a fake game dir with the English_xml.pak in the expected location
			// LocalService uses Util.allLocPaths(root) to discover paks;
			// the path is typically <root>/Localization/English_xml.pak
			Path locDir = tmp.resolve("Localization");
			Files.createDirectories(locDir);
			Files.write(locDir.resolve("English_xml.pak"), englishPakBytes);
			
			LocalService ls = makeLocalService(tmp.toString());
			Map<Language, Map<String, String>> result = ls.loadLocalization(tmp.toString());
			assertFalse(result.isEmpty(), "Should have loaded at least one language");
			assertTrue(result.containsKey(Language.ENGLISH));
			assertFalse(result.get(Language.ENGLISH).isEmpty());
		}
		
		@Test
		@DisplayName("resolve: mod-local string takes precedence over base game")
		void resolveModOverridesBase() {
			LocalService ls = makeLocalService(tmp.toString());
			
			// Build a mod with a custom map
			ModData mod = new ModData();
			mod.id = "test-mod";
			Map<Language, Map<String, String>> modLocal = new EnumMap<>(Language.class);
			Map<String, String> modStrings = new HashMap<>();
			modStrings.put("ui_sword", "Mod Sword Name");
			modLocal.put(Language.ENGLISH, modStrings);
			mod.setLocal(modLocal);
			
			String resolved = ls.resolve("ui_sword", mod, Language.ENGLISH);
			assertEquals("Mod Sword Name", resolved);
		}
		
		@Test
		@DisplayName("resolve: returns null for unknown key")
		void resolveUnknownKey() {
			LocalService ls = makeLocalService(tmp.toString());
			ModData mod = new ModData();
			mod.id = "test-mod";
			mod.setLocal(Collections.emptyMap());
			assertNull(ls.resolve("definitely_not_a_key_xyz", mod, Language.ENGLISH));
		}
		
		@Test
		@DisplayName("writeModLocalization: writes per-language files to temp dir")
		void writeModLocalizationTemp() throws Exception {
			Map<Language, Map<String, String>> local = new EnumMap<>(Language.class);
			local.put(Language.ENGLISH, new LinkedHashMap<>(Map.of("ui_item", "My Item")));
			
			ModData mod = new ModData();
			mod.id = "loc-mod";
			mod.setLocal(local);
			
			LocalService ls = makeLocalService(tmp.toString());
			assertDoesNotThrow(() -> ls.writeModLocalization(mod));
		}
		
		// --- full: write to resources output dir ---
		
		@Test
		@DisplayName("[full] parse English_xml.pak and write to resources/lang_xml/output/")
		void writeLocalizationFull() throws IOException {
			// Load pak
			Path locDir = tmp.resolve("Localization");
			Files.createDirectories(locDir);
			Files.write(locDir.resolve("English_xml.pak"), englishPakBytes);
			
			LocalService ls = makeLocalService(tmp.toString());
			Map<Language, Map<String, String>> result = ls.loadLocalization(tmp.toString());
			assertFalse(result.isEmpty());
			
			// Write as a mod
			Path outBase = Paths.get("src/test/resources/lang_xml/output");
			Files.createDirectories(outBase);
			
			UserService us = new UserService();
			us.gameDirectory = outBase.toString();
			us.language = Language.ENGLISH;
			
			ModData mod = new ModData();
			mod.id = "eng-output-mod";
			mod.setLocal(result);
			
			LocalService outLs = new LocalService(us);
			assertDoesNotThrow(() -> outLs.writeModLocalization(mod));
		}
		
		// helper
		private LocalService makeLocalService(String gameDir) {
			UserService us = new UserService();
			us.gameDirectory = gameDir;
			us.language = Language.ENGLISH;
			return new LocalService(us);
		}
	}
}