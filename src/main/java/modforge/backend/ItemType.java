package modforge.backend;

import modforge.backend.model.ModItem;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

public enum ItemType {
	// TODO: perk_buff_overrides, perk_soul_abilitys, rpg_params
	
	WEAPON_CLASS("weapon_class", C.TABLES, ItemEntry.MELEE_WEAPON_CLASS, ItemEntry.MISSILE_WEAPON_CLASS),
	
	WEAPON_TYPE("item", C.TABLES, ItemEntry.MELEE_WEAPON, ItemEntry.MISSILE_WEAPON, ItemEntry.AMMO),
	
	ARMOR_TYPE("item", C.TABLES, ItemEntry.HOOD, ItemEntry.ARMOR, ItemEntry.HELMET),
	
	CONSUMABLE_TYPE("item", C.TABLES, ItemEntry.FOOD, ItemEntry.POISON),
	
	CRAFTING_TYPE("item", C.TABLES, ItemEntry.HERB, ItemEntry.CRAFTING_MATERIAL),
	
	MISC_TYPE("item", C.TABLES, ItemEntry.NPC_TOOL, ItemEntry.MISC_ITEM, ItemEntry.GAME_DOCUMENT, ItemEntry.DIE, ItemEntry.ITEM_ALIAS, ItemEntry.QUICK_SLOT_CONTAINER, ItemEntry.DICE_BADGE, ItemEntry.PICKABLE_ITEM, ItemEntry.KEY, ItemEntry.MONEY, ItemEntry.KEY_RING),
	
	PERK("perk", C.TABLES, ItemEntry.PERK),
	
	BUFF("buff", C.TABLES, ItemEntry.BUFF),
	
	STORM("storm", C.STORM, ItemEntry.STORM),
	
	PERK_BUFF("perk_buff", C.TABLES, ItemEntry.PERK_BUFF),
	
	PERK_SCRIPT("perk_script", C.TABLES, ItemEntry.PERK_SCRIPT);
	
	// ── Constants ────────────────────────────────────────────────────────────
	
	public static final String TABLES = C.TABLES;
	public static final String GAMEDATA = C.GAMEDATA;
	
	// ── Static indexes built once from ItemEntry.values() ───────────────────
	
	/** class → idKey */
	private static final Map<Class<? extends ModItem>, String> ID_KEYS;
	
	/** Ordered list of (class, idKey) pairs for ModItemBuilder */
	private static final List<HandlerSpec> HANDLER_SPECS;
	
	/** Display entries for the frontend dropdown */
	private static final List<ITDisplay> DISPLAYS;
	
	/** Flat set of endpoint keys, used by excludeNonEndpoints */
	private static final Set<String> ENDPOINT_KEYS;
	
	static {
		Map<Class<? extends ModItem>, String> idMap = new LinkedHashMap<>();
		List<HandlerSpec> specs = new ArrayList<>();
		List<ITDisplay> displays = new ArrayList<>();
		Set<String> epKeys = new HashSet<>();
		
		displays.add(new ITDisplay("All Types", _ -> true));
		
		for (final ItemEntry e : ItemEntry.values()) {
			// id key (first registration wins – ItemEntry order is the priority)
			idMap.putIfAbsent(e.clazz, e.idKey);
			specs.add(new HandlerSpec(e.clazz, e.idKey, e.simpleName));
			
			// endpoint key set
			epKeys.add(e.endpointKey);
			
			// display dropdown
			if (e.showInDisplay)
				displays.add(new ITDisplay(e.displayName(), e.matcher()));
		}
		
		displays.sort(Comparator.comparing(ITDisplay::displayName));
		// Ensure "All Types" stays first after sorting
		displays.sort((a, b) -> {
			if ("All Types".equals(a.displayName()))
				return - 1;
			if ("All Types".equals(b.displayName()))
				return 1;
			return a.displayName().compareTo(b.displayName());
		});
		
		ID_KEYS = Collections.unmodifiableMap(idMap);
		HANDLER_SPECS = Collections.unmodifiableList(specs);
		DISPLAYS = Collections.unmodifiableList(displays);
		ENDPOINT_KEYS = Collections.unmodifiableSet(epKeys);
	}
	
	// ── Instance fields ──────────────────────────────────────────────────────
	
	private final Set<ItemEntry> entries;
	@SuppressWarnings("unused")
	private final String key;
	@SuppressWarnings("unused")
	private final String path;
	
	ItemType(String key, String path, ItemEntry... entries) {
		this.key = key;
		this.path = path;
		this.entries = Set.of(entries);
	}
	
	// ── Public API (signatures unchanged) ────────────────────────────────────
	
	/** Item selector dropdown filter – used by the Item page (frontend). */
	public static boolean excludeNonEndpoints(final ZipEntry ze) {
		final String name = ze.getName().toLowerCase(Locale.ROOT);
		if (! name.endsWith(".xml"))
			return false;
		final Path p = Path.of(name);
		final String fileName = p.getFileName().toString();
		final int delimiter = fileName.indexOf("__");
		final String shortName = (delimiter != - 1) ? fileName.substring(0, delimiter) : fileName.substring(0, fileName.lastIndexOf('.'));
		return ENDPOINT_KEYS.contains(shortName);
	}
	
	/** Item selector dropdown filter for the Item page (frontend). */
	public static Predicate<ModItem> matchesItemType(String selectedType) {
		if (selectedType == null)
			return null;
		return DISPLAYS.stream().filter(d -> d.displayName().equals(selectedType)).findFirst().map(ITDisplay::matcher).orElse(null);
	}
	
	/** All display names for the frontend type dropdown. */
	public static Collection<String> getAllTypes() {
		return DISPLAYS.stream().map(ITDisplay::displayName).collect(Collectors.toCollection(LinkedHashSet::new));
	}
	
	/** The XML attribute name used as the primary ID for a given item class. */
	public static String getIdKey(Class<? extends ModItem> clazz) {
		return ID_KEYS.getOrDefault(clazz, "Id");
	}
	
	/**
	 * Ordered list of (class, idAttrKey) pairs – the single source of truth
	 * consumed by {@code ModItemBuilder.createDefault()} to build its handler list.
	 */
	public static List<HandlerSpec> getHandlerSpecs() {
		return HANDLER_SPECS;
	}
	
	// ── Internal helpers ─────────────────────────────────────────────────────
	
	private Set<ItemEntry> get() {
		return entries;
	}
	
	// ── Nested types (public API – unchanged) ─────────────────────────────────
	
	/** Pair of a concrete item class and the XML attribute name that holds its ID. */
	public record HandlerSpec(Class<? extends ModItem> clazz, String idKey, String name) {
	}
	
	/** Display entry for the frontend type dropdown. */
	private record ITDisplay(String displayName, Predicate<ModItem> matcher) {
	}
}

class C {
	public static final String TABLES = "Data/Tables.pak";
	public static final String STORM = "Data/Storm.pak";
	public static final String GAMEDATA = "Data/IPL_GameData.pak";
}