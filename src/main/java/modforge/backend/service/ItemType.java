package modforge.backend.service;

import modforge.backend.model.*;
import modforge.backend.model.item.*;

import java.util.*;
import java.util.function.Predicate;

public enum ItemType {
	// Define enum constants with their associated classes
	WEAPON_CLASS(MeleeWeaponClass.class, MissileWeaponClass.class),
	WEAPON_TYPE(MeleeWeapon.class, MissileWeapon.class, Ammo.class),
	ARMOR_TYPE(Hood.class, Armor.class, Helmet.class),
	CONSUMABLE_TYPE(Food.class, Poison.class),
	CRAFTING_TYPE(Herb.class, CraftingMaterial.class),
	MISC_TYPE(NPCTool.class, MiscItem.class, GameDocument.class, Die.class,
			ItemAlias.class, QuickSlotContainer.class, DiceBadge.class,
			PickableItem.class, Key.class, Money.class, KeyRing.class),
	PERK(Perk.class),
	BUFF(Buff.class),
	STORM(Storm.class),
	PERK_BUFF(PerkBuff.class),
	PERK_SCRIPT(PerkScript.class),
	ALL_ITEM(
			MeleeWeapon.class, MissileWeapon.class, Ammo.class,
			Hood.class, Armor.class, Helmet.class,
			Food.class, Poison.class, Herb.class, CraftingMaterial.class,
			NPCTool.class, MiscItem.class, GameDocument.class, Die.class,
			ItemAlias.class, QuickSlotContainer.class, DiceBadge.class,
			PickableItem.class, Key.class, Money.class, KeyRing.class,
			Perk.class, Buff.class, Storm.class
	);

	// Constants
	public static final String TABLES = "Data/Tables.pak";
	public static final String GAMEDATA = "Data/IPL_GameData.pak";

	public static final List<String> ITEM_KEYS = List.of(
			"item", "item__alchemy", "item__aux", "item__deprecated",
			"item__dlc", "item__horse", "item__rewards", "item__system", "item__unique"
	);

	public static final Map<Class<? extends ModItem>, String> ITEM_ICONS = new HashMap<>(20);

	static {
		// map can only be created from up to 10 pairs ... so i have to add them manually
		ITEM_ICONS.put(Armor.class, "🛡️");
		ITEM_ICONS.put(Ammo.class, "🎯");
		ITEM_ICONS.put(MeleeWeapon.class, "⚔️");
		ITEM_ICONS.put(Helmet.class, "⛑️");
		ITEM_ICONS.put(Hood.class, "🧢");
		ITEM_ICONS.put(Food.class, "🍎");
		ITEM_ICONS.put(Poison.class, "☠️");
		ITEM_ICONS.put(Herb.class, "🌿");
		ITEM_ICONS.put(Buff.class, "✨");
		ITEM_ICONS.put(MissileWeapon.class, "🏹");
		ITEM_ICONS.put(CraftingMaterial.class, "🔧");
		ITEM_ICONS.put(MiscItem.class, "📦");
		ITEM_ICONS.put(Key.class, "🔑");
		ITEM_ICONS.put(Money.class, "💰");
		ITEM_ICONS.put(KeyRing.class, "🔗");
		ITEM_ICONS.put(Perk.class, "⭐");
	}

	static final Map<String, Class<? extends ModItem>> TABLE_TO_CLASS = Map.ofEntries(
		Map.entry("perks", Perk.class), Map.entry("perk", Perk.class),
		Map.entry("buffs", Buff.class), Map.entry("buff", Buff.class),
		Map.entry("weapons", MeleeWeapon.class), Map.entry("weapon", MeleeWeapon.class),
		Map.entry("armors", Armor.class), Map.entry("armor", Armor.class),
		Map.entry("helmets", Helmet.class), Map.entry("helmet", Helmet.class),
		Map.entry("hoods", Hood.class), Map.entry("hood", Hood.class),
		Map.entry("foods", Food.class), Map.entry("food", Food.class),
		Map.entry("poisons", Poison.class), Map.entry("poison", Poison.class),
		Map.entry("herbs", Herb.class), Map.entry("herb", Herb.class),
		Map.entry("craftingmaterials", CraftingMaterial.class), Map.entry("crafting_materials", CraftingMaterial.class),
		Map.entry("craftingmaterial", CraftingMaterial.class), Map.entry("crafting_material", CraftingMaterial.class),
		Map.entry("misctems", MiscItem.class), Map.entry("miscitem", MiscItem.class),
		Map.entry("misc", MiscItem.class),
		Map.entry("keys", Key.class), Map.entry("key", Key.class),
		Map.entry("money", Money.class),
		Map.entry("keyrings", KeyRing.class), Map.entry("keyring", KeyRing.class)
	);

