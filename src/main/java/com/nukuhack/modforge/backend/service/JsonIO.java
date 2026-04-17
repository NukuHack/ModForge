package com.nukuhack.modforge.backend.service;

import com.nukuhack.modforge.Util;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JsonIO {

    public static JsonValue read(String target) {
        return read(Path.of(target));
    }

    public static JsonValue read(Path target) {
        try {
            if (! Files.exists(target))
                throw new FileNotFoundException("Could not find the file");
            var content = Files.readString(target, StandardCharsets.UTF_8);
            // Strip UTF-8 BOM if present (written by some editors/tools on Windows)
            if (content.startsWith("\uFEFF"))
                content = content.substring(1);

            return JsonValue.parse(content);
        } catch (Exception e) {
            log.error("File load error: {}", e.getMessage());
        }
        return null;
    }

    public static boolean write(Path target, JsonValue value) {
        if (value == null) return false;
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, value.toJsonString(), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            log.warn("JSON write failed [{}]: {}", target, e.getMessage());
        }
        return false;
    }

    public static boolean write(String target, JsonValue value) {
        return write(Path.of(target), value);
    }

    /**
     * Convenience: write a list of objects as a JSON array (empty list writes "[]").
     */
    public static boolean write(Path target, List<JsonObject> items) {
        JsonArray arr = new JsonArray();
        if (items != null) items.forEach(arr::add);
        return write(target, arr);
    }

    public static boolean write(String target, List<JsonObject> items) {
        return write(Path.of(target), items);
    }

    public static abstract class JsonValue {
        public static JsonValue parse(String json) {
            return new JsonParser(json).parse();
        }

        public abstract String toJsonString();

        /**
         * Null-safe cast helpers.
         */
        public boolean isObject() {
            return this instanceof JsonObject;
        }

        public boolean isArray() {
            return this instanceof JsonArray;
        }

        public boolean isString() {
            return this instanceof JsonString;
        }

        public boolean isNumber() {
            return this instanceof JsonNumber;
        }

        public boolean isBoolean() {
            return this instanceof JsonBoolean;
        }

        public boolean isNull() {
            return this instanceof JsonNull;
        }
    }

    @ToString
    @NoArgsConstructor
    public static class JsonObject extends JsonValue {
        private final Map<String, JsonValue> properties = new LinkedHashMap<>();

        public void put(String key, JsonValue value) {
            properties.put(key, value);
        }

        public void put(String key, String value) {
            properties.put(key, new JsonString(value));
        }

        public void put(String key, Number value) {
            properties.put(key, new JsonNumber(value));
        }

        public void put(String key, boolean value) {
            properties.put(key, new JsonBoolean(value));
        }

        /**
         * Fluent builder style.
         */
        public JsonObject with(String key, JsonValue value) {
            put(key, value);
            return this;
        }

        public JsonObject with(String key, String value) {
            put(key, value);
            return this;
        }

        public JsonObject with(String key, Number value) {
            put(key, value);
            return this;
        }

        public JsonObject with(String key, boolean value) {
            put(key, value);
            return this;
        }

        public JsonValue get(@NonNull String key) {
            return properties.get(key);
        }

        public boolean has(@NonNull String key) {
            return properties.containsKey(key);
        }

        public Set<Map.Entry<String, JsonValue>> entrySet() {
            return properties.entrySet();
        }

        /**
         * Typed accessors — return Optional so callers handle absence explicitly.
         */
        public @NonNull Optional<String> getString(String key) {
            JsonValue v = properties.get(key);
            return (v instanceof JsonString s) ? Optional.of(s.getValue()) : Optional.empty();
        }

        public @NonNull Optional<Number> getNumber(String key) {
            JsonValue v = properties.get(key);
            return (v instanceof JsonNumber n) ? Optional.of(n.getValue()) : Optional.empty();
        }

        public @NonNull Optional<Integer> getInt(String key) {
            JsonValue v = properties.get(key);
            return (v instanceof JsonNumber n) ? Optional.of(n.intValue()) : Optional.empty();
        }

        public @NonNull Optional<Long> getLong(String key) {
            JsonValue v = properties.get(key);
            return (v instanceof JsonNumber n) ? Optional.of(n.longValue()) : Optional.empty();
        }

        public @NonNull Optional<Double> getDouble(String key) {
            JsonValue v = properties.get(key);
            return (v instanceof JsonNumber n) ? Optional.of(n.doubleValue()) : Optional.empty();
        }

        public @NonNull Optional<Boolean> getBoolean(String key) {
            JsonValue v = properties.get(key);
            return (v instanceof JsonBoolean b) ? Optional.of(b.getValue()) : Optional.empty();
        }

        public @NonNull Optional<JsonObject> getObject(String key) {
            JsonValue v = properties.get(key);
            return (v instanceof JsonObject o) ? Optional.of(o) : Optional.empty();
        }

        public @NonNull Optional<JsonArray> getArray(String key) {
            JsonValue v = properties.get(key);
            return (v instanceof JsonArray a) ? Optional.of(a) : Optional.empty();
        }

        @Override
        public String toJsonString() {
            var sb = new StringBuilder("{");
            var first = true;
            for (var entry : properties.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append('"').append(Util.escapeJson(entry.getKey())).append('"').append(':');
                sb.append(entry.getValue().toJsonString());
            }
            return sb.append("}").toString();
        }
    }

    @ToString
    @NoArgsConstructor
    public static class JsonArray extends JsonValue {
        private final List<JsonValue> elements = new ArrayList<>();

        public void add(@NonNull JsonValue value) {
            elements.add(value);
        }

        public void add(@NonNull String value) {
            elements.add(new JsonString(value));
        }

        public void add(@NonNull Number value) {
            elements.add(new JsonNumber(value));
        }

        public void add(@NonNull Boolean value) {
            elements.add(new JsonBoolean(value));
        }

        public @NonNull JsonValue get(int index) {
            return elements.get(index);
        }

        public int size() {
            return elements.size();
        }

        public boolean isEmpty() {
            return elements.isEmpty();
        }

        public List<JsonValue> asList() {
            return Collections.unmodifiableList(elements);
        }

        @Override
        public String toJsonString() {
            var sb = new StringBuilder("[");
            var first = true;
            for (var value : elements) {
                if (!first) sb.append(",");
                first = false;
                sb.append(value.toJsonString());
            }
            return sb.append("]").toString();
        }
    }

    @ToString
    @RequiredArgsConstructor
    public static class JsonString extends JsonValue {
        @Getter @NonNull
        private final String value;

        @Override
        public String toJsonString() {
            return '"' + Util.escapeJson(value) + '"';
        }
    }

    @ToString
    @RequiredArgsConstructor
    public static class JsonNumber extends JsonValue {
        @Getter @NonNull
        private final Number value;

        public int intValue() {
            return value.intValue();
        }

        public long longValue() {
            return value.longValue();
        }

        public double doubleValue() {
            return value.doubleValue();
        }

        @Override
        public String toJsonString() {
            double d = doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d))
                return "0";
            return value.toString();
        }
    }

    @ToString
    @RequiredArgsConstructor
    public static class JsonBoolean extends JsonValue {
        @Getter @NonNull
        private final Boolean value;

        @Override
        public String toJsonString() {
            return Boolean.toString(value);
        }
    }

    @ToString
    @NoArgsConstructor
    public static final class JsonNull extends JsonValue {
        public static final JsonNull INSTANCE = new JsonNull();

        @Override
        public String toJsonString() {
            return "null";
        }
    }

    @RequiredArgsConstructor
    private static class JsonParser {
        @NonNull private final String json;
        private int pos = 0;

        JsonValue parse() {
            JsonValue value = parseValue();
            skipWhitespace();
            if (pos < json.length()) {
                log.warn("Trailing content after JSON value at position {}", pos);
            }
            return value;
        }

        private JsonValue parseValue() {
            skipWhitespace();
            if (pos >= json.length()) return JsonNull.INSTANCE;

            return switch (json.charAt(pos)) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 'n' -> parseNull();
                case 't', 'f' -> parseBoolean();
                case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> parseNumber();
                default ->
                        throw new RuntimeException("Unexpected character '" + json.charAt(pos) + "' at position " + pos);
            };
        }

        private JsonObject parseObject() {
            var obj = new JsonObject();
            pos++; // consume '{'
            skipWhitespace();

            if (peek() == '}') {
                pos++;
                return obj;
            }

            while (true) {
                skipWhitespace();
                expect('"', "object key");
                var key = parseString().getValue();
                skipWhitespace();
                expect(':', "':'");
                pos++;
                obj.put(key, parseValue());
                skipWhitespace();
                char c = requireChar("'}' or ','");
                if (c == '}') {
                    pos++;
                    break;
                }
                if (c != ',') throw new RuntimeException("Expected ',' or '}' at position " + pos);
                pos++;
            }
            return obj;
        }

        private JsonArray parseArray() {
            var arr = new JsonArray();
            pos++; // consume '['
            skipWhitespace();

            if (peek() == ']') {
                pos++;
                return arr;
            }

            while (true) {
                arr.add(parseValue());
                skipWhitespace();
                char c = requireChar("']' or ','");
                if (c == ']') {
                    pos++;
                    break;
                }
                if (c != ',') throw new RuntimeException("Expected ',' or ']' at position " + pos);
                pos++;
            }
            return arr;
        }

        private JsonString parseString() {
            pos++; // consume opening '"'
            var sb = new StringBuilder();
            while (pos < json.length()) {
                char c = json.charAt(pos);
                pos++;
                if (c == '"')
                    return new JsonString(sb.toString());
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }

                // We found a backslash - need to process escape sequence
                if (pos >= json.length()) break;
                char esc = json.charAt(pos);
                pos++; // CONSUME the escaped character!

                sb.append(switch (esc) {
                    case '"' -> '"';
                    case '\\' -> '\\';
                    case '/' -> '/';
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case 'u' -> {
                        if (pos + 4 > json.length())  // need 4 hex digits
                            throw new RuntimeException("Incomplete \\u escape at position " + (pos - 1));
                        var hex = json.substring(pos, pos + 4);
                        pos += 4;
                        yield (char) Integer.parseInt(hex, 16);
                    }
                    default -> throw new RuntimeException("Unknown escape '\\" + esc + "' at position " + (pos - 1));
                });
            }
            throw new RuntimeException("Unterminated string starting near position " + pos);
        }

        private JsonNumber parseNumber() {
            int start = pos;
            while (pos < json.length()) {
                char c = json.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '-' || c == '.' || c == 'e' || c == 'E' || c == '+') pos++;
                else break;
            }
            var numStr = json.substring(start, pos);
            try {
                if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E"))
                    return new JsonNumber(Double.parseDouble(numStr));

                try {
                    return new JsonNumber(Integer.parseInt(numStr));
                } catch (NumberFormatException e) {
                    return new JsonNumber(Long.parseLong(numStr));
                }
            } catch (NumberFormatException e) {
                throw new RuntimeException("Invalid number '" + numStr + "' at position " + start);
            }
        }

        private JsonBoolean parseBoolean() {
            if (json.startsWith("true", pos)) {
                pos += 4;
                return new JsonBoolean(true);
            }
            if (json.startsWith("false", pos)) {
                pos += 5;
                return new JsonBoolean(false);
            }
            throw new RuntimeException("Expected boolean at position " + pos);
        }

        private JsonNull parseNull() {
            if (json.startsWith("null", pos)) {
                pos += 4;
                return JsonNull.INSTANCE;
            }
            throw new RuntimeException("Expected null at position " + pos);
        }

        private void skipWhitespace() {
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) pos++;
        }

        private char peek() {
            return pos < json.length() ? json.charAt(pos) : '\0';
        }

        private char requireChar(String context) {
            if (pos >= json.length())
                throw new RuntimeException("Unexpected end of input, expected " + context);
            return json.charAt(pos);
        }

        private void expect(char expected, String context) {
            char actual = requireChar(context);
            if (actual != expected)
                throw new RuntimeException("Expected '" + expected + "' for " + context + " but got '" + actual + "' at position " + pos);
        }
    }
}