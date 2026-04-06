package modforge.backend.model.attributes;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class Attributes {
	private static final Logger log = Logger.getLogger(Attributes.class.getName());
	/**
	 * Discovered at runtime by scanning XML documents.
	 * Concurrent, since we now use multithreading
	 */
	private static final Map<String, Class<?>> TYPE_MAP = new ConcurrentHashMap<>();
	
	static {
		TYPE_MAP.put("buff_params", BuffParam.class);
	}
	
	private Attributes() {
	}
	
	/**
	 * Walk the entire document tree and record the best-guess Java type for
	 * every XML attribute name encountered. Idempotent – safe to call many times.
	 */
	public static void traverseElement(final Element el) {
		final var attrs = el.getAttributes();
		for (int i = 0; i < attrs.getLength(); i++) {
			final var a = (Attr) attrs.item(i);
			String name = a.getLocalName();
			final String value = a.getValue();
			if (name == null)
				continue;
			TYPE_MAP.computeIfAbsent(name, n -> inferType(n, value == null ? "" : value.trim()));
		}
		final var children = el.getChildNodes();
		final int len = children.getLength();
		if (len == 0)
			return;
		for (int i = 0; i < len; i++) {
			if (children.item(i) instanceof Element child)
				traverseElement(child);
		}
	}
	
	private static Class<?> inferType(final String name, final String value) {
		final String lo = name.toLowerCase();
		if (lo.endsWith("id") || lo.endsWith("class"))
			return String.class;
		
		if ("true".equals(value) || "false".equals(value))
			return Boolean.class;
		
		// Check for space-separated values
		if (value.contains(" ") && value.matches("^[\\d\\s]+$"))
			return List.class;
		
		try {
			UUID.fromString(value);
			// TODO : actually save them as UUID
			return String.class;
		} catch (Exception ignored) {
		}
		
		if (! value.isEmpty()) {
			try {
				Double.parseDouble(value);
				return Double.class;
			} catch (final NumberFormatException ignored) {
			}
		}
		
		return String.class;
	}
	
	
	public static <T> String serializeValue(Attribute<T> attr) {
		final T v = attr.getValue();
		if (v == null)
			return "";
		if (attr instanceof List<?> list) {
			if (list.isEmpty())
				return "";
			if (list.getFirst() instanceof String)
				return String.join(",", list.stream().map(Object::toString).toList());
			var sb = new StringBuilder();
			for (var f : list)
				if (f instanceof Attribute<?> a)
					sb.append(serializeValue(a)).append(',');
				else
					log.warning("found list with unsupported type: " + (list.stream().limit(20).toList()) + " type: " + f.getClass());
			return sb.toString();
		}
		return switch (v) {
			case BuffParam b -> b.toAttrString();
			case Enum<?> e -> String.valueOf(e.ordinal());
			case Boolean b -> b.toString().toLowerCase(Locale.ROOT);
			case Double d -> {
				if (Double.isInfinite(d) || Double.isNaN(d))
					yield "-1";
				long rounded = Math.round(d);
				if (Math.abs(d - rounded) < 1e-8)
					yield String.valueOf(rounded);
				yield d.toString();
			}
			default -> v.toString();
		};
	}
	
	/**
	 * Create a typed IAttribute from a raw XML name/value pair.
	 * Falls back to String if the type is not yet discovered.
	 */
	public static Attribute create(final String name, String value) {
		if (value == null || (value = value.trim()).isEmpty())
			return new StringAttribute(name, "");
		final Class<?> type = TYPE_MAP.getOrDefault(name, String.class);
		
		try {
			if (type == BuffParam.class) {
				return new ListAttribute<>(name, BuffParam.parse(value));
			} else if (type == List.class) {
				return new ListAttribute<>(name, List.of(value.split("\\s+")));
			} else if (type == Boolean.class) {
				return new BooleanAttribute(name, Boolean.parseBoolean(value));
			} else if (type == Double.class) {
				return new DoubleAttribute(name, Double.parseDouble(value));
			}
		} catch (Exception ex) {
			log.warning("Cannot parse attribute '" + name + "'='" + value + "': " + ex.getMessage());
		}
		// fallback -> all of it is string
		return new StringAttribute(name, value);
	}
	
	public static Map<String, Class<?>> getTypeMap() {
		return Collections.unmodifiableMap(TYPE_MAP);
	}
}