	private static final List<ItemTypeDisplay> ITEM_DISPLAYS = List.of(
			new ItemTypeDisplay("All Types", item -> true),
			new ItemTypeDisplay("Melee Weapons", item -> item instanceof MeleeWeapon),
			new ItemTypeDisplay("Missile Weapons", item -> item instanceof MissileWeapon),
			new ItemTypeDisplay("Ammo", item -> item instanceof Ammo),
			new ItemTypeDisplay("Armor", item -> item instanceof Armor),
			new ItemTypeDisplay("Helmet", item -> item instanceof Helmet),
			new ItemTypeDisplay("Hood", item -> item instanceof Hood),
			new ItemTypeDisplay("Food", item -> item instanceof Food),
			new ItemTypeDisplay("Poison", item -> item instanceof Poison),
			new ItemTypeDisplay("Herb", item -> item instanceof Herb),
			new ItemTypeDisplay("Crafting Material", item -> item instanceof CraftingMaterial),
			new ItemTypeDisplay("Misc Item", item -> item instanceof MiscItem),
			new ItemTypeDisplay("Key", item -> item instanceof Key),
			new ItemTypeDisplay("Money", item -> item instanceof Money),
			new ItemTypeDisplay("KeyRing", item -> item instanceof KeyRing),
			new ItemTypeDisplay("Perk", item -> item instanceof Perk),
			new ItemTypeDisplay("Buff", item -> item instanceof Buff)
	);

	// Instance fields
	private final Set<Class<? extends ModItem>> itemClasses;

	@SafeVarargs
	ItemType(Class<? extends ModItem>... classes) {
		this.itemClasses = Set.of(classes);
	}

	public Set<Class<? extends ModItem>> get() {
		return itemClasses;
	}

	// Public API
	public static Map<Class<?>, Map<String, String>> endpoints() {
		var endpoints = new LinkedHashMap<Class<?>, Map<String, String>>();

		// Add PERK endpoints
		addEndpointsForType(PERK, endpoints, createPerkEndpoints());

		// Add BUFF endpoints
		addEndpointsForType(BUFF, endpoints, createBuffEndpoints());

		// Add WEAPON_CLASS endpoints
		addEndpointsForType(WEAPON_CLASS, endpoints, orderedMapOf("weapon_class", TABLES));

		// Add all item type endpoints
		var itemEndpoints = createItemEndpoints();
		for (var type : List.of(WEAPON_TYPE, ARMOR_TYPE, CONSUMABLE_TYPE, CRAFTING_TYPE, MISC_TYPE)) {
			addEndpointsForType(type, endpoints, itemEndpoints);
		}

		return Collections.unmodifiableMap(endpoints);
	}

	public static String[] getAllTypes() {
		return ITEM_DISPLAYS.stream()
				.map(ItemTypeDisplay::displayName)
				.toArray(String[]::new);
	}

	public static boolean matchesItemType(ModItem item, String selectedType) {
		if (selectedType == null) return true;

		return ITEM_DISPLAYS.stream()
				.filter(display -> display.displayName().equals(selectedType))
				.findFirst()
				.map(display -> display.matcher().test(item))
				.orElse(true);
	}

	// Private helper methods
	private static void addEndpointsForType(ItemType type, Map<Class<?>, Map<String, String>> endpoints, Map<String, String> typeEndpoints) {
		for (var clazz : type.get()) {
			endpoints.put(clazz, typeEndpoints);
		}
	}

	private static Map<String, String> createPerkEndpoints() {
		return orderedMapOf(
				"perk__combat", TABLES,
				"perk__hardcore", TABLES,
				"perk__kcd2", TABLES,
				"perk__dlc2", TABLES
		);
	}

	private static Map<String, String> createBuffEndpoints() {
		return orderedMapOf(
				"buff", TABLES,
				"buff__alchemy", TABLES,
				"buff__perk", TABLES,
				"buff__perk_hardcore", TABLES,
				"buff__perk_kcd1", TABLES,
				"buff__dlc", TABLES,
				"buff__dlc2_beds", TABLES,
				"buff__dlc2_others", TABLES,
				"buff__dlc2_tables", TABLES
		);
	}

	private static Map<String, String> createItemEndpoints() {
		var map = new LinkedHashMap<String, String>();
		ITEM_KEYS.forEach(key -> map.put(key, TABLES));
		return Collections.unmodifiableMap(map);
	}

	private static Map<String, String> orderedMapOf(String... kvPairs) {
		if (kvPairs.length % 2 != 0) {
			throw new IllegalArgumentException("Need even number of arguments");
		}

		var map = new LinkedHashMap<String, String>();
		for (int i = 0; i < kvPairs.length; i += 2) {
			map.put(kvPairs[i], kvPairs[i + 1]);
		}
		return Collections.unmodifiableMap(map);
	}

	// Record for type display information
	private record ItemTypeDisplay(String displayName, Predicate<ModItem> matcher) {}
}