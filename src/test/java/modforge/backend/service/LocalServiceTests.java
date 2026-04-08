package modforge.backend.service;

import modforge.backend.ModData;
import modforge.backend.model.E.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LocalService localization")
class LocalServiceTests extends BaseServiceTest {
	
	@BeforeEach
	void init() throws IOException {
		loadCommonResources();
	}
	
	// ================================================================
	// LocalService tests
	// ================================================================
	
	
	private LocalService createLocalService(String gameDir) {
		return new LocalService(createStubUserService(gameDir));
	}
	
	@Test
	@DisplayName("parseLocalizationXml: in-memory minimal table")
	void parseMinimalTable() throws Exception {
		String xml = """
				<Table>
				  <Row><Cell>ui_sword</Cell><Cell/><Cell>Iron Sword</Cell></Row>
				  <Row><Cell>ui_shield</Cell><Cell/><Cell>Wooden Shield</Cell></Row>
				</Table>
				""";
		var result = LocalService.parseLocalizationXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
		assertEquals("Iron Sword", result.get("ui_sword"));
		assertEquals("Wooden Shield", result.get("ui_shield"));
	}
	
	@Test
	@DisplayName("parseLocalizationXml: empty table returns empty map")
	void parseEmptyTable() throws Exception {
		String xml = "<Table></Table>";
		var result = LocalService.parseLocalizationXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
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
		var result = LocalService.parseLocalizationXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
		assertFalse(result.containsKey(""));
		assertEquals("GoodValue", result.get("ui_good"));
	}
	
	@Test
	@DisplayName("parseLocalizationXml: parses eng-local.xml resource")
	void parseEngLocalResource() throws Exception {
		var result = LocalService.parseLocalizationXml(new ByteArrayInputStream(engLocalXmlBytes));
		assertFalse(result.isEmpty(), "eng-local.xml should contain localization entries");
	}
	
	@Test
	@DisplayName("makeLocalizationXml: roundtrip through parse")
	void makeAndReparseLocalizationXml() throws Exception {
		Map<String, String> entries = new LinkedHashMap<>();
		entries.put("ui_sword", "Iron Sword");
		entries.put("ui_potion", "Health <Potion> & Co");
		
		String xml = LocalService.makeLocalizationXml(entries);
		
		var reparsed = LocalService.parseLocalizationXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
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
		
		String xml = LocalService.makeLocalizationXml(entries);
		
		var reparsed = LocalService.parseLocalizationXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
		assertEquals(1, reparsed.size());
		assertEquals("Present", reparsed.get("ui_ok"));
	}
	
	@Test
	@DisplayName("loadLocalization: returns empty map when game dir is null")
	void loadLocalizationNullDir() {
		var result = LocalService.loadLocalization(null);
		assertTrue(result.isEmpty());
	}
	
	@Test
	@DisplayName("loadLocalization: loads English_xml.pak resource")
	void loadLocalizationFromPak() throws IOException {
		var locDir = tmp.resolve("Localization");
		Files.createDirectories(locDir);
		Files.write(locDir.resolve("English_xml.pak"), englishPakBytes);
		
		var result = LocalService.loadLocalization(tmp.toString());
		assertFalse(result.isEmpty(), "Should have loaded at least one language");
		assertTrue(result.containsKey(Language.ENGLISH));
		assertFalse(result.get(Language.ENGLISH).isEmpty());
	}
	
	@Test
	@DisplayName("resolve: mod-local string takes precedence over base game")
	void resolveModOverridesBase() {
		var ls = createLocalService(tmp.toString());
		
		var mod = new ModData();
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
		var ls = createLocalService(tmp.toString());
		var mod = new ModData();
		mod.id = "test-mod";
		mod.setLocal(Collections.emptyMap());
		assertNull(ls.resolve("definitely_not_a_key_xyz", mod, Language.ENGLISH));
	}
	
	@Test
	@DisplayName("writeModLocalization: writes per-language files to temp dir")
	void writeModLocalizationTemp() {
		Map<Language, Map<String, String>> local = new EnumMap<>(Language.class);
		local.put(Language.ENGLISH, new LinkedHashMap<>(Map.of("ui_item", "My Item")));
		
		var mod = new ModData();
		mod.id = "loc-mod";
		mod.setLocal(local);
		
		assertDoesNotThrow(() -> LocalService.writeModLocalization(mod, tmp.toString()));
	}
	
	@Test
	@DisplayName("[full] parse English_xml.pak and write to out folder")
	void writeLocalizationFull() throws IOException {
		var locDir = tmp.resolve("Localization");
		Files.createDirectories(locDir);
		Files.write(locDir.resolve("English_xml.pak"), englishPakBytes);
		
		var result = LocalService.loadLocalization(tmp.toString());
		assertFalse(result.isEmpty());
		
		var outBase = RESOURCES_OUTPUT;
		Files.createDirectories(outBase);
		
		var mod = new ModData();
		mod.id = "eng-output-mod";
		mod.setLocal(result);
		
		assertDoesNotThrow(() -> LocalService.writeModLocalization(mod, String.valueOf(outBase)));
	}
}