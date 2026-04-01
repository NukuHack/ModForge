package modforge.backend.service;

import modforge.backend.model.IModItem;
import modforge.backend.model.item.*;

import java.util.*;

public enum ItemType {
	// Define enum constants with their associated class lists and endpoint keys
	WEAPON_CLASS(MeleeWeaponClass.class, MissileWeaponClass.class),
	WEAPON_TYPE(MeleeWeapon.class, MissileWeapon.class, Ammo.class),
	ARMOR_TYPE(Hood.class, Armor.class, Helmet.class),
	CONSUMABLE_TYPE(Food.class, Poison.class),
	CRAFTING_TYPE(Herb.class, CraftingMaterial.class),
	MISC_TYPE(NPCTool.class, MiscItem.class, GameDocument.class, Die.class, ItemAlias.class, QuickSlotContainer.class, DiceBadge.class, PickableItem.class, Key.class, Money.class, KeyRing.class),
	PERK(Perk.class),
	BUFF(Buff.class),
	STORM(Storm.class),
	ALL_ITEM(MeleeWeapon.class, MissileWeapon.class, Ammo.class, Hood.class, Armor.class, Helmet.class, Food.class, Poison.class, Herb.class, CraftingMaterial.class,
			NPCTool.class, MiscItem.class, GameDocument.class, Die.class, ItemAlias.class, QuickSlotContainer.class, DiceBadge.class, PickableItem.class, Key.class, Money.class, KeyRing.class,
			Perk.class, Buff.class, Storm.class);

	private final Set<Class<? extends IModItem>> itemClasses;

	@SafeVarargs
	ItemType(Class<? extends IModItem>... classes) {
		this.itemClasses = Set.of(classes);
	}

	public Set<Class<? extends IModItem>> get() {
		return itemClasses;
	}

	// Static fields
	private static final String TABLES = "Data/Tables.pak";
	private static final String GAMEDATA = "Data/IPL_GameData.pak";

	private static final List<String> ITEM_KEYS = List.of(
			"item", "item__alchemy", "item__aux", "item__deprecated",
			"item__dlc", "item__horse", "item__rewards", "item__system", "item__unique"
	);

	/**
	 * Returns a map of ItemType -> (endpointKey -> pak-relative-path).
	 * Mirrors C# ToolResources.Endpoints().
	 */
	public static Map<Class<?>, Map<String, String>> endpoints() {
		var map = new LinkedHashMap<Class<?>, Map<String, String>>();

		// Handle PERK
		for (var clazz : PERK.get()) {
			map.put(clazz, orderedMapOf(
					"perk__combat", TABLES,
					"perk__hardcore", TABLES,
					"perk__kcd2", TABLES,
					"perk__dlc2", TABLES
			));
		}

		// Handle BUFF
		for (var clazz : BUFF.get()) {
			map.put(clazz, orderedMapOf(
					"buff", TABLES,
					"buff__alchemy", TABLES,
					"buff__perk", TABLES,
					"buff__perk_hardcore", TABLES,
					"buff__perk_kcd1", TABLES,
					"buff__dlc", TABLES,
					"buff__dlc2_beds", TABLES,
					"buff__dlc2_others", TABLES,
					"buff__dlc2_tables", TABLES
			));
		}

		// Handle STORM
		for (var clazz : STORM.get()) {
			map.put(clazz, orderedMapOf("storm.xml", GAMEDATA));
		}

		// Handle WEAPON_CLASS
		var weaponClassEps = orderedMapOf("weapon_class", TABLES);
		for (var clazz : WEAPON_CLASS.get()) {
			map.put(clazz, weaponClassEps);
		}

		// Handle all item types (WEAPON_TYPE, ARMOR_TYPE, etc.)
		var itemEps = itemEndpoints();
		for (var type : List.of(WEAPON_TYPE, ARMOR_TYPE, CONSUMABLE_TYPE, CRAFTING_TYPE, MISC_TYPE)) {
			for (var clazz : type.get()) {
				map.put(clazz, itemEps);
			}
		}

		return Collections.unmodifiableMap(map);
	}

	private static Map<String, String> itemEndpoints() {
		var m = new LinkedHashMap<String, String>();
		ITEM_KEYS.forEach(k -> m.put(k, TABLES));
		return Collections.unmodifiableMap(m);
	}

	/**
	 * Build a String->String LinkedHashMap from alternating key/value varargs.
	 */
	private static Map<String, String> orderedMapOf(String... kvPairs) {
		if (kvPairs.length % 2 != 0) throw new IllegalArgumentException("Need even number of args");
		var m = new LinkedHashMap<String, String>();
		for (int i = 0; i < kvPairs.length; i += 2) {
			m.put(kvPairs[i], kvPairs[i + 1]);
		}
		return Collections.unmodifiableMap(m);
	}
}