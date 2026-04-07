package modforge.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonIO")
class JsonIOTests extends BaseServiceTest {
	
	@BeforeEach
	void init() throws IOException {
		loadCommonResources();
	}
	
	// ================================================================
	// JsonIO tests
	// ================================================================
	
	
	@Test
	@DisplayName("parse object from string")
	void parseObject() {
		String json = "{\"name\":\"sword\",\"damage\":42,\"enabled\":true,\"nothing\":null}";
		var v = JsonIO.JsonValue.parse(json);
		assertInstanceOf(JsonIO.JsonObject.class, v);
		var obj = (JsonIO.JsonObject) v;
		
		assertEquals("sword", ((JsonIO.JsonString) obj.get("name")).getValue());
		assertEquals(42, ((JsonIO.JsonNumber) obj.get("damage")).getValue().intValue());
		assertTrue(((JsonIO.JsonBoolean) obj.get("enabled")).getValue());
		assertInstanceOf(JsonIO.JsonNull.class, obj.get("nothing"));
	}
	
	@Test
	@DisplayName("parse array from string")
	void parseArray() {
		String json = "[1,\"two\",false]";
		var v = JsonIO.JsonValue.parse(json);
		assertInstanceOf(JsonIO.JsonArray.class, v);
		var arr = (JsonIO.JsonArray) v;
		assertEquals(3, arr.size());
		assertEquals(1, ((JsonIO.JsonNumber) arr.get(0)).getValue().intValue());
		assertEquals("two", ((JsonIO.JsonString) arr.get(1)).getValue());
		assertFalse(((JsonIO.JsonBoolean) arr.get(2)).getValue());
	}
	
	@Test
	@DisplayName("parse nested object")
	void parseNested() {
		String json = "{\"item\":{\"id\":\"loot_01\",\"tags\":[\"rare\",\"sword\"]}}";
		var root = (JsonIO.JsonObject) JsonIO.JsonValue.parse(json);
		var item = (JsonIO.JsonObject) root.get("item");
		assertNotNull(item);
		assertEquals("loot_01", ((JsonIO.JsonString) item.get("id")).getValue());
		var tags = (JsonIO.JsonArray) item.get("tags");
		assertEquals(2, tags.size());
	}
	
	@Test
	@DisplayName("roundtrip: build → serialize → parse")
	void roundtrip() {
		var obj = new JsonIO.JsonObject();
		obj.put("key", new JsonIO.JsonString("value with \"quotes\""));
		obj.put("num", new JsonIO.JsonNumber(3.14));
		obj.put("flag", new JsonIO.JsonBoolean(false));
		
		String serialized = obj.toJsonString();
		var parsed = (JsonIO.JsonObject) JsonIO.JsonValue.parse(serialized);
		
		assertEquals("value with \"quotes\"", ((JsonIO.JsonString) parsed.get("key")).getValue());
		assertEquals(3.14, ((JsonIO.JsonNumber) parsed.get("num")).getValue().doubleValue(), 1e-10);
		assertFalse(((JsonIO.JsonBoolean) parsed.get("flag")).getValue());
	}
	
	@Test
	@DisplayName("parse userconfig.json resource")
	void parseUserConfigResource() {
		String json = new String(userConfigJsonBytes, StandardCharsets.UTF_8);
		var v = JsonIO.JsonValue.parse(json);
		assertNotNull(v);
	}
	
	@Test
	@DisplayName("write single JsonObject to temp file and read back")
	void writeSingleObject() {
		var obj = new JsonIO.JsonObject();
		obj.put("modId", new JsonIO.JsonString("my-mod"));
		obj.put("version", new JsonIO.JsonNumber(1));
		
		var out = tmp.resolve("single.json");
		assertTrue(JsonIO.write(out, obj));
		assertTrue(Files.exists(out));
		
		var read = JsonIO.read(out);
		assertInstanceOf(JsonIO.JsonObject.class, read);
		assertEquals("my-mod", ((JsonIO.JsonString) ((JsonIO.JsonObject) read).get("modId")).getValue());
	}
	
	@Test
	@DisplayName("write list of JsonObjects to temp file and read back")
	void writeList() {
		List<JsonIO.JsonObject> list = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			var o = new JsonIO.JsonObject();
			o.put("i", new JsonIO.JsonNumber(i));
			list.add(o);
		}
		
		var out = tmp.resolve("list.json");
		assertTrue(JsonIO.write(out, list));
		
		var read = JsonIO.read(out);
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
		var v = JsonIO.read(tmp.resolve("does-not-exist.json"));
		assertNull(v);
	}
	
	@Test
	@DisplayName("[full] write userconfig copy to out folder")
	void writeUserConfigFull() throws IOException {
		String json = new String(userConfigJsonBytes, StandardCharsets.UTF_8);
		var parsed = JsonIO.JsonValue.parse(json);
		assertInstanceOf(JsonIO.JsonObject.class, parsed);
		
		var outDir = RESOURCES_OUTPUT;
		Files.createDirectories(outDir);
		var outFile = outDir.resolve("userconfig-copy.json");
		assertTrue(JsonIO.write(outFile, (JsonIO.JsonObject) parsed));
		assertTrue(Files.exists(outFile));
	}
}