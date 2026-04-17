package com.nukuhack.modforge.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JsonIOTest extends BaseServiceTest {

    @TempDir
    Path tempDir;

    // ==================== Basic Parsing Tests ====================

    @BeforeEach
    void init() throws IOException {
        loadCommonResources();
    }

    @DisplayName("JsonIO")
    @Nested
    class JsonIOTests extends BaseServiceTest {

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
        @DisplayName("write returns for null / empty")
        void writeGuards() {
            assertFalse(JsonIO.write(tmp.resolve("nope.json"), (JsonIO.JsonObject) null));
            assertTrue(JsonIO.write(tmp.resolve("nope.json"), (List<JsonIO.JsonObject>) null));
            assertTrue(JsonIO.write(tmp.resolve("nope.json"), Collections.emptyList()));
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
    
    @Nested
    @DisplayName("Basic Parsing")
    class BasicParsing {
        
        @Test
        @DisplayName("Parse simple object")
        void parseSimpleObject() {
            String json = "{\"name\":\"John\",\"age\":30,\"active\":true,\"score\":95.5}";
            
            JsonIO.JsonValue result = JsonIO.JsonValue.parse(json);
            
            assertTrue(result.isObject());
            JsonIO.JsonObject obj = (JsonIO.JsonObject) result;
            assertEquals("John", obj.getString("name").orElse(null));
            assertEquals(30, obj.getInt("age").orElse(0));
            assertTrue(obj.getBoolean("active").orElse(false));
            assertEquals(95.5, obj.getDouble("score").orElse(0.0));
        }
        
        @Test
        @DisplayName("Parse simple array")
        void parseSimpleArray() {
            String json = "[\"apple\",\"banana\",\"cherry\"]";
            
            JsonIO.JsonValue result = JsonIO.JsonValue.parse(json);
            
            assertTrue(result.isArray());
            JsonIO.JsonArray arr = (JsonIO.JsonArray) result;
            assertEquals(3, arr.size());
            assertEquals("apple", ((JsonIO.JsonString)arr.get(0)).getValue());
            assertEquals("banana", ((JsonIO.JsonString)arr.get(1)).getValue());
            assertEquals("cherry", ((JsonIO.JsonString)arr.get(2)).getValue());
        }
        
        @Test
        @DisplayName("Parse null literal")
        void parseNull() {
            String json = "null";
            
            JsonIO.JsonValue result = JsonIO.JsonValue.parse(json);
            
            assertTrue(result.isNull());
        }
        
        @Test
        @DisplayName("Parse boolean literals")
        void parseBooleans() {
            JsonIO.JsonValue trueVal = JsonIO.JsonValue.parse("true");
            JsonIO.JsonValue falseVal = JsonIO.JsonValue.parse("false");
            
            assertTrue(trueVal.isBoolean());
            assertTrue(((JsonIO.JsonBoolean)trueVal).getValue());
            assertTrue(falseVal.isBoolean());
            assertFalse(((JsonIO.JsonBoolean)falseVal).getValue());
        }
        
        @Test
        @DisplayName("Parse various number formats")
        void parseNumbers() {
            assertAll(
                () -> assertEquals(42, ((JsonIO.JsonNumber)JsonIO.JsonValue.parse("42")).intValue()),
                () -> assertEquals(-42, ((JsonIO.JsonNumber)JsonIO.JsonValue.parse("-42")).intValue()),
                () -> assertEquals(3.14, ((JsonIO.JsonNumber)JsonIO.JsonValue.parse("3.14")).doubleValue(), 0.001),
                () -> assertEquals(1e10, ((JsonIO.JsonNumber)JsonIO.JsonValue.parse("1e10")).doubleValue(), 0.001),
                () -> assertEquals(0.001, ((JsonIO.JsonNumber)JsonIO.JsonValue.parse("1e-3")).doubleValue(), 0.0001),
                () -> assertEquals(9223372036854775807L, 
                    ((JsonIO.JsonNumber)JsonIO.JsonValue.parse("9223372036854775807")).longValue())
            );
        }
        
        @Test
        @DisplayName("Parse nested structures")
        void parseNestedStructures() {
            String json = """
                {
                    "user": {
                        "name": "Alice",
                        "address": {
                            "street": "123 Main St",
                            "city": "Springfield",
                            "zip": 12345
                        },
                        "hobbies": ["reading", "gaming", "coding"]
                    },
                    "metadata": {
                        "created": "2024-01-01",
                        "active": true
                    }
                }
                """;
            
            JsonIO.JsonValue result = JsonIO.JsonValue.parse(json);
            JsonIO.JsonObject root = (JsonIO.JsonObject) result;
            
            JsonIO.JsonObject user = root.getObject("user").orElseThrow();
            assertEquals("Alice", user.getString("name").orElse(null));
            
            JsonIO.JsonObject address = user.getObject("address").orElseThrow();
            assertEquals("Springfield", address.getString("city").orElse(null));
            assertEquals(12345, address.getInt("zip").orElse(0));
            
            JsonIO.JsonArray hobbies = user.getArray("hobbies").orElseThrow();
            assertEquals(3, hobbies.size());
            assertEquals("reading", ((JsonIO.JsonString)hobbies.get(0)).getValue());
        }
    }
    
    // ==================== String Escape Tests ====================
    
    @Nested
    @DisplayName("String Escaping")
    class StringEscaping {
        
        @Test
        @DisplayName("Escape special characters")
        void escapeSpecialCharacters() {
            String json = "{\"text\":\"Line1\\nLine2\\tTabbed\\\"Quote\\\"\\r\\b\\f\"}";

            JsonIO.JsonObject obj = (JsonIO.JsonObject) JsonIO.JsonValue.parse(json);
            String value = obj.getString("text").orElseThrow();
            
            assertTrue(value.contains("\n"));
            assertTrue(value.contains("\t"));
            assertTrue(value.contains("\""));
            assertTrue(value.contains("\r"));
            assertTrue(value.contains("\b"));
            assertTrue(value.contains("\f"));
        }
        
        @Test
        @DisplayName("Parse Unicode escapes")
        void parseUnicodeEscapes() {
            String json = "{\"emoji\":\"\\uD83D\\uDE00 Hello \\u4E16\\u754C\"}";
            
            JsonIO.JsonObject obj = (JsonIO.JsonObject) JsonIO.JsonValue.parse(json);
            String value = obj.getString("emoji").orElseThrow();
            
            assertTrue(value.contains("😀"));
            assertTrue(value.contains("世界"));
        }
        
        @Test
        @DisplayName("Round-trip escaped strings")
        void roundTripEscapedStrings() {
            String original = "Complex\\string\\with\\\"quotes\\\"\\nand\\ttabs\\r\\n\\u0041";
            
            JsonIO.JsonObject obj = new JsonIO.JsonObject();
            obj.put("escaped", original);
            
            String serialized = obj.toJsonString();
            JsonIO.JsonObject parsed = (JsonIO.JsonObject) JsonIO.JsonValue.parse(serialized);
            
            assertEquals(original, parsed.getString("escaped").orElseThrow());
        }
    }
    
    // ==================== Edge Cases and Error Handling ====================
    
    @Nested
    @DisplayName("Edge Cases and Errors")
    class EdgeCases {
        
        @Test
        @DisplayName("Empty object")
        void emptyObject() {
            JsonIO.JsonValue result = JsonIO.JsonValue.parse("{}");
            
            assertTrue(result.isObject());
            JsonIO.JsonObject obj = (JsonIO.JsonObject) result;
            assertTrue(obj.entrySet().isEmpty());
        }
        
        @Test
        @DisplayName("Empty array")
        void emptyArray() {
            JsonIO.JsonValue result = JsonIO.JsonValue.parse("[]");
            
            assertTrue(result.isArray());
            JsonIO.JsonArray arr = (JsonIO.JsonArray) result;
            assertEquals(0, arr.size());
            assertTrue(arr.isEmpty());
        }
        
        @Test
        @DisplayName("Whitespace handling")
        void whitespaceHandling() {
            String json = "  \n\t  {  \n  \"key\"  :  \t  \"value\"  \n  }  \t  ";
            
            JsonIO.JsonObject obj = (JsonIO.JsonObject) JsonIO.JsonValue.parse(json);
            
            assertEquals("value", obj.getString("key").orElseThrow());
        }
        
        @Test
        @DisplayName("Missing optional values")
        void missingOptionalValues() {
            JsonIO.JsonObject obj = new JsonIO.JsonObject();
            
            assertTrue(obj.getString("nonexistent").isEmpty());
            assertTrue(obj.getInt("nonexistent").isEmpty());
            assertTrue(obj.getBoolean("nonexistent").isEmpty());
            assertTrue(obj.getObject("nonexistent").isEmpty());
            assertTrue(obj.getArray("nonexistent").isEmpty());
        }
        
        @Test
        @DisplayName("Deep nesting")
        void deepNesting() {
            StringBuilder json = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                json.append("{\"level").append(i).append("\":");
            }
            json.append("\"value\"");
            for (int i = 0; i < 100; i++) {
                json.append("}");
            }
            
            JsonIO.JsonValue result = JsonIO.JsonValue.parse(json.toString());
            assertTrue(result.isObject());
            
            // Navigate through 100 levels
            JsonIO.JsonValue current = result;
            for (int i = 0; i < 100; i++) {
                assertTrue(current.isObject());
                JsonIO.JsonObject obj = (JsonIO.JsonObject) current;
                current = obj.get("level" + i);
            }
            assertTrue(current.isString());
            assertEquals("value", ((JsonIO.JsonString)current).getValue());
        }
        
        @Test
        @DisplayName("Invalid JSON throws exception")
        void invalidJsonThrowsException() {
            String[] invalidJsons = {
                "{", 
                "[", 
                "{\"key\":}", 
                "{\"key\":value}", 
                "{\"key\":'value'}", 
                "{\"key\":1,}", 
                "[1,2,]", 
                "{\"key\":1 2}", 
                "{\"key\":}", 
                "tru", 
                "fals", 
                "nul"
            };
            
            for (String invalid : invalidJsons) {
                assertThrows(Exception.class, () -> JsonIO.JsonValue.parse(invalid),
                    "Should throw exception for: " + invalid);
            }
        }
        
        @Test
        @DisplayName("Trailing content handled gracefully")
        void trailingContentHandled() {
            // This shouldn't throw, but will log a warning
            assertDoesNotThrow(() -> {
                JsonIO.JsonValue.parse("{\"key\":\"value\"} extra content");
            });
        }
    }
    
    // ==================== Builder Pattern Tests ====================
    
    @Nested
    @DisplayName("Builder Pattern")
    class BuilderPattern {
        
        @Test
        @DisplayName("Fluent object building")
        void fluentObjectBuilding() {
            JsonIO.JsonObject obj = new JsonIO.JsonObject()
                .with("string", "value")
                .with("number", 42)
                .with("boolean", true)
                .with("nullValue", JsonIO.JsonNull.INSTANCE)
                .with("nested", new JsonIO.JsonObject()
                    .with("inner", "nestedValue"));
            
            assertEquals("value", obj.getString("string").orElseThrow());
            assertEquals(42, obj.getInt("number").orElseThrow());
            assertTrue(obj.getBoolean("boolean").orElseThrow());
            assertTrue(obj.get("nullValue").isNull());
            
            JsonIO.JsonObject nested = obj.getObject("nested").orElseThrow();
            assertEquals("nestedValue", nested.getString("inner").orElseThrow());
        }
        
        @Test
        @DisplayName("Fluent array building")
        void fluentArrayBuilding() {
            JsonIO.JsonArray arr = new JsonIO.JsonArray();
            arr.add("first");
            arr.add(42);
            arr.add(true);
            arr.add(new JsonIO.JsonObject().with("key", "value"));
            
            assertEquals(4, arr.size());
            assertEquals("first", ((JsonIO.JsonString)arr.get(0)).getValue());
            assertEquals(42, ((JsonIO.JsonNumber)arr.get(1)).intValue());
            assertTrue(((JsonIO.JsonBoolean)arr.get(2)).getValue());
            assertTrue(arr.get(3).isObject());
        }
    }
    
    // ==================== File I/O Tests ====================
    
    @Nested
    @DisplayName("File I/O Operations")
    class FileIO {
        
        @Test
        @DisplayName("Write and read object to file")
        void writeAndReadObject() throws IOException {
            Path file = tempDir.resolve("test.json");
            
            JsonIO.JsonObject original = new JsonIO.JsonObject()
                .with("name", "Test User")
                .with("age", 25)
                .with("active", true);
            
            assertTrue(JsonIO.write(file, original));
            assertTrue(Files.exists(file));
            
            JsonIO.JsonValue read = JsonIO.read(file);
            assertTrue(read.isObject());
            
            JsonIO.JsonObject obj = (JsonIO.JsonObject) read;
            assertEquals("Test User", obj.getString("name").orElseThrow());
            assertEquals(25, obj.getInt("age").orElseThrow());
            assertTrue(obj.getBoolean("active").orElseThrow());
        }
        
        @Test
        @DisplayName("Write and read array to file")
        void writeAndReadArray() throws IOException {
            Path file = tempDir.resolve("array.json");
            
            JsonIO.JsonArray original = new JsonIO.JsonArray();
            original.add("item1");
            original.add(42);
            original.add(false);
            
            assertTrue(JsonIO.write(file, original));
            
            JsonIO.JsonValue read = JsonIO.read(file);
            assertTrue(read.isArray());
            
            JsonIO.JsonArray arr = (JsonIO.JsonArray) read;
            assertEquals(3, arr.size());
            assertEquals("item1", ((JsonIO.JsonString)arr.get(0)).getValue());
        }
        
        @Test
        @DisplayName("Write list of objects convenience method")
        void writeListOfObjects() throws IOException {
            Path file = tempDir.resolve("list.json");
            
            List<JsonIO.JsonObject> items = Arrays.asList(
                new JsonIO.JsonObject().with("id", 1).with("name", "First"),
                new JsonIO.JsonObject().with("id", 2).with("name", "Second"),
                new JsonIO.JsonObject().with("id", 3).with("name", "Third")
            );
            
            assertTrue(JsonIO.write(file, items));
            
            JsonIO.JsonValue read = JsonIO.read(file);
            assertTrue(read.isArray());
            
            JsonIO.JsonArray arr = (JsonIO.JsonArray) read;
            assertEquals(3, arr.size());
            
            JsonIO.JsonObject first = (JsonIO.JsonObject) arr.get(0);
            assertEquals(1, first.getInt("id").orElseThrow());
            assertEquals("First", first.getString("name").orElseThrow());
        }
        
        @Test
        @DisplayName("Write empty list")
        void writeEmptyList() throws IOException {
            Path file = tempDir.resolve("empty.json");
            
            assertTrue(JsonIO.write(file, Collections.emptyList()));
            
            String content = Files.readString(file);
            assertEquals("[]", content);
        }
        
        @Test
        @DisplayName("Write creates parent directories")
        void writeCreatesParentDirectories() {
            Path file = tempDir.resolve("deep/nested/dir/test.json");
            
            JsonIO.JsonObject obj = new JsonIO.JsonObject().with("test", "value");
            
            assertTrue(JsonIO.write(file, obj));
            assertTrue(Files.exists(file));
        }
        
        @Test
        @DisplayName("Read non-existent file returns null")
        void readNonExistentFile() {
            Path file = tempDir.resolve("nonexistent.json");
            
            JsonIO.JsonValue result = JsonIO.read(file);
            assertNull(result);
        }
        
        @Test
        @DisplayName("Write null value returns false")
        void writeNullValue() {
            Path file = tempDir.resolve("null.json");
            
            assertFalse(JsonIO.write(file, (JsonIO.JsonValue) null));
        }
        
        @Test
        @DisplayName("Handle UTF-8 BOM")
        void handleUtf8Bom() throws IOException {
            Path file = tempDir.resolve("bom.json");
            
            // Write file with UTF-8 BOM
            byte[] content = "{\"key\":\"value\"}".getBytes(StandardCharsets.UTF_8);
            byte[] withBom = new byte[content.length + 3];
            withBom[0] = (byte) 0xEF;
            withBom[1] = (byte) 0xBB;
            withBom[2] = (byte) 0xBF;
            System.arraycopy(content, 0, withBom, 3, content.length);
            Files.write(file, withBom);
            
            JsonIO.JsonValue result = JsonIO.read(file);
            assertTrue(result.isObject());
            
            JsonIO.JsonObject obj = (JsonIO.JsonObject) result;
            assertEquals("value", obj.getString("key").orElseThrow());
        }
    }
    
    // ==================== Large Scale Tests ====================
    
    @Nested
    @DisplayName("Large Scale Operations")
    class LargeScale {
        
        @Test
        @DisplayName("Large array - 10,000 elements")
        void largeArray() {
            JsonIO.JsonArray arr = new JsonIO.JsonArray();
            
            for (int i = 0; i < 10000; i++) {
                arr.add(new JsonIO.JsonObject()
                    .with("index", i)
                    .with("square", i * i)
                    .with("sqrt", Math.sqrt(i))
                    .with("even", i % 2 == 0)
                    .with("name", "Item_" + i));
            }
            
            assertEquals(10000, arr.size());
            
            // Serialize and parse
            String serialized = arr.toJsonString();
            JsonIO.JsonValue parsed = JsonIO.JsonValue.parse(serialized);
            
            assertTrue(parsed.isArray());
            JsonIO.JsonArray parsedArr = (JsonIO.JsonArray) parsed;
            assertEquals(10000, parsedArr.size());
            
            // Check a few random elements
            JsonIO.JsonObject item = (JsonIO.JsonObject) parsedArr.get(5000);
            assertEquals(5000, item.getInt("index").orElseThrow());
            assertTrue(item.getBoolean("even").orElseThrow());
        }
        
        @Test
        @DisplayName("Large object - 1,000 properties")
        void largeObject() {
            JsonIO.JsonObject obj = new JsonIO.JsonObject();
            
            for (int i = 0; i < 1000; i++) {
                obj.put("prop" + i, "value" + i);
            }
            
            assertEquals(1000, obj.entrySet().size());
            
            String serialized = obj.toJsonString();
            JsonIO.JsonObject parsed = (JsonIO.JsonObject) JsonIO.JsonValue.parse(serialized);
            
            assertEquals(1000, parsed.entrySet().size());
            assertEquals("value500", parsed.getString("prop500").orElseThrow());
        }
        
        @Test
        @DisplayName("Deeply nested structure with arrays")
        void deeplyNestedWithArrays() {
            JsonIO.JsonObject root = new JsonIO.JsonObject();
            
            JsonIO.JsonArray level1 = new JsonIO.JsonArray();
            for (int i = 0; i < 10; i++) {
                JsonIO.JsonObject obj = new JsonIO.JsonObject();
                JsonIO.JsonArray level2 = new JsonIO.JsonArray();
                for (int j = 0; j < 10; j++) {
                    JsonIO.JsonObject inner = new JsonIO.JsonObject();
                    JsonIO.JsonArray level3 = new JsonIO.JsonArray();
                    for (int k = 0; k < 10; k++) {
                        level3.add(new JsonIO.JsonObject()
                            .with("x", i)
                            .with("y", j)
                            .with("z", k)
                            .with("value", i * 100 + j * 10 + k));
                    }
                    inner.put("data", level3);
                    level2.add(inner);
                }
                obj.put("nested", level2);
                level1.add(obj);
            }
            root.put("root", level1);
            
            // This creates 1 + 10 + 100 + 1000 = 1111 objects, 1000 leaf objects
            String serialized = root.toJsonString();
            
            long start = System.currentTimeMillis();
            JsonIO.JsonValue parsed = JsonIO.JsonValue.parse(serialized);
            long end = System.currentTimeMillis();
            
            System.out.println("Parsing time for deep structure: " + (end - start) + "ms");
            assertTrue(parsed.isObject());
            
            // Verify one of the leaf values
            JsonIO.JsonObject parsedRoot = (JsonIO.JsonObject) parsed;
            JsonIO.JsonArray parsedLevel1 = parsedRoot.getArray("root").orElseThrow();
            JsonIO.JsonObject firstLevel1 = (JsonIO.JsonObject) parsedLevel1.get(0);
            JsonIO.JsonArray parsedLevel2 = firstLevel1.getArray("nested").orElseThrow();
            JsonIO.JsonObject firstLevel2 = (JsonIO.JsonObject) parsedLevel2.get(0);
            JsonIO.JsonArray parsedLevel3 = firstLevel2.getArray("data").orElseThrow();
            JsonIO.JsonObject leaf = (JsonIO.JsonObject) parsedLevel3.get(5);
            
            assertEquals(5, leaf.getInt("z").orElseThrow());
        }
        
        @Test
        @DisplayName("Full roundabout - complex real-world scenario")
        void fullRoundaboutComplexScenario() {
            // Create a complex data structure representing a city traffic management system
            JsonIO.JsonObject city = new JsonIO.JsonObject()
                .with("name", "Metropolis")
                .with("population", 2500000)
                .with("active", true);
            
            // Add districts
            JsonIO.JsonArray districts = new JsonIO.JsonArray();
            String[] districtNames = {"Downtown", "Uptown", "Midtown", "Suburbia", "Industrial", "Waterfront"};
            
            for (int d = 0; d < districtNames.length; d++) {
                JsonIO.JsonObject district = new JsonIO.JsonObject()
                    .with("id", d)
                    .with("name", districtNames[d])
                    .with("area", 100 + d * 50);
                
                // Add intersections (including roundabouts)
                JsonIO.JsonArray intersections = new JsonIO.JsonArray();
                Random rand = new Random(d); // Deterministic random
                
                for (int i = 0; i < 50; i++) {
                    JsonIO.JsonObject intersection = new JsonIO.JsonObject()
                        .with("id", d * 100 + i)
                        .with("type", i % 5 == 0 ? "roundabout" : "signalized")
                        .with("latitude", 40.0 + rand.nextDouble() * 10)
                        .with("longitude", -74.0 + rand.nextDouble() * 10)
                        .with("trafficLight", i % 5 != 0);
                    
                    // Add connecting roads
                    JsonIO.JsonArray roads = new JsonIO.JsonArray();
                    for (int r = 0; r < 4; r++) {
                        JsonIO.JsonObject road = new JsonIO.JsonObject()
                            .with("id", d * 1000 + i * 10 + r)
                            .with("name", "Road_" + (char)('A' + r))
                            .with("lanes", 2 + rand.nextInt(4))
                            .with("speedLimit", 25 + rand.nextInt(30))
                            .with("trafficVolume", rand.nextInt(5000))
                            .with("connectedTo", (d * 100 + i + r + 1) % 500);
                        
                        // Add traffic sensors for this road
                        JsonIO.JsonArray sensors = new JsonIO.JsonArray();
                        for (int s = 0; s < 3; s++) {
                            JsonIO.JsonObject sensor = new JsonIO.JsonObject()
                                .with("id", d * 10000 + i * 100 + r * 10 + s)
                                .with("type", s == 0 ? "induction" : s == 1 ? "camera" : "radar")
                                .with("active", rand.nextBoolean())
                                .with("lastReading", rand.nextInt(100));
                            
                            // Add historical data
                            JsonIO.JsonArray history = new JsonIO.JsonArray();
                            for (int h = 0; h < 24; h++) {
                                history.add(new JsonIO.JsonObject()
                                    .with("hour", h)
                                    .with("count", rand.nextInt(500))
                                    .with("avgSpeed", 20 + rand.nextInt(40)));
                            }
                            sensor.put("hourlyData", history);
                            sensors.add(sensor);
                        }
                        road.put("sensors", sensors);
                        roads.add(road);
                    }
                    intersection.put("roads", roads);
                    
                    // Add maintenance records
                    JsonIO.JsonArray maintenance = new JsonIO.JsonArray();
                    for (int m = 0; m < rand.nextInt(5); m++) {
                        maintenance.add(new JsonIO.JsonObject()
                            .with("date", String.format("2024-%02d-%02d", 
                                rand.nextInt(12) + 1, rand.nextInt(28) + 1))
                            .with("type", m % 3 == 0 ? "repair" : m % 3 == 1 ? "inspection" : "upgrade")
                            .with("cost", 1000 + rand.nextInt(10000))
                            .with("completed", rand.nextBoolean()));
                    }
                    intersection.put("maintenance", maintenance);
                    
                    intersections.add(intersection);
                }
                district.put("intersections", intersections);
                
                // Add traffic statistics
                JsonIO.JsonObject stats = new JsonIO.JsonObject()
                    .with("avgTrafficVolume", 2500 + rand.nextInt(3000))
                    .with("peakHour", 8 + rand.nextInt(10))
                    .with("accidentRate", rand.nextDouble() * 0.1)
                    .with("congestionIndex", rand.nextDouble() * 10);
                district.put("statistics", stats);
                
                districts.add(district);
            }
            city.put("districts", districts);
            
            // Add city-wide configuration
            JsonIO.JsonObject config = new JsonIO.JsonObject()
                .with("trafficSystem", new JsonIO.JsonObject()
                    .with("version", "2.3.1")
                    .with("mode", "adaptive")
                    .with("updateInterval", 300))
                .with("emergencyRoutes", new JsonIO.JsonArray() {{
                    add(new JsonIO.JsonObject().with("name", "Hospital Corridor").with("priority", 1));
                    add(new JsonIO.JsonObject().with("name", "Fire Station Access").with("priority", 1));
                    add(new JsonIO.JsonObject().with("name", "Police Priority Route").with("priority", 2));
                }})
                .with("restrictions", new JsonIO.JsonObject()
                    .with("maxSpeedCitywide", 55)
                    .with("truckRestricted", true)
                    .with("restrictedHours", new JsonIO.JsonArray() {{
                        add("07:00-09:00");
                        add("16:00-19:00");
                    }}));
            city.put("configuration", config);
            
            // Serialize
            long startSerialize = System.currentTimeMillis();
            String serialized = city.toJsonString();
            long endSerialize = System.currentTimeMillis();
            System.out.println("Serialization time: " + (endSerialize - startSerialize) + "ms");
            System.out.println("Serialized size: " + serialized.length() + " characters");
            
            // Parse
            long startParse = System.currentTimeMillis();
            JsonIO.JsonValue parsed = JsonIO.JsonValue.parse(serialized);
            long endParse = System.currentTimeMillis();
            System.out.println("Parsing time: " + (endParse - startParse) + "ms");
            
            // Verify
            assertTrue(parsed.isObject());
            JsonIO.JsonObject parsedCity = (JsonIO.JsonObject) parsed;
            
            assertEquals("Metropolis", parsedCity.getString("name").orElseThrow());
            assertEquals(2500000, parsedCity.getInt("population").orElseThrow());
            
            JsonIO.JsonArray parsedDistricts = parsedCity.getArray("districts").orElseThrow();
            assertEquals(6, parsedDistricts.size());
            
            // Check a random deep value
            JsonIO.JsonObject firstDistrict = (JsonIO.JsonObject) parsedDistricts.get(0);
            JsonIO.JsonArray intersections = firstDistrict.getArray("intersections").orElseThrow();
            JsonIO.JsonObject roundabout = null;
            
            for (int i = 0; i < intersections.size(); i++) {
                JsonIO.JsonObject inter = (JsonIO.JsonObject) intersections.get(i);
                if ("roundabout".equals(inter.getString("type").orElse(""))) {
                    roundabout = inter;
                    break;
                }
            }
            
            assertNotNull(roundabout, "Should have at least one roundabout");
            JsonIO.JsonArray roads = roundabout.getArray("roads").orElseThrow();
            assertTrue(roads.size() > 0);
            
            // Write to file and read back
            Path file = tempDir.resolve("city_traffic.json");
            assertTrue(JsonIO.write(file, city));
            
            long startFileRead = System.currentTimeMillis();
            JsonIO.JsonValue fileRead = JsonIO.read(file);
            long endFileRead = System.currentTimeMillis();
            System.out.println("File read time: " + (endFileRead - startFileRead) + "ms");
            
            assertTrue(fileRead.isObject());
            JsonIO.JsonObject fileCity = (JsonIO.JsonObject) fileRead;
            assertEquals("Metropolis", fileCity.getString("name").orElseThrow());
        }
    }
    
    // ==================== Concurrent Access Tests ====================
    
    @Nested
    @DisplayName("Concurrent Operations")
    class ConcurrentTests {
        
        @Test
        @DisplayName("Concurrent parsing from multiple threads")
        void concurrentParsing() throws Exception {
            String json = new JsonIO.JsonObject()
                .with("key1", "value1")
                .with("key2", 42)
                .with("key3", true)
                .with("nested", new JsonIO.JsonObject()
                    .with("inner", new JsonIO.JsonArray() {{
                        add("a");
                        add("b");
                        add("c");
                    }}))
                .toJsonString();
            
            ExecutorService executor = Executors.newFixedThreadPool(10);
            int taskCount = 1000;
            CountDownLatch latch = new CountDownLatch(taskCount);
            List<Future<Boolean>> futures = new ArrayList<>();
            
            for (int i = 0; i < taskCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        JsonIO.JsonValue parsed = JsonIO.JsonValue.parse(json);
                        boolean result = parsed.isObject() && 
                            ((JsonIO.JsonObject)parsed).getString("key1").orElse("").equals("value1");
                        latch.countDown();
                        return result;
                    } catch (Exception e) {
                        latch.countDown();
                        return false;
                    }
                }));
            }
            
            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();
            
            for (Future<Boolean> future : futures) {
                assertTrue(future.get(), "All concurrent parses should succeed");
            }
        }
        
        @Test
        @DisplayName("Concurrent file writes and reads")
        void concurrentFileIO() throws Exception {
            ExecutorService executor = Executors.newFixedThreadPool(8);
            int taskCount = 100;
            CountDownLatch latch = new CountDownLatch(taskCount);
            List<Future<Boolean>> futures = new ArrayList<>();
            
            for (int i = 0; i < taskCount; i++) {
                final int index = i;
                Path file = tempDir.resolve("concurrent_" + index + ".json");
                
                futures.add(executor.submit(() -> {
                    try {
                        JsonIO.JsonObject obj = new JsonIO.JsonObject()
                            .with("thread", Thread.currentThread().getName())
                            .with("index", index)
                            .with("timestamp", System.currentTimeMillis());
                        
                        boolean writeResult = JsonIO.write(file, obj);
                        if (!writeResult) return false;
                        
                        JsonIO.JsonValue read = JsonIO.read(file);
                        boolean readResult = read != null && read.isObject() &&
                            ((JsonIO.JsonObject)read).getInt("index").orElse(-1) == index;
                        
                        latch.countDown();
                        return readResult;
                    } catch (Exception e) {
                        latch.countDown();
                        return false;
                    }
                }));
            }
            
            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();
            
            for (Future<Boolean> future : futures) {
                assertTrue(future.get(), "All concurrent file operations should succeed");
            }
        }
    }
    
    // ==================== Type Safety Tests ====================
    
    @Nested
    @DisplayName("Type Safety")
    class TypeSafety {
        
        @Test
        @DisplayName("Type checking methods")
        void typeChecking() {
            assertTrue(new JsonIO.JsonObject().isObject());
            assertTrue(new JsonIO.JsonArray().isArray());
            assertTrue(new JsonIO.JsonString("test").isString());
            assertTrue(new JsonIO.JsonNumber(42).isNumber());
            assertTrue(new JsonIO.JsonBoolean(true).isBoolean());
            assertTrue(JsonIO.JsonNull.INSTANCE.isNull());
            
            assertFalse(new JsonIO.JsonObject().isArray());
            assertFalse(new JsonIO.JsonArray().isString());
            assertFalse(new JsonIO.JsonString("test").isNumber());
        }
        
        @Test
        @DisplayName("Type-safe accessors return Optional")
        void typeSafeAccessors() {
            JsonIO.JsonObject obj = new JsonIO.JsonObject()
                .with("string", "value")
                .with("number", 42)
                .with("bool", true)
                .with("null", JsonIO.JsonNull.INSTANCE);
            
            assertTrue(obj.getString("string").isPresent());
            assertTrue(obj.getNumber("number").isPresent());
            assertTrue(obj.getBoolean("bool").isPresent());
            
            assertFalse(obj.getString("nonexistent").isPresent());
            assertFalse(obj.getString("number").isPresent()); // Wrong type
            assertFalse(obj.getNumber("string").isPresent()); // Wrong type
            assertFalse(obj.getBoolean("string").isPresent()); // Wrong type
            
            // get() on wrong type returns empty
            JsonIO.JsonObject nested = new JsonIO.JsonObject();
            obj.put("object", nested);
            
            assertTrue(obj.getObject("object").isPresent());
            assertFalse(obj.getObject("string").isPresent());
            
            JsonIO.JsonArray arr = new JsonIO.JsonArray();
            obj.put("array", arr);
            
            assertTrue(obj.getArray("array").isPresent());
            assertFalse(obj.getArray("string").isPresent());
        }
        
        @Test
        @DisplayName("Number type conversions")
        void numberTypeConversions() {
            JsonIO.JsonNumber intNum = new JsonIO.JsonNumber(42);
            JsonIO.JsonNumber doubleNum = new JsonIO.JsonNumber(3.14159);
            JsonIO.JsonNumber longNum = new JsonIO.JsonNumber(9223372036854775807L);
            
            assertEquals(42, intNum.intValue());
            assertEquals(42L, intNum.longValue());
            assertEquals(42.0, intNum.doubleValue(), 0.001);
            
            assertEquals(3, doubleNum.intValue());
            assertEquals(3L, doubleNum.longValue());
            assertEquals(3.14159, doubleNum.doubleValue(), 0.00001);
            
            assertEquals(9223372036854775807L, longNum.longValue());
        }
    }
    
    // ==================== Serialization Format Tests ====================
    
    @Nested
    @DisplayName("Serialization Format")
    class SerializationFormat {
        
        @Test
        @DisplayName("Object serialization format")
        void objectSerializationFormat() {
            JsonIO.JsonObject obj = new JsonIO.JsonObject()
                .with("name", "John")
                .with("age", 30)
                .with("active", true)
                .with("null", JsonIO.JsonNull.INSTANCE);
            
            String json = obj.toJsonString();
            
            // Since it's a LinkedHashMap, order is preserved
            assertTrue(json.startsWith("{"));
            assertTrue(json.endsWith("}"));
            assertTrue(json.contains("\"name\":\"John\""));
            assertTrue(json.contains("\"age\":30"));
            assertTrue(json.contains("\"active\":true"));
            assertTrue(json.contains("\"null\":null"));
        }
        
        @Test
        @DisplayName("Array serialization format")
        void arraySerializationFormat() {
            JsonIO.JsonArray arr = new JsonIO.JsonArray();
            arr.add("first");
            arr.add(42);
            arr.add(false);
            arr.add(JsonIO.JsonNull.INSTANCE);
            
            String json = arr.toJsonString();
            
            assertEquals("[\"first\",42,false,null]", json);
        }
        
        @Test
        @DisplayName("Nested serialization format")
        void nestedSerializationFormat() {
            JsonIO.JsonObject obj = new JsonIO.JsonObject()
                .with("nested", new JsonIO.JsonObject()
                    .with("array", new JsonIO.JsonArray() {{
                        add(new JsonIO.JsonObject().with("key", "value"));
                        add("string");
                        add(123);
                    }}));
            
            String json = obj.toJsonString();
            
            // Parse it back to verify it's valid JSON
            JsonIO.JsonValue parsed = JsonIO.JsonValue.parse(json);
            assertTrue(parsed.isObject());
        }
        
        @Test
        @DisplayName("Special character escaping in serialization")
        void specialCharacterEscaping() {
            JsonIO.JsonObject obj = new JsonIO.JsonObject()
                .with("quotes", "He said \"Hello\"")
                .with("backslash", "Path\\to\\file")
                .with("newline", "Line1\nLine2")
                .with("tab", "Column1\tColumn2")
                .with("unicode", "Smiley: \uD83D\uDE00");
            
            String json = obj.toJsonString();
            
            // Parse back and verify
            JsonIO.JsonObject parsed = (JsonIO.JsonObject) JsonIO.JsonValue.parse(json);
            
            assertEquals("He said \"Hello\"", parsed.getString("quotes").orElseThrow());
            assertEquals("Path\\to\\file", parsed.getString("backslash").orElseThrow());
            assertEquals("Line1\nLine2", parsed.getString("newline").orElseThrow());
            assertEquals("Column1\tColumn2", parsed.getString("tab").orElseThrow());
            assertEquals("Smiley: 😀", parsed.getString("unicode").orElseThrow());
        }
    }
    
    // ==================== Performance Tests ====================
    
    @Nested
    @DisplayName("Performance")
    @Tag("performance")
    class Performance {
        
        @Test
        @DisplayName("Parse large JSON array - 100,000 numbers")
        void parseLargeNumberArray() {
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < 100000; i++) {
                if (i > 0) json.append(",");
                json.append(i);
            }
            json.append("]");
            
            long start = System.currentTimeMillis();
            JsonIO.JsonValue result = JsonIO.JsonValue.parse(json.toString());
            long end = System.currentTimeMillis();
            
            System.out.println("Parse 100k numbers: " + (end - start) + "ms");
            assertTrue(result.isArray());
            assertEquals(100000, ((JsonIO.JsonArray)result).size());
        }
        
        @Test
        @DisplayName("Serialize large object - 10,000 properties")
        void serializeLargeObject() {
            JsonIO.JsonObject obj = new JsonIO.JsonObject();
            for (int i = 0; i < 10000; i++) {
                obj.put("prop" + i, "value" + i);
            }
            
            long start = System.currentTimeMillis();
            String json = obj.toJsonString();
            long end = System.currentTimeMillis();
            
            System.out.println("Serialize 10k properties: " + (end - start) + "ms");
            System.out.println("Size: " + json.length() + " chars");
            
            assertTrue(json.length() > 100000);
        }
        
        @Test
        @DisplayName("Deep navigation performance")
        void deepNavigationPerformance() {
            // Create deep structure
            JsonIO.JsonObject root = new JsonIO.JsonObject();
            JsonIO.JsonObject current = root;
            
            for (int i = 0; i < 1000; i++) {
                JsonIO.JsonObject next = new JsonIO.JsonObject();
                current.put("next", next);
                current = next;
            }
            current.put("value", 42);
            
            String json = root.toJsonString();
            
            long start = System.currentTimeMillis();
            JsonIO.JsonObject parsed = (JsonIO.JsonObject) JsonIO.JsonValue.parse(json);
            
            // Navigate to the bottom
            JsonIO.JsonValue nav = parsed;
            for (int i = 0; i < 1000; i++) {
                nav = ((JsonIO.JsonObject)nav).get("next");
            }
            long end = System.currentTimeMillis();
            
            System.out.println("Deep navigation (1000 levels): " + (end - start) + "ms");
            assertEquals(42, ((JsonIO.JsonNumber)((JsonIO.JsonObject)nav).get("value")).intValue());
        }
    }
    
    // ==================== Unicode and Encoding Tests ====================
    
    @Nested
    @DisplayName("Unicode and Encoding")
    class UnicodeEncoding {
        
        @Test
        @DisplayName("Handle various Unicode characters")
        void handleUnicodeCharacters() {
            String[] unicodeStrings = {
                "Hello 世界",                    // Chinese
                "Привет мир",                   // Russian
                "مرحبا بالعالم",                // Arabic
                "नमस्ते दुनिया",                // Hindi
                "👋🌍",                          // Emojis
                "αβγδε",                        // Greek
                "日本語",                        // Japanese
                "한국어"                         // Korean
            };
            
            for (String text : unicodeStrings) {
                JsonIO.JsonObject obj = new JsonIO.JsonObject().with("text", text);
                String json = obj.toJsonString();
                
                JsonIO.JsonObject parsed = (JsonIO.JsonObject) JsonIO.JsonValue.parse(json);
                assertEquals(text, parsed.getString("text").orElseThrow());
            }
        }
        
        @Test
        @DisplayName("Mixed Unicode in keys and values")
        void mixedUnicode() {
            JsonIO.JsonObject obj = new JsonIO.JsonObject()
                .with("ключ", "значение")
                .with("鍵", "値")
                .with("clé", "valeur");
            
            String json = obj.toJsonString();
            JsonIO.JsonObject parsed = (JsonIO.JsonObject) JsonIO.JsonValue.parse(json);
            
            assertEquals("значение", parsed.getString("ключ").orElseThrow());
            assertEquals("値", parsed.getString("鍵").orElseThrow());
            assertEquals("valeur", parsed.getString("clé").orElseThrow());
        }
        
        @Test
        @DisplayName("Surrogate pairs handling")
        void surrogatePairs() {
            // Emoji that requires surrogate pair
            String emoji = "🎉 Celebration 🎊";
            String complexEmoji = "👨‍👩‍👧‍👦 Family"; // Family emoji with ZWJ
            
            JsonIO.JsonObject obj = new JsonIO.JsonObject()
                .with("party", emoji)
                .with("family", complexEmoji);
            
            String json = obj.toJsonString();
            JsonIO.JsonObject parsed = (JsonIO.JsonObject) JsonIO.JsonValue.parse(json);
            
            assertEquals(emoji, parsed.getString("party").orElseThrow());
            assertEquals(complexEmoji, parsed.getString("family").orElseThrow());
        }
    }
    
    // ==================== Boundary Value Tests ====================
    
    @Nested
    @DisplayName("Boundary Values")
    class BoundaryValues {
        
        @Test
        @DisplayName("Number boundary values")
        void numberBoundaries() {
            Number[] boundaryNumbers = {
                Integer.MAX_VALUE,
                Integer.MIN_VALUE,
                Long.MAX_VALUE,
                Long.MIN_VALUE,
                Double.MAX_VALUE,
                Double.MIN_VALUE,
                Double.MIN_NORMAL,
                Float.MAX_VALUE,
                Float.MIN_VALUE,
                0,
                -0.0,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                Double.NaN
            };
            
            for (var num : boundaryNumbers) {
                JsonIO.JsonNumber jsonNum = new JsonIO.JsonNumber(num);
                String json = jsonNum.toJsonString();
                
                JsonIO.JsonNumber parsed = (JsonIO.JsonNumber) JsonIO.JsonValue.parse(json);
                
                if (num instanceof Double d && (d.isNaN() || d.isInfinite())) {
                    assertEquals("0", parsed.toJsonString());
                } else {
                    assertEquals(num.doubleValue(), parsed.doubleValue(), Math.abs(0.001 * num.doubleValue()));
                }
            }
        }
        
        @Test
        @DisplayName("String length boundaries")
        void stringLengthBoundaries() {
            // Empty string
            JsonIO.JsonObject obj1 = new JsonIO.JsonObject().with("empty", "");
            assertEquals("", ((JsonIO.JsonObject)JsonIO.JsonValue.parse(obj1.toJsonString()))
                .getString("empty").orElseThrow());
            
            // Very long string (1MB)
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000000; i++) {
                sb.append('a');
            }
            String longString = sb.toString();
            
            JsonIO.JsonObject obj2 = new JsonIO.JsonObject().with("long", longString);
            String json = obj2.toJsonString();
            
            long start = System.currentTimeMillis();
            JsonIO.JsonObject parsed = (JsonIO.JsonObject) JsonIO.JsonValue.parse(json);
            long end = System.currentTimeMillis();
            
            System.out.println("Parse 1MB string: " + (end - start) + "ms");
            assertEquals(longString.length(), parsed.getString("long").orElseThrow().length());
        }
        
        @Test
        @DisplayName("Maximum nesting depth")
        void maximumNestingDepth() {
            int depth = 500;
            
            // Build deep nested structure
            JsonIO.JsonArray arr = new JsonIO.JsonArray();
            JsonIO.JsonArray current = arr;
            
            for (int i = 0; i < depth; i++) {
                JsonIO.JsonArray next = new JsonIO.JsonArray();
                current.add(next);
                current.add("level" + i);
                current = next;
            }
            current.add("bottom");
            
            String json = arr.toJsonString();
            JsonIO.JsonValue parsed = JsonIO.JsonValue.parse(json);
            
            // Navigate to bottom
            JsonIO.JsonArray nav = (JsonIO.JsonArray) parsed;
            for (int i = 0; i < depth; i++) {
                nav = (JsonIO.JsonArray) nav.get(0);
            }
            
            assertEquals("bottom", ((JsonIO.JsonString)nav.get(0)).getValue());
        }
    }
    
    // ==================== Integration Scenario Tests ====================
    
    @Nested
    @DisplayName("Real-world Scenarios")
    class RealWorldScenarios {
        
        @Test
        @DisplayName("Configuration file parsing")
        void configurationFile() throws IOException {
            String configJson = """
                {
                    "app": {
                        "name": "MyApplication",
                        "version": "2.0.1",
                        "debug": true,
                        "features": {
                            "logging": true,
                            "metrics": false,
                            "cache": {
                                "enabled": true,
                                "ttl": 3600
                            }
                        }
                    },
                    "database": {
                        "host": "localhost",
                        "port": 5432,
                        "name": "appdb",
                        "pool": {
                            "minSize": 5,
                            "maxSize": 20,
                            "timeout": 30000
                        }
                    },
                    "servers": [
                        {
                            "host": "server1.example.com",
                            "port": 8080,
                            "weight": 1
                        },
                        {
                            "host": "server2.example.com",
                            "port": 8080,
                            "weight": 2
                        }
                    ]
                }
                """;
            
            Path configFile = tempDir.resolve("config.json");
            Files.writeString(configFile, configJson);
            
            JsonIO.JsonObject config = (JsonIO.JsonObject) JsonIO.read(configFile);
            
            assertEquals("MyApplication", config.getObject("app")
                .flatMap(app -> app.getString("name")).orElseThrow());
            
            assertEquals(5432, config.getObject("database")
                .flatMap(db -> db.getInt("port")).orElseThrow());
            
            JsonIO.JsonArray servers = config.getArray("servers").orElseThrow();
            assertEquals(2, servers.size());
            
            JsonIO.JsonObject server1 = (JsonIO.JsonObject) servers.get(0);
            assertEquals("server1.example.com", server1.getString("host").orElseThrow());
            
            // Modify and save
            config.getObject("app").ifPresent(app -> 
                app.put("version", "2.0.2"));
            
            assertTrue(JsonIO.write(configFile, config));
            
            // Verify changes
            JsonIO.JsonObject updated = (JsonIO.JsonObject) JsonIO.read(configFile);
            assertEquals("2.0.2", updated.getObject("app")
                .flatMap(app -> app.getString("version")).orElseThrow());
        }
        
        @Test
        @DisplayName("API response simulation")
        void apiResponseSimulation() {
            JsonIO.JsonObject response = new JsonIO.JsonObject()
                .with("status", "success")
                .with("code", 200)
                .with("data", new JsonIO.JsonObject()
                    .with("users", new JsonIO.JsonArray() {{
                        add(new JsonIO.JsonObject()
                            .with("id", 1)
                            .with("username", "john_doe")
                            .with("email", "john@example.com")
                            .with("roles", new JsonIO.JsonArray() {{
                                add("user");
                                add("editor");
                            }}));
                        add(new JsonIO.JsonObject()
                            .with("id", 2)
                            .with("username", "jane_smith")
                            .with("email", "jane@example.com")
                            .with("roles", new JsonIO.JsonArray() {{
                                add("user");
                                add("admin");
                            }}));
                    }})
                    .with("pagination", new JsonIO.JsonObject()
                        .with("page", 1)
                        .with("perPage", 10)
                        .with("total", 42)
                        .with("hasMore", true)))
                .with("metadata", new JsonIO.JsonObject()
                    .with("requestId", "abc-123-def")
                    .with("timestamp", System.currentTimeMillis()));
            
            String json = response.toJsonString();
            JsonIO.JsonObject parsed = (JsonIO.JsonObject) JsonIO.JsonValue.parse(json);
            
            assertEquals("success", parsed.getString("status").orElseThrow());
            assertEquals(200, parsed.getInt("code").orElseThrow());
            
            JsonIO.JsonObject data = parsed.getObject("data").orElseThrow();
            JsonIO.JsonArray users = data.getArray("users").orElseThrow();
            
            JsonIO.JsonObject firstUser = (JsonIO.JsonObject) users.get(0);
            assertEquals("john_doe", firstUser.getString("username").orElseThrow());
            
            JsonIO.JsonArray roles = firstUser.getArray("roles").orElseThrow();
            assertEquals(2, roles.size());
            assertTrue(roles.asList().stream()
                .map(v -> ((JsonIO.JsonString)v).getValue())
                .anyMatch("editor"::equals));
        }
    }
}