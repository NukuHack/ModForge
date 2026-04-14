package com.nukuhack.modforge.backend;

import com.nukuhack.modforge.backend.model.ModItem;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;

@Slf4j
@NonNull
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ItemType {
	
	/** class → idKey */
	private static final Map<Class<? extends ModItem>, String> ID_KEYS;
	
	/** Display entries for the frontend dropdown */
	private static final List<ITDisplay> DISPLAYS;
	
	/** Flat set of endpoint keys, used by excludeNonEndpoints */
	private static final Set<String> ENDPOINT_KEYS;
	
	static {
		var idMap = new LinkedHashMap<Class<? extends ModItem>, String>();
		var displays = new ArrayList<ITDisplay>();
		var epKeys = new HashSet<String>();
		
		displays.add(new ITDisplay("All Types", i -> true));
		
		for (var e : ItemEntry.values()) {
			
			idMap.put(e.clazz, e.idKey);
			
			epKeys.add(e.fileName.toLowerCase());
			
			if (e.showInDisplay)
				displays.add(new ITDisplay(e.displayName(), e.matcher()));
		}
		
		displays.sort(Comparator.comparing(ITDisplay::displayName));
		
		displays.sort((a, b) -> {
			if ("All Types".equals(a.displayName()))
				return - 1;
			if ("All Types".equals(b.displayName()))
				return 1;
			return a.displayName().compareTo(b.displayName());
		});
		
		ID_KEYS = Collections.unmodifiableMap(idMap);
		DISPLAYS = Collections.unmodifiableList(displays);
		ENDPOINT_KEYS = Collections.unmodifiableSet(epKeys);
	}
	
	/** Item selector dropdown filter – used by the Item page (frontend). */
	public static boolean excludeNonEndpoints(final @NonNull ZipEntry ze) {
		if (ze.isDirectory())
			return false;
		var name = ze.getName().toLowerCase(Locale.ROOT).replace('\\', '/');
		var isData = name.endsWith(".xml") || name.endsWith(".adb");
		if (! isData)
			return false;
		
		if (name.startsWith("libs/storm/"))
			return true;
		var p = Path.of(name);
		var fileName = p.getFileName().toString();
		var delimiter = fileName.indexOf("__");
		var shortName = (delimiter != - 1) ? fileName.substring(0, delimiter) : fileName.substring(0, fileName.lastIndexOf('.'));
		return ENDPOINT_KEYS.contains(shortName);
	}
	
	/** Item selector dropdown filter for the Item page (frontend). */
	public static Predicate<ModItem> matchesItemType(String selectedType) {
		if (selectedType == null)
			return null;
		return DISPLAYS.stream().filter(d -> d.displayName.equals(selectedType)).findFirst().map(ITDisplay::matcher).orElse(null);
	}
	
	/** All display names for the frontend type dropdown. */
	public static @NonNull Collection<String> getAllTypes() {
		return DISPLAYS.stream().map(ITDisplay::displayName).toList();
	}
	
	/** The XML attribute name used as the primary ID for a given item class. */
	public static @NonNull String getIdKey(Class<? extends ModItem> clazz) {
		return ID_KEYS.getOrDefault(clazz, "Id");
	}
	
	/** Display entry for the frontend type dropdown. */
	@NonNull
	private record ITDisplay(String displayName, Predicate<ModItem> matcher) {
	}
}