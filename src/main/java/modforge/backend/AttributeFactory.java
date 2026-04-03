package modforge.backend;

import modforge.backend.model.BuffParam;
import modforge.backend.model.MathOperation;
import modforge.backend.model.attributes.*;
import org.w3c.dom.Attr;
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
	 * Discovered at runtime by scanning XML documents.
	 */
	private static final Map<String, Class<?>> TYPE_MAP = new HashMap<>();

	private static final Pattern BUFF_PARAM_RE = Pattern.compile("(\\w+)([+\\-=*%<>!])([\\-+]?\\d+(?:\\.\\d+)?)");

	static {
		TYPE_MAP.put("buff_params", BuffParam.class);
	}

	/**
	 * Walk the entire document tree and record the best-guess Java type for
	 * every XML attribute name encountered. Idempotent – safe to call many times.
	 */
	public static void traverseElement(Element el) {
		var attrs = el.getAttributes();
		for (int i = 0; i < attrs.getLength(); i++) {
			final var a = (Attr) attrs.item(i);
			final String name = a.getLocalName();
			final String value = a.getValue();
			if (name == null || name.isBlank()) continue;
			TYPE_MAP.computeIfAbsent(name, n -> inferType(n, value == null ? "" : value));
		}
		var children = el.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			if (children.item(i) instanceof Element child) traverseElement(child);
		}
	}

	private static Class<?> inferType(String name, String value) {
		if (TYPE_MAP.containsKey(name)) return TYPE_MAP.get(name);

		final String lo = name.toLowerCase(Locale.ROOT);
		if (lo.endsWith("id") || lo.endsWith("class")) return String.class;

		if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))
			return Boolean.class;

		// Check for space-separated values
		if (value.contains(" ") && value.matches("^[\\d\\s]+$"))
			return List.class;

		try {
			UUID.fromString(value);
			return String.class;
		} catch (Exception ignored) {
		}

		if (!value.isBlank()) {
			try {
				Double.parseDouble(value);
				return Double.class;
			} catch (final NumberFormatException ignored) {
			}
		}

		return String.class;
	}

	/**
	 * Create a typed IAttribute from a raw XML name/value pair.
	 * Falls back to String if the type is not yet discovered.
	 */
	public static IAttribute create(final String name, String value) {
		if (value == null) value = "";
		final Class<?> type = TYPE_MAP.getOrDefault(name, String.class);

		try {
			if (type == BuffParam.class) {
				final List<BuffParam> valueNew = parseBuffParams(value);
				return new ListAttribute<>(name, valueNew);
			} else if (type == List.class) {
				final List<String> valueNew = value.isBlank() ? List.of() : List.of(value.trim().split("\\s+"));
				return new ListAttribute<>(name, valueNew);
			} else if (type == Boolean.class) {
				final Boolean valueNew = value.isBlank() ? Boolean.FALSE : Boolean.parseBoolean(value);
				return new BooleanAttribute(name, valueNew);
			} else if (type == Double.class) {
				final Double valueNew =  value.isBlank() ? 0.0 : Double.parseDouble(value);
				return new DoubleAttribute(name, valueNew);
			} else {
				return new StringAttribute(name, value);
			}
		} catch (Exception ex) {
			log.warning("Cannot parse attribute '" + name + "'='" + value + "': " + ex.getMessage());
			return new StringAttribute(name, value);
		}
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
