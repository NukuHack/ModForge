package modforge.backend.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

import static modforge.Util.escapeJson;
import static modforge.Util.pickFolderAsync;

public final class JsonIO {
	private static final Logger log = Logger.getLogger(JsonIO.class.getName());
	
	private JsonIO() {
	}
	
	public static JsonValue read(String target) {
		return read(Path.of(target));
	}
	
	public static JsonValue read(Path target) {
		try {
			if (Files.exists(target)) {
				String content = Files.readString(target, StandardCharsets.UTF_8);
				return JsonValue.parse(content);
			}
		} catch (Exception e) {
			log.severe("Config load error: " + e.getMessage());
		}
		return null;
	}
	
	public static boolean write(Path target, List<JsonObject> items) {
		if (items == null || items.isEmpty())
			return false;
		JsonArray arr = new JsonArray();
		for (final JsonObject item : items) {
			arr.add(item);
		}
		try {
			Files.createDirectories(target.getParent());
			Files.writeString(target, arr.toJsonString(), StandardCharsets.UTF_8);
			return true;
		} catch (IOException e) {
			log.warning("JSON write failed: " + e.getMessage());
		}
		return false;
	}
	
	public static boolean write(String target, List<JsonObject> items) {
		return write(Path.of(target), items);
	}
	
	public static boolean write(Path target, JsonObject item) {
		if (item == null)
			return false;
		try {
			Files.createDirectories(target.getParent());
			Files.writeString(target, item.toJsonString(), StandardCharsets.UTF_8);
			return true;
		} catch (IOException e) {
			log.warning("JSON write failed: " + e.getMessage());
		}
		return false;
	}
	
	public static boolean write(String target, JsonObject item) {
		return write(Path.of(target), item);
	}
	
	// Simple JSON value representation
	public static abstract class JsonValue {
		public static JsonValue parse(String json) {
			return new JsonParser(json).parse();
		}
		
		public abstract String toJsonString();
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
			final var sb = new StringBuilder("{");
			boolean first = true;
			for (Map.Entry<String, JsonValue> entry : properties.entrySet()) {
				if (! first)
					sb.append(",");
				first = false;
				sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
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
				if (! first)
					sb.append(",");
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
			return "\"" + escapeJson(value) + "\"";
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
			if (pos >= json.length())
				return new JsonNull();
			
			char c = json.charAt(pos);
			return switch (c) {
				case '{' -> parseObject();
				case '[' -> parseArray();
				case '"' -> parseString();
				case 'n' -> parseNull();
				case 't', 'f' -> parseBoolean();
				case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> parseNumber();
				default -> throw new RuntimeException("Unexpected character: " + c);
			};
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
				if (json.charAt(pos) != '"')
					throw new RuntimeException("Expected string key");
				final String key = parseString().getValue();
				skipWhitespace();
				if (json.charAt(pos) != ':')
					throw new RuntimeException("Expected ':'");
				pos++;
				final JsonValue value = parseValue();
				obj.put(key, value);
				skipWhitespace();
				if (json.charAt(pos) == '}') {
					pos++;
					break;
				}
				if (json.charAt(pos) != ',')
					throw new RuntimeException("Expected ',' or '}'");
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
				if (json.charAt(pos) != ',')
					throw new RuntimeException("Expected ',' or ']'");
				pos++;
			}
			return arr;
		}
		
		private JsonString parseString() {
			pos++; // skip opening quote
			final var sb = new StringBuilder();
			while (pos < json.length()) {
				char c = json.charAt(pos);
				if (c == '"') {
					pos++;
					return new JsonString(sb.toString());
				}
				if (c == '\\') {
					pos++;
					if (pos >= json.length()) break;
					sb.append(switch (json.charAt(pos)) {
						case '"' -> '"';
						case '\\' -> '\\';
						case '/' -> '/';
						case 'b' -> '\b';
						case 'f' -> '\f';
						case 'n' -> '\n';
						case 'r' -> '\r';
						case 't' -> '\t';
						default -> json.charAt(pos);
					});
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
}