package modforge.backend;

import modforge.backend.model.Attribute;
import modforge.backend.model.BuffParam;
import modforge.backend.model.IAttribute;
import modforge.backend.model.MathOperation;
import org.w3c.dom.Element;

import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class AttributeFactory {
	private static final Logger log = Logger.getLogger(AttributeFactory.class.getName());

	private AttributeFactory() {
	}

	/**
	 * Types we know upfront – extend as needed to mirror C# knownAttributeEnums.
	 */
	private static final Map<String, Class<?>> KNOWN_TYPES = new HashMap<>();

	/**
	 * Discovered at runtime by scanning XML documents.
	 */
	private static final Map<String, Class<?>> TYPE_MAP = new HashMap<>();

	private static final Pattern BUFF_PARAM_RE = Pattern.compile("(\\w+)([+\\-=*%<>!])([\\-+]?\\d+(?:\\.\\d+)?)");

	static {
		KNOWN_TYPES.put("buff_params", List.class);
	}

	/**
	 * Walk the entire document tree and record the best-guess Java type for
	 * every XML attribute name encountered. Idempotent – safe to call many times.
	 */
	public static void discoverTypes(Element root) {
		traverseElement(root);
	}

	private static void traverseElement(Element el) {
		var attrs = el.getAttributes();
		for (int i = 0; i < attrs.getLength(); i++) {
			var a = (org.w3c.dom.Attr) attrs.item(i);
			String name = a.getLocalName();
			String value = a.getValue();
			if (name == null || name.isBlank()) continue;
			TYPE_MAP.computeIfAbsent(name, n -> inferType(n, value == null ? "" : value));
		}
		var children = el.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			if (children.item(i) instanceof Element child) traverseElement(child);
		}
	}

	private static Class<?> inferType(String name, String value) {
		if (KNOWN_TYPES.containsKey(name)) return KNOWN_TYPES.get(name);

		String lo = name.toLowerCase(Locale.ROOT);
		if (lo.endsWith("id")) return String.class;

		try {
			UUID.fromString(value);
			return String.class;
		} catch (Exception ignored) {
		}

		if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))
			return Boolean.class;

		if (!value.isBlank()) {
			try {
				Double.parseDouble(value);
				return Double.class;
			} catch (Exception ignored) {
			}
		}

		return String.class;
	}

	/**
	 * Create a typed IAttribute from a raw XML name/value pair.
	 * Falls back to String if the type is not yet discovered.
	 */
	public static IAttribute create(String name, String rawValue) {
		Class<?> type = TYPE_MAP.getOrDefault(name, String.class);
		Object value;

		try {
			if (type == List.class) {
				value = parseBuffParams(rawValue == null ? "" : rawValue);
			} else if (type == Boolean.class) {
				value = rawValue == null || rawValue.isBlank()
						? Boolean.FALSE : Boolean.parseBoolean(rawValue);
			} else if (type == Double.class) {
				value = rawValue == null || rawValue.isBlank()
						? 0.0 : Double.parseDouble(rawValue);
			} else {
				value = rawValue == null ? "" : rawValue;
			}
		} catch (Exception ex) {
			log.warning("Cannot parse attribute '" + name + "'='" + rawValue + "': " + ex.getMessage());
			value = rawValue == null ? "" : rawValue;
		}

		return new Attribute<>(name, value);
	}

	/**
	 * Parse the compact game format:  "Strength+5,Agility-2.5,Charisma=10"
	 */
	public static List<BuffParam> parseBuffParams(String raw) {
		var list = new ArrayList<BuffParam>();
		if (raw == null || raw.isBlank()) return list;

		for (String part : raw.split(",")) {
			String t = part.trim();
			if (t.isEmpty()) continue;
			var m = BUFF_PARAM_RE.matcher(t);
			if (m.find()) {
				list.add(new BuffParam(
						m.group(1),
						MathOperation.fromSymbol(m.group(2)),
						Double.parseDouble(m.group(3))
				));
			} else {
				list.add(new BuffParam(t, MathOperation.SET_ABSOLUTE, 1));
			}
		}
		return list;
	}

	/**
	 * Return a default (empty-value) attribute for every discovered name.
	 */
	public static List<IAttribute> getAllKnown() {
		return TYPE_MAP.keySet().stream()
				.map(k -> create(k, ""))
				.collect(Collectors.toList());
	}

	public static Map<String, Class<?>> getTypeMap() {
		return Collections.unmodifiableMap(TYPE_MAP);
	}
}
