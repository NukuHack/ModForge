package modforge.backend;

import modforge.Singleton;
import modforge.Util;
import modforge.backend.model.ModItem;
import modforge.backend.model.item.*;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public enum ItemType {
	// Define enum constants with their associated classes
	WEAPON_CLASS(MeleeWeaponClass.class, MissileWeaponClass.class),
	WEAPON_TYPE(MeleeWeapon.class, MissileWeapon.class, Ammo.class),
	ARMOR_TYPE(Hood.class, Armor.class, Helmet.class),
	CONSUMABLE_TYPE(Food.class, Poison.class),
	CRAFTING_TYPE(Herb.class, CraftingMaterial.class),
	MISC_TYPE(NPCTool.class, MiscItem.class, GameDocument.class, Die.class, ItemAlias.class, QuickSlotContainer.class,
			DiceBadge.class, PickableItem.class, Key.class, Money.class, KeyRing.class), PERK(Perk.class),
	BUFF(Buff.class),
	STORM(Storm.class),
	PERK_BUFF(PerkBuff.class),
	PERK_SCRIPT(PerkScript.class),
	ALL_ITEM(MeleeWeapon.class, MissileWeapon.class, Ammo.class, Hood.class, Armor.class, Helmet.class, Food.class, Poison.class, Herb.class,
			CraftingMaterial.class, NPCTool.class, MiscItem.class, GameDocument.class, Die.class, ItemAlias.class, QuickSlotContainer.class,
			DiceBadge.class, PickableItem.class, Key.class, Money.class, KeyRing.class, Perk.class, Buff.class, Storm.class);
	
	// Constants
	public static final String TABLES = "Data/Tables.pak";
	public static final String GAMEDATA = "Data/IPL_GameData.pak";
	
	// Static master data instance
	private static final MasterData MASTER_DATA = new MasterData();
	
	// Instance fields
	private final Set<Class<? extends ModItem>> itemClasses;
	
	@SafeVarargs
	ItemType(Class<? extends ModItem>... classes) {
		this.itemClasses = Set.of(classes);
	}
	
	public static Set<String> endpointSet() {
		return MASTER_DATA.endpointSet();
	}
	
	// Item selector dropdown inside Item page (frontend)
	public static Set<String> getAllTypes() {
		return MASTER_DATA.getAllTypes();
	}
	
	// Item selector dropdown filter for Item page (frontend)
	public static Predicate<ModItem> matchesItemType(String selectedType) {
		return MASTER_DATA.matchesItemType(selectedType);
	}
	
	// Item page - Item display frontend
	public static String getIconForClass(Class<? extends ModItem> clazz) {
		return MASTER_DATA.getIconForClass(clazz);
	}
	
	// first check for items inside XML data
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
	
	// Only used to know what files should we look at inside the saved game data
	public static Set<DataPoint> dataPoints(final String gameDir) {
		final Set<DataPoint> points = new HashSet<>();
		MASTER_DATA.getEndpoints().forEach((type, e) ->
			points.add(new DataPoint(Util.join(gameDir, e.getValue()), e.getKey(), type))
		);
		return points;
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
		private static final Map<Class<? extends ModItem>, String> ITEM_ICONS = initializeIcons();
		
		private final Map<String, Class<? extends ModItem>> tableToClass;
		private final Map<Class<?>, Map.Entry<String, String>> endpoints;
		private final Set<ITDisplay> itemDisplays;
		private final Map<Class<? extends ModItem>, String> idKeys;
		private final List<HandlerSpec> handlerSpecs;
		
		private MasterData() {}
		
		{
			this.tableToClass = initializeTableMappings();
			this.endpoints = initializeEndpoints();
			this.idKeys = new LinkedHashMap<>();
			this.handlerSpecs = new ArrayList<>();
			initializeIdKeys();
			this.itemDisplays = new LinkedHashSet<>(initializeDisplays());
		}
		
		private static Map<Class<? extends ModItem>, String> initializeIcons() {
			// Giant icon mapping
			final var icons = new HashMap<Class<? extends ModItem>, String>();
			icons.put(Armor.class, "🛡️");
			icons.put(Ammo.class, "🎯");
			icons.put(MeleeWeapon.class, "⚔️");
			icons.put(Helmet.class, "⛑️");
			icons.put(Hood.class, "🧢");
			icons.put(Food.class, "🍎");
			icons.put(Poison.class, "☠️");
			icons.put(Herb.class, "🌿");
			icons.put(Buff.class, "✨");
			icons.put(MissileWeapon.class, "🏹");
			icons.put(CraftingMaterial.class, "🔧");
			icons.put(MiscItem.class, "📦");
			icons.put(Key.class, "🔑");
			icons.put(Money.class, "💰");
			icons.put(KeyRing.class, "🔗");
			icons.put(Perk.class, "⭐");
			return Collections.unmodifiableMap(icons);
		}
		
		private static Map<String, Class<? extends ModItem>> initializeTableMappings() {
			// Giant table mapping
			Map<String, Class<? extends ModItem>> mappings = new HashMap<>();
			mappings.put("ammo", Ammo.class);
			mappings.put("armor", Armor.class);
			mappings.put("armors", Armor.class);
			mappings.put("buff", Buff.class);
			mappings.put("buffs", Buff.class);
			mappings.put("craftingmaterial", CraftingMaterial.class);
			mappings.put("craftingmaterials", CraftingMaterial.class);
			mappings.put("dicebadge", DiceBadge.class);
			mappings.put("dicebadges", DiceBadge.class);
			mappings.put("die", Die.class);
			mappings.put("dice", Die.class);
			mappings.put("food", Food.class);
			mappings.put("foods", Food.class);
			mappings.put("gamedocument", GameDocument.class);
			mappings.put("gamedocuments", GameDocument.class);
			mappings.put("helmet", Helmet.class);
			mappings.put("helmets", Helmet.class);
			mappings.put("herb", Herb.class);
			mappings.put("herbs", Herb.class);
			mappings.put("hood", Hood.class);
			mappings.put("hoods", Hood.class);
			mappings.put("itemalias", ItemAlias.class);
			mappings.put("itemaliases", ItemAlias.class);
			mappings.put("key", Key.class);
			mappings.put("keys", Key.class);
			mappings.put("keyring", KeyRing.class);
			mappings.put("keyrings", KeyRing.class);
			mappings.put("meleeweapon", MeleeWeapon.class);
			mappings.put("meleeweapons", MeleeWeapon.class);
			mappings.put("meleeweaponclass", MeleeWeaponClass.class);
			mappings.put("meleeweaponclasses", MeleeWeaponClass.class);
			mappings.put("misc", MiscItem.class);
			mappings.put("miscitem", MiscItem.class);
			mappings.put("miscitems", MiscItem.class);
			mappings.put("missileweapon", MissileWeapon.class);
			mappings.put("missileweapons", MissileWeapon.class);
			mappings.put("missileweaponclass", MissileWeaponClass.class);
			mappings.put("missileweaponclasses", MissileWeaponClass.class);
			mappings.put("money", Money.class);
			mappings.put("npctool", NPCTool.class);
			mappings.put("npctools", NPCTool.class);
			mappings.put("perk", Perk.class);
			mappings.put("perks", Perk.class);
			mappings.put("perkbuff", PerkBuff.class);
			mappings.put("perkbuffs", PerkBuff.class);
			mappings.put("perkscript", PerkScript.class);
			mappings.put("perkscripts", PerkScript.class);
			mappings.put("pickableitem", PickableItem.class);
			mappings.put("pickableitems", PickableItem.class);
			mappings.put("poison", Poison.class);
			mappings.put("poisons", Poison.class);
			mappings.put("quickslotcontainer", QuickSlotContainer.class);
			mappings.put("quickslotcontainers", QuickSlotContainer.class);
			mappings.put("storm", Storm.class);
			mappings.put("storms", Storm.class);
			mappings.put("weapon", MeleeWeapon.class);
			mappings.put("weapons", MeleeWeapon.class);
			return Collections.unmodifiableMap(mappings);
		}
		
		private Map<Class<?>, Map.Entry<String, String>> initializeEndpoints() {
			Map<Class<?>, Map.Entry<String, String>> temp = new HashMap<>();
			
			// TODO :  perk_buff_overrides, perk_soul_abilitys, rpg_params
			temp.put(Perk.class, new AbstractMap.SimpleEntry<>("perk", TABLES));
			temp.put(Buff.class, new AbstractMap.SimpleEntry<>("buff", TABLES));
			temp.put(MeleeWeaponClass.class, new AbstractMap.SimpleEntry<>("weapon_class", TABLES));
			temp.put(MissileWeaponClass.class, new AbstractMap.SimpleEntry<>("weapon_class", TABLES));
			temp.put(PerkBuff.class, new AbstractMap.SimpleEntry<>("perk_buff", TABLES));
			temp.put(PerkScript.class, new AbstractMap.SimpleEntry<>("perk_script", TABLES));
			
			// Add all item type endpoints
			for (Class<? extends ModItem> itemClass : ItemType.ALL_ITEM.get()) {
				temp.putIfAbsent(itemClass, new AbstractMap.SimpleEntry<>("item", TABLES));
			}
			
			return Collections.unmodifiableMap(temp);
		}
		
		private List<ITDisplay> initializeDisplays() {
			final List<ITDisplay> displays = new ArrayList<>();
			displays.add(new ITDisplay("All Types", _ -> true));
			
			// Get all unique classes from ALL_ITEM
			final Set<Class<? extends ModItem>> allClasses = Arrays.stream(ItemType.values()).map(ItemType::get).flatMap(Collection::stream).collect(Collectors.toSet());
			
			// Generate displays dynamically
			allClasses.stream()
					.filter(this::shouldIncludeInDisplay)
					.map(this::createDisplayForClass)
					.sorted(Comparator.comparing(ITDisplay::displayName))
					.forEach(displays::add);
			
			return Collections.unmodifiableList(displays);
		}
		
		private boolean shouldIncludeInDisplay(final Class<?> clazz) {
			// Filter out classes you don't want in the dropdown
			return MeleeWeaponClass.class != clazz && clazz != MissileWeaponClass.class;
		}
		
		private ITDisplay createDisplayForClass(final Class<?> clazz) {
			return new ITDisplay(formatDisplayName(clazz.getSimpleName()), clazz::isInstance);
		}
		
		private String formatDisplayName(final String className) {
			// Insert space before each uppercase letter that has a lowercase letter after it
			String withSpaces = className.replaceAll("(?<=[A-Z])(?=[A-Z][a-z])|(?<=[a-z])(?=[A-Z])", " ");
			
			// Handle special cases like "NPCTool" -> "NPC Tool"
			withSpaces = withSpaces.replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
			
			return withSpaces;
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
		
		public Map<Class<?>, Map.Entry<String, String>> getEndpoints() {
			return endpoints;
		}
		
		public Set<String> endpointSet() {
			return endpoints.values().stream().map(Map.Entry::getKey).collect(Collectors.toSet());
		}
		
		public Set<String> getAllTypes() {
			return itemDisplays.stream().map(ITDisplay::displayName).collect(Collectors.toCollection(LinkedHashSet::new));
		}
		
		public Predicate<ModItem> matchesItemType(String selectedType) {
			if (selectedType == null)
				return null;
			
			return itemDisplays.stream().filter(display -> display.displayName().equals(selectedType)).findFirst().map(ITDisplay::matcher).orElse(null);
		}
		
		public String getIconForClass(Class<? extends ModItem> clazz) {
			return ITEM_ICONS.getOrDefault(clazz, "📦");
		}
		
		public Class<? extends ModItem> getClassFromTableName(String tableName) {
			return tableToClass.get(tableName.toLowerCase());
		}
		
		public String getIdKey(Class<? extends ModItem> clazz) {
			return idKeys.getOrDefault(clazz, "Id");
		}
		
		public List<HandlerSpec> getHandlerSpecs() {
			return handlerSpecs;
		}
	}
	
	// Record for type display information
	private record ITDisplay(String displayName, Predicate<ModItem> matcher) {
	}
}