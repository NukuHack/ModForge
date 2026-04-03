package modforge.backend;

import modforge.backend.model.ModItem;
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
	MISC_TYPE(
			NPCTool.class, MiscItem.class, GameDocument.class, Die.class,
			ItemAlias.class, QuickSlotContainer.class, DiceBadge.class,
			PickableItem.class, Key.class, Money.class, KeyRing.class
	),
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
	// SINGLE SOURCE OF TRUTH - The Giant Master Map
	private static final MasterData MASTER_DATA = new MasterData();
	// Instance fields
	private final Set<Class<? extends ModItem>> itemClasses;

	@SafeVarargs
	ItemType(Class<? extends ModItem>... classes) {
		this.itemClasses = Set.of(classes);
	}

	// Only used to know what files should we look at inside the saved game data
	public static Map<Class<?>, Map<String, String>> endpoints() {
		return MASTER_DATA.getEndpoints();
	}

	// Item selector dropdown inside Item page (frontend)
	public static String[] getAllTypes() {
		return MASTER_DATA.getAllTypes();
	}

	// Item selector dropdown filter for Item page (frontend)
	public static boolean matchesItemType(ModItem item, String selectedType) {
		return MASTER_DATA.matchesItemType(item, selectedType);
	}

	// Item page - Item display frontend
	public static String getIconForClass(Class<? extends ModItem> clazz) {
		return MASTER_DATA.getIconForClass(clazz);
	}

	// first check for items inside xml data
	public static Class<? extends ModItem> getClassFromTableName(String tableName) {
		return MASTER_DATA.getClassFromTableName(tableName);
	}

	private Set<Class<? extends ModItem>> get() {
		return itemClasses;
	}

	// Master Data class - The single source of truth
	private static class MasterData {

		// The giant master maps
		private static final Map<Class<? extends ModItem>, String> itemIcons = new HashMap<>();
		private final Map<String, Class<? extends ModItem>> tableToClass = new HashMap<>();
		private final Map<Class<?>, Map<String, String>> endpoints = new LinkedHashMap<>();
		private final List<ITDisplay> itemDisplays = new ArrayList<>();

		{
			initializeIcons();
			initializeTableMappings();
			initializeEndpoints();
			initializeDisplays();
		}

		private void initializeIcons() {
			// Giant icon mapping
			Map<Class<? extends ModItem>, String> icons = Map.ofEntries(
					Map.entry(Armor.class, "🛡️"),
					Map.entry(Ammo.class, "🎯"),
					Map.entry(MeleeWeapon.class, "⚔️"),
					Map.entry(Helmet.class, "⛑️"),
					Map.entry(Hood.class, "🧢"),
					Map.entry(Food.class, "🍎"),
					Map.entry(Poison.class, "☠️"),
					Map.entry(Herb.class, "🌿"),
					Map.entry(Buff.class, "✨"),
					Map.entry(MissileWeapon.class, "🏹"),
					Map.entry(CraftingMaterial.class, "🔧"),
					Map.entry(MiscItem.class, "📦"),
					Map.entry(Key.class, "🔑"),
					Map.entry(Money.class, "💰"),
					Map.entry(KeyRing.class, "🔗"),
					Map.entry(Perk.class, "⭐")
			);
			itemIcons.putAll(icons);
		}

		private void initializeTableMappings() {
			// Giant table mapping
			Map<String, Class<? extends ModItem>> mappings = Map.ofEntries(
					Map.entry("perks", Perk.class), Map.entry("perk", Perk.class),
					Map.entry("buffs", Buff.class), Map.entry("buff", Buff.class),
					Map.entry("weapons", MeleeWeapon.class), Map.entry("weapon", MeleeWeapon.class),
					Map.entry("armors", Armor.class), Map.entry("armor", Armor.class),
					Map.entry("helmets", Helmet.class), Map.entry("helmet", Helmet.class),
					Map.entry("hoods", Hood.class), Map.entry("hood", Hood.class),
					Map.entry("foods", Food.class), Map.entry("food", Food.class),
					Map.entry("poisons", Poison.class), Map.entry("poison", Poison.class),
					Map.entry("herbs", Herb.class), Map.entry("herb", Herb.class),
					Map.entry("craftingmaterials", CraftingMaterial.class),
					Map.entry("crafting_materials", CraftingMaterial.class),
					Map.entry("craftingmaterial", CraftingMaterial.class),
					Map.entry("crafting_material", CraftingMaterial.class),
					Map.entry("miscitems", MiscItem.class),
					Map.entry("miscitem", MiscItem.class),
					Map.entry("misc", MiscItem.class),
					Map.entry("keys", Key.class), Map.entry("key", Key.class),
					Map.entry("money", Money.class),
					Map.entry("keyrings", KeyRing.class), Map.entry("keyring", KeyRing.class)
			);
			tableToClass.putAll(mappings);
		}

		private void initializeEndpoints() {
			addEndpointsForType(ItemType.PERK, orderedMapOf(
					"perk__combat", TABLES,
					"perk__hardcore", TABLES,
					"perk__kcd2", TABLES,
					"perk__dlc2", TABLES
			));
			addEndpointsForType(ItemType.BUFF, orderedMapOf(
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
			addEndpointsForType(ItemType.WEAPON_CLASS, orderedMapOf(
					"weapon_class", TABLES
			));

			// Add all item type endpoints
			var itemEndpoints = createItemEndpoints();
			for (var type : List.of(ItemType.WEAPON_TYPE, ItemType.ARMOR_TYPE,
					ItemType.CONSUMABLE_TYPE, ItemType.CRAFTING_TYPE,
					ItemType.MISC_TYPE)) {
				addEndpointsForType(type, itemEndpoints);
			}
		}

		private void initializeDisplays() {
			// Giant display list
			itemDisplays.addAll(List.of(
					new ITDisplay("All Types", item -> true),
					new ITDisplay("Melee Weapons", item -> item instanceof MeleeWeapon),
					new ITDisplay("Missile Weapons", item -> item instanceof MissileWeapon),
					new ITDisplay("Ammo", item -> item instanceof Ammo),
					new ITDisplay("Armor", item -> item instanceof Armor),
					new ITDisplay("Helmet", item -> item instanceof Helmet),
					new ITDisplay("Hood", item -> item instanceof Hood),
					new ITDisplay("Food", item -> item instanceof Food),
					new ITDisplay("Poison", item -> item instanceof Poison),
					new ITDisplay("Herb", item -> item instanceof Herb),
					new ITDisplay("Crafting Material", item -> item instanceof CraftingMaterial),
					new ITDisplay("Misc Item", item -> item instanceof MiscItem),
					new ITDisplay("Key", item -> item instanceof Key),
					new ITDisplay("Money", item -> item instanceof Money),
					new ITDisplay("KeyRing", item -> item instanceof KeyRing),
					new ITDisplay("Perk", item -> item instanceof Perk),
					new ITDisplay("Buff", item -> item instanceof Buff)
			));
		}

		private void addEndpointsForType(ItemType type, Map<String, String> typeEndpoints) {
			for (var clazz : type.get()) {
				endpoints.put(clazz, typeEndpoints);
			}
		}

		private Map<String, String> createItemEndpoints() {
			var map = new LinkedHashMap<String, String>();
			List.of(
					"item", "item__alchemy", "item__aux", "item__deprecated",
					"item__dlc", "item__horse", "item__rewards", "item__system", "item__unique"
			).forEach(key -> map.put(key, TABLES));
			return Collections.unmodifiableMap(map);
		}

		private Map<String, String> orderedMapOf(String... kvPairs) {
			if (kvPairs.length % 2 != 0) {
				throw new IllegalArgumentException("Need even number of arguments");
			}

			var map = new LinkedHashMap<String, String>();
			for (int i = 0; i < kvPairs.length; i += 2) {
				map.put(kvPairs[i], kvPairs[i + 1]);
			}
			return Collections.unmodifiableMap(map);
		}

		private Map<Class<?>, Map<String, String>> getEndpoints() {
			return Collections.unmodifiableMap(endpoints);
		}

		private String[] getAllTypes() {
			return itemDisplays.stream()
					       .map(ITDisplay::displayName)
					       .toArray(String[]::new);
		}

		private boolean matchesItemType(ModItem item, String selectedType) {
			if (selectedType == null) return true;

			return itemDisplays.stream()
					       .filter(display -> display.displayName().equals(selectedType))
					       .findFirst()
					       .map(display -> display.matcher().test(item))
					       .orElse(true);
		}

		private String getIconForClass(Class<? extends ModItem> clazz) {
			return itemIcons.getOrDefault(clazz, "📦");
		}

		private Class<? extends ModItem> getClassFromTableName(String tableName) {
			return tableToClass.get(tableName.toLowerCase());
		}
	}

	// Record for type display information
	private record ITDisplay(String displayName, Predicate<ModItem> matcher) {
	}
}