package modforge.backend;

import modforge.backend.model.ModItem;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

public class ItemType {
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
		
		for (final var e : ItemEntry.values()) {
			// id key (first registration wins – ItemEntry order is the priority)
			idMap.put(e.clazz, e.idKey);
			specs.add(new HandlerSpec(e.clazz, e.idKey, e.simpleName()));
			
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
	
	// ── Nested types (public API – unchanged) ─────────────────────────────────
	
	/** Pair of a concrete item class and the XML attribute name that holds its ID. */
	public record HandlerSpec(Class<? extends ModItem> clazz, String idKey, String name) {
	}
	
	/** Display entry for the frontend type dropdown. */
	private record ITDisplay(String displayName, Predicate<ModItem> matcher) {
	}
}