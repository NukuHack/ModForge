package com.nukuhack.modforge.backend.model;

import com.nukuhack.modforge.backend.model.E.Enums;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
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
		TYPE_MAP.put("buff_ai_tag_id", E.BuffAiTag.class);
		TYPE_MAP.put("buff_class_id", E.BuffClass.class);
		TYPE_MAP.put("buff_exclusivity_id", E.BuffExclusivity.class);
		TYPE_MAP.put("buff_family_id", E.BuffFamily.class);
		TYPE_MAP.put("buff_lifetime_id", E.BuffLifetime.class);
		TYPE_MAP.put("buff_ui_type_id", E.BuffUiType.class);
		TYPE_MAP.put("buff_ui_visibility_id", E.BuffUiVisibility.class);
		TYPE_MAP.put("stat_selector", E.StatSelector.class);
		TYPE_MAP.put("exclude_in_game_mode", E.ExcludeInGameMode.class);
		TYPE_MAP.put("skill_selector", E.SkillSelector.class);
		TYPE_MAP.put("visibility", E.Visibility.class);
		TYPE_MAP.put("ArmorArchetype", E.ArmorArchetype.class);
		TYPE_MAP.put("AmmoClass", E.AmmoClass.class);
		TYPE_MAP.put("ArmorSurface", E.ArmorSurface.class);
		TYPE_MAP.put("BodyLayerType", E.BodyLayerType.class);
		TYPE_MAP.put("Class", E.WeaponClass.class);
		TYPE_MAP.put("CraftingMaterialSubtype", E.CraftingMaterialSubtype.class);
		TYPE_MAP.put("CraftingMaterialType", E.CraftingMaterialType.class);
		TYPE_MAP.put("DiceBadgeSubtype", E.DiceBadgeSubtype.class);
		TYPE_MAP.put("DiceBadgeType", E.DiceBadgeType.class);
		TYPE_MAP.put("DocumentClass", E.DocumentClass.class);
		TYPE_MAP.put("FoodSubtype", E.FoodSubtype.class);
		TYPE_MAP.put("FoodType", E.FoodType.class);
		TYPE_MAP.put("ItemCategory", E.ItemCategory.class);
		TYPE_MAP.put("ItemTag", E.ItemTag.class);
		TYPE_MAP.put("ItemUiSound", E.ItemUiSound.class);
		TYPE_MAP.put("KeySubtype", E.KeySubtype.class);
		TYPE_MAP.put("KeyType", E.KeyType.class);
		TYPE_MAP.put("MiscSubtype", E.MiscSubtype.class);
		TYPE_MAP.put("MiscType", E.MiscType.class);
		TYPE_MAP.put("NpcToolSubtype", E.NpcToolSubtype.class);
		TYPE_MAP.put("OintmentItemSubtype", E.OintmentItemSubtype.class);
		TYPE_MAP.put("OintmentItemType", E.OintmentItemType.class);
		TYPE_MAP.put("SubClass", E.WeaponSubClass.class);
	}
	
	/**
	 * Record the best-guess Java type for an XML attribute name
	 */
	private static @NonNull Class<?> inferType(final @NonNull String name, final @NonNull String value) {
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
	public static @NonNull Attribute create(final @NonNull String name, @NonNull String value) {
		if ((value = value.trim()).isEmpty())
			return new Attribute.StringAttribute(name, "");
		var v = value;
		var type = TYPE_MAP.computeIfAbsent(name, n -> inferType(n, v));
		
		
		try {
			if (type == UUID.class) {
				return new Attribute.UUIDAttribute(name, UUID.fromString(value));
			} else if (type == String.class) {
				return new Attribute.StringAttribute(name, value);
			} else if (type == Attribute.BuffParam.class) {
				var list = Attribute.BuffParamListAttribute.parse(value);
				if (list.isEmpty())
					throw new IllegalArgumentException("Can not have empty buff-param");
				return new Attribute.BuffParamListAttribute(name, list);
			} else if (type == List.class) {
				return new Attribute.ListAttribute<>(name, List.of(value.split("\\s+")));
			} else if (type == Boolean.class) {
				return new Attribute.BooleanAttribute(name, Boolean.parseBoolean(value));
			} else if (type == Double.class) {
				return new Attribute.DoubleAttribute(name, Double.parseDouble(value));
			} else if (type.isEnum()) {
				int i = 1;
				try {
					i = Integer.parseInt(value);
					return new Attribute.EnumAttribute(name, Enums.fromValueRaw(type, i));
				} catch (Exception e) {
					log.debug("could not parse enum value '{}', for type '{}' - falling back to number", value, type.getSimpleName());
					return new Attribute.DoubleAttribute(name, (double) i);
				}
			}
		} catch (Exception e) {
			log.debug("could not parse value '{}', for type '{}' - falling back to string", value, type.getSimpleName());
			return new Attribute.StringAttribute(name, value);
		}
		
		log.warn("Cannot parse attribute '{}'='{}' - falling back to string", name, value);
		return new Attribute.StringAttribute(name, value);
	}
}
