package com.nukuhack.modforge.backend.model;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Attributes {
	/**
	 * Discovered at runtime by scanning XML documents.
	 * Concurrent, since we now use multithreading
	 */
	public static final Map<String, Class<?>> TYPE_MAP = new ConcurrentHashMap<>();
	
	static {
		TYPE_MAP.put("buff_params", Attribute.BuffParam.class);
	}
	
	/**
	 * Record the best-guess Java type for an XML attribute name
	 */
	private static Class<?> inferType(final String name, final String value) {
		final String lo = name.toLowerCase();
		if (lo.endsWith("class"))
			return String.class;
		
		if ("true".equals(value) || "false".equals(value))
			return Boolean.class;
		
		// Check for space-separated values
		if (value.contains(" ") && value.matches("^[\\d\\s]+$"))
			return List.class;
		
		try {
			UUID.fromString(value);
			return UUID.class;
		} catch (Exception ignored) {
		}
		
		if (! value.isEmpty()) {
			try {
				Double.parseDouble(value);
				return Double.class;
			} catch (NumberFormatException ignored) {
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
			return new Attribute.StringAttribute(name, "");
		final var v = value;
		final Class<?> type = TYPE_MAP.computeIfAbsent(name, n -> inferType(n, v));
		
		try {
			if (type == UUID.class) {
				return new Attribute.UUIDAttribute(name, UUID.fromString(value));
			} else if (type == String.class) {
				return new Attribute.StringAttribute(name, value);
			} else if (type == Attribute.BuffParam.class) {
				return new Attribute.BuffParamListAttribute(name, value);
			} else if (type == List.class) {
				return new Attribute.ListAttribute<>(name, List.of(value.split("\\s+")));
			} else if (type == Boolean.class) {
				return new Attribute.BooleanAttribute(name, Boolean.parseBoolean(value));
			} else if (type == Double.class) {
				return new Attribute.DoubleAttribute(name, Double.parseDouble(value));
			} else if (type.isEnum()) {
				return new Attribute.EnumAttribute(name, Enum.valueOf((Class<? extends Enum>) type, value));
			}
			throw new IllegalArgumentException("Attribute is not a known type");
		} catch (Exception ex) {
			log.warn("Cannot parse attribute '{}'='{}': {}", name, value, ex.getMessage());
			return new Attribute.StringAttribute(name, value);
		}
	}
}
