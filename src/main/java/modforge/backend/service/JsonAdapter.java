package modforge.backend.service;

import modforge.backend.model.ModItem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

public final class JsonAdapter {
	private static final Logger log = Logger.getLogger(JsonAdapter.class.getName());

	private Path baseDir;

	public void setBaseDir(String appDataDir) {
		this.baseDir = Path.of(appDataDir, "ModForge");
	}

	public JsonAdapter(String appDataDir) {
		this.baseDir = Path.of(appDataDir, "ModForge");
	}

	public static JsonValue read(Path configFile) {
		try {
			if (Files.exists(configFile)) {
				String content = Files.readString(configFile, StandardCharsets.UTF_8);
				return JsonValue.parse(content);
			}
		} catch (Exception e) {
			log.severe("Config load error: " + e.getMessage());
		}
		return null;
	}

	public static void write(Path configFile, JsonObject jsonObject) throws IOException {
		Files.createDirectories(configFile.getParent());
		Files.writeString(configFile, jsonObject.toJsonString(), StandardCharsets.UTF_8);
	}

	// Simple JSON value representation
	public static abstract class JsonValue {
		public abstract String toJsonString();

		public static JsonValue parse(String json) {
			return new JsonParser(json).parse();
		}
	}

	public static class JsonObject extends JsonValue {
		private final Map<String, JsonValue> properties = new LinkedHashMap<>();

		public void put(String key, JsonValue value) {
			properties.put(key, value);
		}

		public JsonValue get(String key) {
			return properties.get(key);
		}

		public boolean has(String key) {
			return properties.containsKey(key);
		}

		public Set<Map.Entry<String, JsonValue>> entrySet() {
			return properties.entrySet();
		}

		@Override
		public String toJsonString() {
			StringBuilder sb = new StringBuilder("{");
			boolean first = true;
			for (Map.Entry<String, JsonValue> entry : properties.entrySet()) {
				if (!first) sb.append(",");
				first = false;
				sb.append("\"").append(escape(entry.getKey())).append("\":");
				sb.append(entry.getValue().toJsonString());
			}
			sb.append("}");
			return sb.toString();
		}
	}

	public static class JsonArray extends JsonValue {
		private final List<JsonValue> elements = new ArrayList<>();

		public void add(JsonValue value) {
			elements.add(value);
		}

		public JsonValue get(int index) {
			return elements.get(index);
		}

		public int size() {
			return elements.size();
		}

		@Override
		public String toJsonString() {
			StringBuilder sb = new StringBuilder("[");
			boolean first = true;
			for (JsonValue value : elements) {
				if (!first) sb.append(",");
				first = false;
				sb.append(value.toJsonString());
			}
			sb.append("]");
			return sb.toString();
		}
	}

	public static class JsonString extends JsonValue {
		private final String value;

		public JsonString(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}

		@Override
		public String toJsonString() {
			return "\"" + escape(value) + "\"";
		}
	}

	public static class JsonNumber extends JsonValue {
		private final Number value;

		public JsonNumber(Number value) {
			this.value = value;
		}

		public Number getValue() {
			return value;
		}

		@Override
		public String toJsonString() {
			return value.toString();
		}
	}

	public static class JsonBoolean extends JsonValue {
		private final boolean value;

		public JsonBoolean(boolean value) {
			this.value = value;
		}

		public boolean getValue() {
			return value;
		}

		@Override
		public String toJsonString() {
			return Boolean.toString(value);
		}
	}

	public static class JsonNull extends JsonValue {
		@Override
		public String toJsonString() {
			return "null";
		}
	}

	// Simple JSON parser
	private static class JsonParser {
		private final String json;
		private int pos = 0;

		JsonParser(String json) {
			this.json = json;
		}

		JsonValue parse() {
			skipWhitespace();
			return parseValue();
		}

		private JsonValue parseValue() {
			skipWhitespace();
			if (pos >= json.length()) return new JsonNull();

			char c = json.charAt(pos);
			if (c == '{') return parseObject();
			if (c == '[') return parseArray();
			if (c == '"') return parseString();
			if (c == 't' || c == 'f') return parseBoolean();
			if (c == 'n') return parseNull();
			if (c == '-' || (c >= '0' && c <= '9')) return parseNumber();

			throw new RuntimeException("Unexpected character: " + c);
		}

		private JsonObject parseObject() {
			JsonObject obj = new JsonObject();
			pos++; // skip '{'
			skipWhitespace();

			if (pos < json.length() && json.charAt(pos) == '}') {
				pos++;
				return obj;
			}

			while (true) {
				skipWhitespace();
				if (json.charAt(pos) != '"') throw new RuntimeException("Expected string key");
				String key = parseString().getValue();
				skipWhitespace();
				if (json.charAt(pos) != ':') throw new RuntimeException("Expected ':'");
				pos++;
				JsonValue value = parseValue();
				obj.put(key, value);
				skipWhitespace();
				if (json.charAt(pos) == '}') {
					pos++;
					break;
				}
				if (json.charAt(pos) != ',') throw new RuntimeException("Expected ',' or '}'");
				pos++;
			}
			return obj;
		}

