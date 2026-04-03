package modforge.backend;

import modforge.backend.model.ModItem;
import modforge.backend.model.item.*;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public enum ItemType {
	// Define enum constants with their associated classes
	WEAPON_CLASS(MeleeWeaponClass.class, MissileWeaponClass.class), WEAPON_TYPE(MeleeWeapon.class, MissileWeapon.class, Ammo.class), ARMOR_TYPE(Hood.class, Armor.class, Helmet.class), CONSUMABLE_TYPE(Food.class, Poison.class), CRAFTING_TYPE(Herb.class, CraftingMaterial.class), MISC_TYPE(NPCTool.class, MiscItem.class, GameDocument.class, Die.class, ItemAlias.class, QuickSlotContainer.class, DiceBadge.class, PickableItem.class, Key.class, Money.class, KeyRing.class), PERK(Perk.class), BUFF(Buff.class), STORM(Storm.class), PERK_BUFF(PerkBuff.class), PERK_SCRIPT(PerkScript.class), ALL_ITEM(MeleeWeapon.class, MissileWeapon.class, Ammo.class, Hood.class, Armor.class, Helmet.class, Food.class, Poison.class, Herb.class, CraftingMaterial.class, NPCTool.class, MiscItem.class, GameDocument.class, Die.class, ItemAlias.class, QuickSlotContainer.class, DiceBadge.class, PickableItem.class, Key.class, Money.class, KeyRing.class, Perk.class, Buff.class, Storm.class);
	
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
	
	public static Set<String> endpointSet() {
		return MASTER_DATA.endpointSet();
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
	public static Class<? extends ModItem> getClassFromTableName(final String tableName) {
		return MASTER_DATA.getClassFromTableName(tableName.toLowerCase(Locale.ROOT).trim().replace(" ", "").replace("_", ""));
	}
	
	/**
	 * The XML attribute name used as the primary ID for a given item class.
	 */
	public static String getIdKey(Class<? extends ModItem> clazz) {
		return MASTER_DATA.getIdKey(clazz);
	}
	
	/**
	 * Ordered list of (class, idAttrKey) pairs – the single source of truth
	 * consumed by {@code ModItemBuilder.createDefault()} to build its handler list.
	 */
	public static List<HandlerSpec> getHandlerSpecs() {
		return MASTER_DATA.getHandlerSpecs();
	}
	
	private Set<Class<? extends ModItem>> get() {
		return itemClasses;
	}
	
	/**
	 * Pair of a concrete item class and the XML attribute name that holds its ID.
	 */
	public record HandlerSpec(Class<? extends ModItem> clazz, String idKey) {
	}
	
	// Master Data class - The single source of truth
	private static class MasterData {
		
		// The giant master maps
		private static final Map<Class<? extends ModItem>, String> itemIcons = new HashMap<>();
		private static final Set<Class<? extends ModItem>> exclude = Set.of(MeleeWeaponClass.class, MissileWeaponClass.class, PerkBuff.class, PerkScript.class, Storm.class);
		private final Map<String, Class<? extends ModItem>> tableToClass = new HashMap<>();
		private final Map<Class<?>, Map<String, String>> endpoints = new LinkedHashMap<>();
		private final List<ITDisplay> itemDisplays = new ArrayList<>();
		/**
		 * Class → XML id-attribute name; drives both icon lookup and builder handler list.
		 */
		private final Map<Class<? extends ModItem>, String> idKeys = new LinkedHashMap<>();
		/**
		 * Stable, ordered list consumed by ModItemBuilder.createDefault().
		 */
		private final List<HandlerSpec> handlerSpecs = new ArrayList<>();
		
		{
			initializeIcons();
			initializeTableMappings();
			initializeEndpoints();
			initializeDisplays();
			initializeIdKeys();
		}
		
		private void initializeIcons() {
			// Giant icon mapping
			Map<Class<? extends ModItem>, String> icons = Map.ofEntries(Map.entry(Armor.class, "🛡️"), Map.entry(Ammo.class, "🎯"), Map.entry(MeleeWeapon.class, "⚔️"), Map.entry(Helmet.class, "⛑️"), Map.entry(Hood.class, "🧢"), Map.entry(Food.class, "🍎"), Map.entry(Poison.class, "☠️"), Map.entry(Herb.class, "🌿"), Map.entry(Buff.class, "✨"), Map.entry(MissileWeapon.class, "🏹"), Map.entry(CraftingMaterial.class, "🔧"), Map.entry(MiscItem.class, "📦"), Map.entry(Key.class, "🔑"), Map.entry(Money.class, "💰"), Map.entry(KeyRing.class, "🔗"), Map.entry(Perk.class, "⭐"));
			itemIcons.putAll(icons);
		}
		
		private void initializeTableMappings() {
			// Giant table mapping
			Map<String, Class<? extends ModItem>> mappings = Map.<String, Class<? extends ModItem>>ofEntries(Map.entry("ammo", Ammo.class), Map.entry("armor", Armor.class), Map.entry("armors", Armor.class), Map.entry("buff", Buff.class), Map.entry("buffs", Buff.class), Map.entry("craftingmaterial", CraftingMaterial.class), Map.entry("craftingmaterials", CraftingMaterial.class), Map.entry("dicebadge", DiceBadge.class), Map.entry("dicebadges", DiceBadge.class), Map.entry("die", Die.class), Map.entry("dice", Die.class), Map.entry("food", Food.class), Map.entry("foods", Food.class), Map.entry("gamedocument", GameDocument.class), Map.entry("gamedocuments", GameDocument.class), Map.entry("helmet", Helmet.class), Map.entry("helmets", Helmet.class), Map.entry("herb", Herb.class), Map.entry("herbs", Herb.class), Map.entry("hood", Hood.class), Map.entry("hoods", Hood.class), Map.entry("itemalias", ItemAlias.class), Map.entry("itemaliases", ItemAlias.class), Map.entry("key", Key.class), Map.entry("keys", Key.class), Map.entry("keyring", KeyRing.class), Map.entry("keyrings", KeyRing.class), Map.entry("meleeweapon", MeleeWeapon.class), Map.entry("meleeweapons", MeleeWeapon.class), Map.entry("meleeweaponclass", MeleeWeaponClass.class), Map.entry("meleeweaponclasses", MeleeWeaponClass.class), Map.entry("misc", MiscItem.class), Map.entry("miscitem", MiscItem.class), Map.entry("miscitems", MiscItem.class), Map.entry("missileweapon", MissileWeapon.class), Map.entry("missileweapons", MissileWeapon.class), Map.entry("missileweaponclass", MissileWeaponClass.class), Map.entry("missileweaponclasses", MissileWeaponClass.class), Map.entry("money", Money.class), Map.entry("npctool", NPCTool.class), Map.entry("npctools", NPCTool.class), Map.entry("perk", Perk.class), Map.entry("perks", Perk.class), Map.entry("perkbuff", PerkBuff.class), Map.entry("perkbuffs", PerkBuff.class), Map.entry("perkscript", PerkScript.class), Map.entry("perkscripts", PerkScript.class), Map.entry("pickableitem", PickableItem.class), Map.entry("pickableitems", PickableItem.class), Map.entry("poison", Poison.class), Map.entry("poisons", Poison.class), Map.entry("quickslotcontainer", QuickSlotContainer.class), Map.entry("quickslotcontainers", QuickSlotContainer.class), Map.entry("storm", Storm.class), Map.entry("storms", Storm.class), Map.entry("weapon", MeleeWeapon.class), Map.entry("weapons", MeleeWeapon.class));
			tableToClass.putAll(mappings);
		}
		
		private void initializeEndpoints() {
			addEndpointsForType(ItemType.PERK, orderedMapOf("perk", TABLES, "perk__combat", TABLES, "perk__hardcore", TABLES, "perk__kcd2", TABLES, "perk__dlc2", TABLES));
			addEndpointsForType(ItemType.BUFF, orderedMapOf("buff", TABLES, "buff__alchemy", TABLES, "buff__perk", TABLES, "buff__perk_hardcore", TABLES, "buff__perk_kcd1", TABLES, "buff__dlc", TABLES, "buff__dlc2_beds", TABLES, "buff__dlc2_others", TABLES, "buff__dlc2_tables", TABLES));
			// perk_buff_overrides, perk_soul_abilitys
			addEndpointsForType(ItemType.WEAPON_CLASS, orderedMapOf("weapon_class", TABLES));
			addEndpointsForType(ItemType.PERK_BUFF, orderedMapOf("perk_buff", TABLES));
			addEndpointsForType(ItemType.PERK_SCRIPT, orderedMapOf("perk_script", TABLES));
			
			// Add all item type endpoints
			var itemEndpoints = createItemEndpoints();
			for (var type : List.of(ItemType.WEAPON_TYPE, ItemType.ARMOR_TYPE, ItemType.CONSUMABLE_TYPE, ItemType.CRAFTING_TYPE, ItemType.MISC_TYPE)) {
				addEndpointsForType(type, itemEndpoints);
			}
		}
		
		private void initializeDisplays() {
			itemDisplays.add(new ITDisplay("All Types", _ -> true));
			
			// Get all unique classes from ALL_ITEM and other type enums
			final Set<Class<? extends ModItem>> allClasses = ItemType.ALL_ITEM.get();
			
			// Generate displays dynamically
			allClasses.stream().filter(this::shouldIncludeInDisplay).map(this::createDisplayForClass).sorted(Comparator.comparing(ITDisplay::displayName)).forEach(itemDisplays::add);
		}
		
		private boolean shouldIncludeInDisplay(final Class<?> clazz) {
			// Filter out classes you don't want in the dropdown
			return ! exclude.contains(clazz);
		}
		
		private ITDisplay createDisplayForClass(final Class<?> clazz) {
			return new ITDisplay(formatDisplayName(clazz.getSimpleName()), clazz::isInstance);
		}
		
		private String formatDisplayName(final String className) {
			// Insert space before each uppercase letter that has a lowercase letter after it
			// "MeleeWeapon" -> "Melee Weapon"
			// "CraftingMaterial" -> "Crafting Material"
			// "KeyRing" -> "Key Ring"
			// "NPC" -> "NPC" (stays as is since no lowercase after)
			String withSpaces = className.replaceAll("(?<=[A-Z])(?=[A-Z][a-z])|(?<=[a-z])(?=[A-Z])", " ");
			
			// Handle special cases like "NPCTool" -> "NPC Tool"
			// This handles consecutive uppercase letters followed by a word
			withSpaces = withSpaces.replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
			
			// Handle "DiceBadge" -> "Dice Badge" (already handled by first regex)
			// Handle "QuickSlotContainer" -> "Quick Slot Container"
			
			return withSpaces;
		}
		
		private void addEndpointsForType(ItemType type, Map<String, String> typeEndpoints) {
			for (var clazz : type.get()) {
				endpoints.put(clazz, typeEndpoints);
			}
		}
		
		private Map<String, String> createItemEndpoints() {
			var map = new LinkedHashMap<String, String>();
			List.of("item", "item__alchemy", "item__aux", "item__deprecated", "item__dlc", "item__horse", "item__rewards", "item__system", "item__unique").forEach(key -> map.put(key, TABLES));
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
		
		private Set<String> endpointSet() {
			return endpoints.values().stream().map(Map::keySet).flatMap(Collection::stream).collect(Collectors.toSet());
		}
		
		private String[] getAllTypes() {
			return itemDisplays.stream().map(ITDisplay::displayName).toArray(String[]::new);
		}
		
		private boolean matchesItemType(ModItem item, String selectedType) {
			if (selectedType == null)
				return true;
			
			return itemDisplays.stream().filter(display -> display.displayName().equals(selectedType)).findFirst().map(display -> display.matcher().test(item)).orElse(true);
		}
		
		private String getIconForClass(Class<? extends ModItem> clazz) {
			return itemIcons.getOrDefault(clazz, "📦");
		}
		
		private Class<? extends ModItem> getClassFromTableName(String tableName) {
			return tableToClass.get(tableName.toLowerCase());
		}
		
		/**
		 * Single source of truth for every (class, idAttrKey) pair.
		 * Order here determines handler-priority in ModItemBuilder.
		 */
		private void initializeIdKeys() {
			reg(Ammo.class, "Id");
			reg(Armor.class, "Id");
			reg(Buff.class, "buff_id");
			reg(CraftingMaterial.class, "Id");
			reg(DiceBadge.class, "Id");
			reg(Die.class, "Id");
			reg(Food.class, "Id");
			reg(GameDocument.class, "Id");
			reg(Helmet.class, "Id");
			reg(Herb.class, "Id");
			reg(Hood.class, "Id");
			reg(ItemAlias.class, "Id");
			reg(Key.class, "Id");
			reg(KeyRing.class, "Id");
			reg(MeleeWeapon.class, "Id");
			reg(MeleeWeaponClass.class, "id");
			reg(MiscItem.class, "Id");
			reg(MissileWeapon.class, "Id");
			reg(MissileWeaponClass.class, "id");
			reg(Money.class, "Id");
			reg(NPCTool.class, "Id");
			reg(Perk.class, "perk_id");
			reg(PerkBuff.class, "perk_id");
			reg(PerkScript.class, "perk_id");
			reg(PickableItem.class, "Id");
			reg(Poison.class, "Id");
			reg(QuickSlotContainer.class, "Id");
			reg(Storm.class, "id");
		}
		
		private void reg(Class<? extends ModItem> clazz, String idKey) {
			idKeys.put(clazz, idKey);
			handlerSpecs.add(new HandlerSpec(clazz, idKey));
		}
		
		private String getIdKey(Class<? extends ModItem> clazz) {
			return idKeys.getOrDefault(clazz, "Id");
		}
		
		private List<HandlerSpec> getHandlerSpecs() {
			return Collections.unmodifiableList(handlerSpecs);
		}
	}
	
	// Record for type display information
	private record ITDisplay(String displayName, Predicate<ModItem> matcher) {
	}
}