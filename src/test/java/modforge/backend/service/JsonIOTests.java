package modforge.backend.service;

import modforge.Util;
import org.junit.jupiter.api.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
class JsonIOTests {
	
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
	// JsonIO tests
	// ================================================================
	
	@Nested
	@DisplayName("JsonIO")
	class JsonIOTest {
		
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
		void writeSingleObject() {
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
		void writeList() {
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
}