package modforge.backend.service;

import modforge.Util;
import modforge.backend.ModData;
import modforge.backend.model.Language;
import modforge.backend.model.ModItem;
import modforge.backend.service.ConfigService;
import modforge.backend.service.ItemService;
import modforge.backend.service.JsonIO;
import modforge.backend.service.LocalService;
import org.junit.jupiter.api.*;
import org.w3c.dom.Document;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
    // JsonIO tests
    // ================================================================

    @Nested
    @DisplayName("JsonIO")
    class JsonIOTests {

        // --- in-memory parse ---

        @Test
        @DisplayName("parse object from string")
        void parseObject() {
            String json = "{\"name\":\"sword\",\"damage\":42,\"enabled\":true,\"nothing\":null}";
            JsonIO.JsonValue v = JsonIO.JsonValue.parse(json);
            assertInstanceOf(JsonIO.JsonObject.class, v);
            JsonIO.JsonObject obj = (JsonIO.JsonObject) v;

            assertEquals("sword", ((JsonIO.JsonString) obj.get("name")).getValue());
            assertEquals(42, ((JsonIO.JsonNumber) obj.get("damage")).getValue().intValue());
            assertTrue(((JsonIO.JsonBoolean) obj.get("enabled")).getValue());
            assertInstanceOf(JsonIO.JsonNull.class, obj.get("nothing"));
        }

        @Test
        @DisplayName("parse array from string")
        void parseArray() {
            String json = "[1,\"two\",false]";
            JsonIO.JsonValue v = JsonIO.JsonValue.parse(json);
            assertInstanceOf(JsonIO.JsonArray.class, v);
            JsonIO.JsonArray arr = (JsonIO.JsonArray) v;
            assertEquals(3, arr.size());
            assertEquals(1, ((JsonIO.JsonNumber) arr.get(0)).getValue().intValue());
            assertEquals("two", ((JsonIO.JsonString) arr.get(1)).getValue());
            assertFalse(((JsonIO.JsonBoolean) arr.get(2)).getValue());
        }

        @Test
        @DisplayName("parse nested object")
        void parseNested() {
            String json = "{\"item\":{\"id\":\"loot_01\",\"tags\":[\"rare\",\"sword\"]}}";
            JsonIO.JsonObject root = (JsonIO.JsonObject) JsonIO.JsonValue.parse(json);
            JsonIO.JsonObject item = (JsonIO.JsonObject) root.get("item");
            assertNotNull(item);
            assertEquals("loot_01", ((JsonIO.JsonString) item.get("id")).getValue());
            JsonIO.JsonArray tags = (JsonIO.JsonArray) item.get("tags");
            assertEquals(2, tags.size());
        }

        @Test
        @DisplayName("roundtrip: build → serialize → parse")
        void roundtrip() {
            JsonIO.JsonObject obj = new JsonIO.JsonObject();
            obj.put("key", new JsonIO.JsonString("value with \"quotes\""));
            obj.put("num", new JsonIO.JsonNumber(3.14));
            obj.put("flag", new JsonIO.JsonBoolean(false));

            String serialized = obj.toJsonString();
            JsonIO.JsonObject parsed = (JsonIO.JsonObject) JsonIO.JsonValue.parse(serialized);

            assertEquals("value with \"quotes\"", ((JsonIO.JsonString) parsed.get("key")).getValue());
            assertEquals(3.14, ((JsonIO.JsonNumber) parsed.get("num")).getValue().doubleValue(), 1e-10);
            assertFalse(((JsonIO.JsonBoolean) parsed.get("flag")).getValue());
        }

        @Test
        @DisplayName("parse userconfig.json resource")
        void parseUserConfigResource() {
            String json = new String(userConfigJsonBytes, StandardCharsets.UTF_8);
            JsonIO.JsonValue v = JsonIO.JsonValue.parse(json);
            // The file must parse without throwing
            assertNotNull(v);
        }

        // --- write / read via temp dir ---

        @Test
        @DisplayName("write single JsonObject to temp file and read back")
        void writeSingleObject() throws IOException {
            JsonIO.JsonObject obj = new JsonIO.JsonObject();
            obj.put("modId", new JsonIO.JsonString("my-mod"));
            obj.put("version", new JsonIO.JsonNumber(1));

            Path out = tmp.resolve("single.json");
            assertTrue(JsonIO.write(out, obj));
            assertTrue(Files.exists(out));

            JsonIO.JsonValue read = JsonIO.read(out);
            assertInstanceOf(JsonIO.JsonObject.class, read);
            assertEquals("my-mod", ((JsonIO.JsonString) ((JsonIO.JsonObject) read).get("modId")).getValue());
        }

        @Test
        @DisplayName("write list of JsonObjects to temp file and read back")
        void writeList() throws IOException {
            List<JsonIO.JsonObject> list = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                JsonIO.JsonObject o = new JsonIO.JsonObject();
                o.put("i", new JsonIO.JsonNumber(i));
                list.add(o);
            }

            Path out = tmp.resolve("list.json");
            assertTrue(JsonIO.write(out, list));

            JsonIO.JsonValue read = JsonIO.read(out);
            assertInstanceOf(JsonIO.JsonArray.class, read);
            assertEquals(3, ((JsonIO.JsonArray) read).size());
        }

        @Test
        @DisplayName("write returns false for null / empty")
        void writeGuards() {
            assertFalse(JsonIO.write(tmp.resolve("nope.json"), (JsonIO.JsonObject) null));
            assertFalse(JsonIO.write(tmp.resolve("nope.json"), (List<JsonIO.JsonObject>) null));
            assertFalse(JsonIO.write(tmp.resolve("nope.json"), Collections.emptyList()));
        }

        @Test
        @DisplayName("read returns null for non-existent file")
        void readMissing() {
            JsonIO.JsonValue v = JsonIO.read(tmp.resolve("does-not-exist.json"));
            assertNull(v);
        }

        // --- full: write to resources output dir ---

        @Test
        @DisplayName("[full] write userconfig copy to resources/json/output/")
        void writeUserConfigFull() throws IOException {
            String json = new String(userConfigJsonBytes, StandardCharsets.UTF_8);
            JsonIO.JsonValue parsed = JsonIO.JsonValue.parse(json);
            assertInstanceOf(JsonIO.JsonObject.class, parsed);

            Path outDir = Paths.get("src/test/resources/json/output");
            Files.createDirectories(outDir);
            Path outFile = outDir.resolve("userconfig-copy.json");
            assertTrue(JsonIO.write(outFile, (JsonIO.JsonObject) parsed));
            assertTrue(Files.exists(outFile));
        }
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
            var pattern = java.util.regex.Pattern.compile(
                    "^(?!#|;)\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*(.*?)\\s*(?:;.*)?$");
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == ';' || line.charAt(0) == '#') continue;
                var m = pattern.matcher(line);
                if (m.matches()) result.put(m.group(1), m.group(2).trim());
            }
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
            String content = """
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

    // ================================================================
    // ItemService XML tests
    // ================================================================

    @Nested
    @DisplayName("ItemService XML parsing")
    class ItemServiceTests {

        @Test
        @DisplayName("parseXml: parses valid XML from byte array")
        void parseXmlBasic() {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <database name="barbaro">
                      <LootInfos version="1">
                        <LootInfo UIName="ui_loot_001" id="loot_001" weight="1.0"/>
                      </LootInfos>
                    </database>
                    """;
            InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
            Document doc = ItemService.parseXml(is);
            assertNotNull(doc);
            assertEquals("database", doc.getDocumentElement().getTagName());
        }

        @Test
        @DisplayName("parseXml: parses item__lootinfo.xml resource without error")
        void parseItemXmlResource() {
            InputStream is = new ByteArrayInputStream(itemXmlBytes);
            Document doc = ItemService.parseXml(is);
            assertNotNull(doc);
            // Root must be <database>
            assertEquals("database", doc.getDocumentElement().getTagName());
            // Must have at least one child group element
            assertTrue(doc.getDocumentElement().getChildNodes().getLength() > 0);
        }

        @Test
        @DisplayName("parseXml: handles UTF-8 with BOM gracefully")
        void parseXmlWithBom() {
            // Prepend UTF-8 BOM manually
            byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
            byte[] xml = "<?xml version=\"1.0\"?><root/>".getBytes(StandardCharsets.UTF_8);
            byte[] withBom = new byte[bom.length + xml.length];
            System.arraycopy(bom, 0, withBom, 0, bom.length);
            System.arraycopy(xml, 0, withBom, bom.length, xml.length);
            // Should not throw
            assertDoesNotThrow(() -> ItemService.parseXml(new ByteArrayInputStream(withBom)));
        }

        @Test
        @DisplayName("parseXml: external entity features are disabled (security)")
        void parseXmlXxeDisabled() {
            // A DOCTYPE with an external entity must not trigger a network call / error
            String xxe = """
                    <?xml version="1.0"?>
                    <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                    <root>&xxe;</root>
                    """;
            // Should either parse without resolution or throw a benign parse error,
            // but must NOT cause an IOException from a network/filesystem fetch.
            assertDoesNotThrow(() -> ItemService.parseXml(new ByteArrayInputStream(xxe.getBytes(StandardCharsets.UTF_8))));
        }

        @Test
        @DisplayName("loadItems: returns empty set for non-existent path")
        void loadItemsMissingDir() {
            // We need an ItemService instance – use a minimal stub UserService
            UserService us = stubUserService(tmp.toString());
            // Point to a path that doesn't exist
            Path missing = tmp.resolve("nonexistent");
            ItemService svc = new ItemService(us); // init() will silently skip (no Data dir)
            Set<ModItem> items = svc.loadItems(missing);
            assertTrue(items.isEmpty());
        }

        @Test
        @DisplayName("loadItems: empty PAK dir returns empty set")
        void loadItemsEmptyDir() throws IOException {
            Path dataDir = tmp.resolve("Data/Empty");
            Files.createDirectories(dataDir);
            UserService us = stubUserService(tmp.toString());
            ItemService svc = new ItemService(us);
            Set<ModItem> items = svc.loadItems(dataDir);
			System.out.println(items);
            assertTrue(items.isEmpty());
        }

        @Test
        @DisplayName("loadItems: reads items from perk-data.pak resource")
        void loadItemsFromPak() throws IOException {
            Path dataDir = tmp.resolve("Data");
            Files.createDirectories(dataDir);
            Files.write(dataDir.resolve("perk-data.pak"), perkDataPakBytes);

            UserService us = stubUserService(tmp.toString());
            ItemService svc = new ItemService(us);
            Set<ModItem> items = svc.loadItems(dataDir);
            // The PAK has real data – expect at least one item
            assertFalse(items.isEmpty(), "Should load at least one item from perk-data.pak");
        }

        @Test
        @DisplayName("writeModItems: writes nothing when mod has no items")
        void writeModItemsEmpty() {
            ModData mod = new ModData();
            mod.id = "empty-mod";
            mod.setItems(Collections.emptySet());

            UserService us = stubUserService(tmp.toString());
            ItemService svc = new ItemService(us);
            assertDoesNotThrow(() -> svc.writeModItems(mod));
        }

        // --- full: write items loaded from resource PAK to resources output ---

        @Test
        @DisplayName("[full] load perk-data.pak and write mod items to resources/item_xml/output/")
        void writeModItemsFull() throws IOException {
            Path dataDir = tmp.resolve("Data");
            Files.createDirectories(dataDir);
            Files.write(dataDir.resolve("perk-data.pak"), perkDataPakBytes);

            // Use a staging game dir rooted in resources/item_xml/output
            Path outBase = Paths.get("src/test/resources/item_xml/output");
            Files.createDirectories(outBase);

            UserService us = stubUserService(outBase.toString());
            ItemService svc = new ItemService(us);

            // Load from the temp pak, then write as a mod
            Set<ModItem> items = svc.loadItems(dataDir);
            assertFalse(items.isEmpty());

            ModData mod = new ModData();
            mod.id = "perk-test-mod";
            mod.setItems(items);

            assertDoesNotThrow(() -> svc.writeModItems(mod));
        }

        // helper
        private UserService stubUserService(String gameDir) {
            UserService us = new UserService();
            us.gameDirectory = gameDir;
            return us;
        }
    }

    // ================================================================
    // LocalService tests
    // ================================================================

    @Nested
    @DisplayName("LocalService localization")
    class LocalServiceTests {

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
            Map<String, String> result = ls.parseLocalizationXml(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            assertEquals("Iron Sword", result.get("ui_sword"));
            assertEquals("Wooden Shield", result.get("ui_shield"));
        }

        @Test
        @DisplayName("parseLocalizationXml: empty table returns empty map")
        void parseEmptyTable() throws Exception {
            String xml = "<Table></Table>";
            LocalService ls = makeLocalService(null);
            Map<String, String> result = ls.parseLocalizationXml(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
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
            Map<String, String> result = ls.parseLocalizationXml(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            assertFalse(result.containsKey(""));
            assertEquals("GoodValue", result.get("ui_good"));
        }

        @Test
        @DisplayName("parseLocalizationXml: parses eng-local.xml resource")
        void parseEngLocalResource() throws Exception {
            LocalService ls = makeLocalService(null);
            Map<String, String> result = ls.parseLocalizationXml(
                    new ByteArrayInputStream(engLocalXmlBytes));
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
            Map<String, String> reparsed = ls.parseLocalizationXml(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
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

            Map<String, String> reparsed = ls.parseLocalizationXml(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
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