		private JsonArray parseArray() {
			JsonArray arr = new JsonArray();
			pos++; // skip '['
			skipWhitespace();

			if (pos < json.length() && json.charAt(pos) == ']') {
				pos++;
				return arr;
			}

			while (true) {
				arr.add(parseValue());
				skipWhitespace();
				if (json.charAt(pos) == ']') {
					pos++;
					break;
				}
				if (json.charAt(pos) != ',') throw new RuntimeException("Expected ',' or ']'");
				pos++;
			}
			return arr;
		}

		private JsonString parseString() {
			pos++; // skip opening quote
			StringBuilder sb = new StringBuilder();
			while (pos < json.length()) {
				char c = json.charAt(pos);
				if (c == '"') {
					pos++;
					return new JsonString(sb.toString());
				}
				if (c == '\\') {
					pos++;
					if (pos >= json.length()) break;
					char escaped = json.charAt(pos);
					switch (escaped) {
						case '"': sb.append('"'); break;
						case '\\': sb.append('\\'); break;
						case '/': sb.append('/'); break;
						case 'b': sb.append('\b'); break;
						case 'f': sb.append('\f'); break;
						case 'n': sb.append('\n'); break;
						case 'r': sb.append('\r'); break;
						case 't': sb.append('\t'); break;
						default: sb.append(escaped);
					}
				} else {
					sb.append(c);
				}
				pos++;
			}
			throw new RuntimeException("Unterminated string");
		}

		private JsonNumber parseNumber() {
			StringBuilder sb = new StringBuilder();
			while (pos < json.length()) {
				char c = json.charAt(pos);
				if ((c >= '0' && c <= '9') || c == '-' || c == '.' || c == 'e' || c == 'E' || c == '+') {
					sb.append(c);
					pos++;
				} else {
					break;
				}
			}
			String numStr = sb.toString();
			if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E")) {
				return new JsonNumber(Double.parseDouble(numStr));
			} else {
				try {
					return new JsonNumber(Integer.parseInt(numStr));
				} catch (NumberFormatException e) {
					return new JsonNumber(Long.parseLong(numStr));
				}
			}
		}

		private JsonBoolean parseBoolean() {
			if (json.startsWith("true", pos)) {
				pos += 4;
				return new JsonBoolean(true);
			} else if (json.startsWith("false", pos)) {
				pos += 5;
				return new JsonBoolean(false);
			}
			throw new RuntimeException("Expected boolean");
		}

		private JsonNull parseNull() {
			if (json.startsWith("null", pos)) {
				pos += 4;
				return new JsonNull();
			}
			throw new RuntimeException("Expected null");
		}

		private void skipWhitespace() {
			while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
				pos++;
			}
		}
	}

	private static String escape(String s) {
		StringBuilder sb = new StringBuilder();
		for (char c : s.toCharArray()) {
			switch (c) {
				case '"': sb.append("\\\""); break;
				case '\\': sb.append("\\\\"); break;
				case '\b': sb.append("\\b"); break;
				case '\f': sb.append("\\f"); break;
				case '\n': sb.append("\\n"); break;
				case '\r': sb.append("\\r"); break;
				case '\t': sb.append("\\t"); break;
				default:
					if (c < 0x20) {
						sb.append(String.format("\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
			}
		}
		return sb.toString();
	}

	// Convert ModItem to/from JsonValue
	private JsonValue modItemToJson(ModItem item) {
		JsonObject obj = new JsonObject();
		// Add all ModItem fields here based on your ModItem class
		// Example:
		// obj.put("id", new JsonString(item.getId()));
		// obj.put("name", new JsonString(item.getName()));
		// obj.put("version", new JsonString(item.getVersion()));
		return obj;
	}

	private ModItem jsonToModItem(JsonValue value) {
		if (!(value instanceof JsonObject)) return null;
		JsonObject obj = (JsonObject) value;
		// Populate ModItem fields here
		// Example:
		// if (obj.has("id")) item.setId(((JsonString)obj.get("id")).getValue());
		// if (obj.has("name")) item.setName(((JsonString)obj.get("name")).getValue());
		return null;
	}

	public List<ModItem> readFromJson(String filePath) {
		Path file = Path.of(filePath);
		if (!Files.exists(file)) return new ArrayList<>();
		try {
			String content = Files.readString(file, StandardCharsets.UTF_8);
			JsonValue parsed = JsonValue.parse(content);
			List<ModItem> items = new ArrayList<>();

			if (parsed instanceof JsonArray) {
				JsonArray arr = (JsonArray) parsed;
				for (int i = 0; i < arr.size(); i++) {
					ModItem item = jsonToModItem(arr.get(i));
					if (item != null) items.add(item);
				}
			}
			return items;
		} catch (IOException e) {
			log.warning("JSON read failed (" + filePath + "): " + e.getMessage());
			return new ArrayList<>();
		}
	}

	public void writeAsJson(List<ModItem> items) {
		if (items == null || items.isEmpty()) return;
		JsonArray arr = new JsonArray();
		for (ModItem item : items) {
			arr.add(modItemToJson(item));
		}
		Path target = baseDir.resolve("moditems.json");
		try {
			Files.createDirectories(target.getParent());
			Files.writeString(target, arr.toJsonString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			log.warning("JSON write failed: " + e.getMessage());
		}
	}
}