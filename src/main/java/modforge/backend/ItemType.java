package modforge.backend;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import modforge.backend.model.ModItem;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@lombok.extern.slf4j.Slf4j
public final class ItemType {
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
		
		displays.add(new ITDisplay("All Types", i -> true));
		
		for (final var e : ItemEntry.values()) {
			// id key (first registration wins – ItemEntry order is the priority)
			idMap.put(e.clazz, e.idKey);
			specs.add(new HandlerSpec(e.clazz, e.idKey, e.objName.toLowerCase()));
			
			// endpoint key set
			epKeys.add(e.fileName.toLowerCase());
			
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
		if (ze.isDirectory())
			return false;
		final var name = ze.getName().toLowerCase(Locale.ROOT);
		final var isData = name.endsWith(".xml") || name.endsWith(".adb");
		if (! isData)
			return false;
		// AHH STUPID STORM
		if (name.startsWith("libs/storm/"))
			return true;
		final var p = Path.of(name);
		final var fileName = p.getFileName().toString();
		final var delimiter = fileName.indexOf("__");
		final var shortName = (delimiter != - 1) ? fileName.substring(0, delimiter) : fileName.substring(0, fileName.lastIndexOf('.'));
		return ENDPOINT_KEYS.contains(shortName);
	}
	
	/** Item selector dropdown filter for the Item page (frontend). */
	public static Predicate<ModItem> matchesItemType(String selectedType) {
		if (selectedType == null)
			return null;
		return DISPLAYS.stream().filter(d -> d.displayName.equals(selectedType)).findFirst().map(ITDisplay::matcher).orElse(null);
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
		public record HandlerSpec(@NonNull Class<? extends ModItem> clazz, @NonNull String idKey, @NonNull String name) {
	}
	
	/** Display entry for the frontend type dropdown. */
		private record ITDisplay(@NonNull String displayName, @NonNull Predicate<ModItem> matcher) {
	}
}