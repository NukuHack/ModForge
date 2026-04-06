package modforge.backend.service;

import modforge.Util;
import modforge.backend.ModData;
import org.junit.jupiter.api.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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
class ServiceTests {
	
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
			if (is != null) return is.readAllBytes();
		}
		Path path = Paths.get("src/test/resources/" + resourceName);
		if (Files.exists(path)) return Files.readAllBytes(path);
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
		modCfgBytes        = readResourceBytes("cfg/mod.cfg");
		autoexecCfgBytes   = readResourceBytes("cfg/autoexec.cfg");
		itemXmlBytes       = readResourceBytes("item_xml/item__lootinfo.xml");
		perkDataPakBytes   = readResourceBytes("item_xml/perk-data.pak");
		userConfigJsonBytes = readResourceBytes("json/userconfig.json");
		englishPakBytes    = readResourceBytes("lang_xml/English_xml.pak");
		engLocalXmlBytes   = readResourceBytes("lang_xml/eng-local.xml");
	}
	// ================================================================
	// ConfigService tests
	// ================================================================
	
	@Nested
	@DisplayName("ConfigService")
	class ConfigServiceTests {
		
		// --- in-memory parse via the regex ---
		
		/**
		 * Minimal stub that exposes the private loadConfigFile logic through
		 * a temp file copy, without needing the full UserService wiring.
		 */
		private Map<String, String> parseConfigBytes(byte[] cfgBytes) throws Exception {
			Path file = tmp.resolve("cfg.cfg");
			Files.write(file, cfgBytes);
			
			// Use reflection to call the private loadConfigFile
			// (Alternative: parse manually using the same regex pattern for a pure unit test.)
			// We replicate the pattern here to keep the test self-contained.
			Map<String, String> result = new LinkedHashMap<>();
			ConfigService.loadConfigFile(file, result);
			return result;
		}
		
		@Test
		@DisplayName("parse mod.cfg resource – at least one key present")
		void parseModCfg() throws Exception {
			Map<String, String> cfg = parseConfigBytes(modCfgBytes);
			assertFalse(cfg.isEmpty(), "mod.cfg should contain at least one key=value entry");
		}
		
		@Test
		@DisplayName("parse autoexec.cfg resource – at least one key present")
		void parseAutoexecCfg() throws Exception {
			Map<String, String> cfg = parseConfigBytes(autoexecCfgBytes);
			assertFalse(cfg.isEmpty(), "autoexec.cfg should contain at least one key=value entry");
		}
		
		@Test
		@DisplayName("comments and blank lines are stripped")
		void commentsStripped() throws Exception {
			final var content = """
				; top-level comment
				# hash comment
	
				key1 = value1
				; inline comment line
				key2 = value2 ; trailing comment
				""";
			Map<String, String> cfg = parseConfigBytes(content.getBytes(StandardCharsets.UTF_8));
			assertEquals(2, cfg.size());
			assertEquals("value1", cfg.get("key1"));
			assertEquals("value2", cfg.get("key2")); // trailing comment stripped by regex
		}
		
		@Test
		@DisplayName("mergeConfigs: source overwrites base")
		void mergeConfigs() {
			Map<String, String> base = new LinkedHashMap<>();
			base.put("a", "1");
			base.put("b", "2");
			Map<String, String> merge = Map.of("b", "99", "c", "3");
			ConfigService.mergeConfigs(base, merge);
			assertEquals("1", base.get("a"));
			assertEquals("99", base.get("b"));
			assertEquals("3", base.get("c"));
		}
		
		@Test
		@DisplayName("mergeConfigs: null merge is a no-op")
		void mergeConfigsNull() {
			Map<String, String> base = new LinkedHashMap<>(Map.of("x", "1"));
			ConfigService.mergeConfigs(base, null);
			assertEquals(1, base.size());
		}
		
		@Test
		@DisplayName("configToString formats entries as key = value")
		void configToString() {
			Map<String, String> cfg = new LinkedHashMap<>();
			cfg.put("foo", "bar");
			cfg.put("baz", "42");
			String out = ConfigService.configToString(cfg);
			assertTrue(out.contains("foo = bar"));
			assertTrue(out.contains("baz = 42"));
		}
		
		@Test
		@DisplayName("configToString returns empty string for null/empty map")
		void configToStringEmpty() {
			assertEquals("", ConfigService.configToString(null));
			assertEquals("", ConfigService.configToString(Collections.emptyMap()));
		}
		
		@Test
		@DisplayName("saveModConfig: null/blank guards return false")
		void saveModConfigGuards() {
			ModData mod = new ModData();
			mod.id = "test-mod";
			mod.setConfig(Map.of("k", "v"));
			
			assertFalse(ConfigService.saveModConfig(null, mod));
			assertFalse(ConfigService.saveModConfig("", mod));
			
			ModData noId = new ModData();
			noId.id = "";
			noId.setConfig(Map.of("k", "v"));
			assertFalse(ConfigService.saveModConfig(tmp.toString(), noId));
		}
		
		@Test
		@DisplayName("saveModConfig: empty config is a no-op (returns true)")
		void saveModConfigEmpty() {
			ModData mod = new ModData();
			mod.id = "test-mod";
			mod.setConfig(Collections.emptyMap());
			assertTrue(ConfigService.saveModConfig(tmp.toString(), mod));
		}
		
		@Test
		@DisplayName("saveModConfig writes and can be re-parsed in temp dir")
		void saveAndReadModConfig() throws Exception {
			ModData mod = new ModData();
			mod.id = "my-mod";
			mod.setConfig(new LinkedHashMap<>(Map.of("g_difficulty", "2", "sys_spec", "4")));
			
			assertTrue(ConfigService.saveModConfig(tmp.toString(), mod));
			
			Path written = tmp.resolve("mod.cfg");
			assertTrue(Files.exists(written));
			Map<String, String> readBack = parseConfigBytes(Files.readAllBytes(written));
			assertEquals("2", readBack.get("g_difficulty"));
			assertEquals("4", readBack.get("sys_spec"));
		}
		
		// --- full: write to resources output dir ---
		
		@Test
		@DisplayName("[full] save mod config derived from mod.cfg resource to resources/cfg/output/")
		void saveModConfigFull() throws Exception {
			Map<String, String> cfg = parseConfigBytes(modCfgBytes);
			
			ModData mod = new ModData();
			mod.id = "resource-mod";
			mod.setConfig(cfg);
			
			Path outDir = Paths.get("src/test/resources/cfg/output");
			Files.createDirectories(outDir);
			assertTrue(ConfigService.saveModConfig(outDir.toString(), mod));
			assertTrue(Files.exists(outDir.resolve("mod.cfg")));
		}
	}
	
}