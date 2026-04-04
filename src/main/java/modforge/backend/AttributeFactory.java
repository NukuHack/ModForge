package modforge.backend;

import modforge.backend.model.attributes.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class AttributeFactory {
	private static final Logger log = Logger.getLogger(AttributeFactory.class.getName());
	/**
	 * Discovered at runtime by scanning XML documents.
	 * Concurrent, since we now use multithreading
	 */
	private static final Map<String, Class<?>> TYPE_MAP = new ConcurrentHashMap<>();
	
	static {
		TYPE_MAP.put("buff_params", BuffParam.class);
	}
	
	private AttributeFactory() {
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
