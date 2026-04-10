package com.nukuhack.modforge.backend;

import com.nukuhack.modforge.backend.model.E.Language;
import com.nukuhack.modforge.backend.model.ModItem;
import com.nukuhack.modforge.backend.service.IconService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor
@Slf4j
public final class ModData {
	private final Map<String, ModItem> items = new HashMap<>();
	@Getter
	private final Map<String, String> config = new HashMap<>();
	/**
	 * lang-code -> (string-key -> localized-value)
	 */
	@Getter
	private final Map<Language, Map<String, String>> localizations = new EnumMap<>(Language.class);
	/**
	 * Per-mod icon store: icon stem (lowercase, no extension) -> raw DDS bytes.
	 * Populated by IconService.loadModIconsForMod() from the mod's own PAK files.
	 */
	@Getter
	private final Map<String, IconService.Icon> iconIndex = new HashMap<>();
	/**
	 * Set of supported versions of the KDC2 game
	 */
	@Getter
	private final Set<String> supportsGameVersions = new HashSet<>();
	public String id = "";
	public String name = "";
	public String description = "";
	public String author = "";
	public String modVersion = "";
	public String createdOn = "";
	public boolean modifiesLevel = false;
	
	@Override
	public String toString() {
		return "ModData{" + "id='" + id + '\'' + ", name='" + name + '\'' + ", description='" + description + '\'' + ", author='" + author + '\'' + ", modVersion='" + modVersion + '\'' + ", createdOn='" + createdOn + '\'' + ", modifiesLevel=" + modifiesLevel + ", supportsGameVersions=" + supportsGameVersions + ", item_size=" + items.size() + ", lang_size=" + localizations.size() + ", icon_size=" + iconIndex.size() + '}';
	}
	
	public void addItem(ModItem item) {
		items.put(item.getId(), item);
	}
	
	public Collection<ModItem> getItems() {
		return Collections.unmodifiableCollection(items.values());
	}
	
	public void setItems(Collection<ModItem> input) {
		this.items.clear();
		Map<String, List<ModItem>> duplicates = new HashMap<>();
		input.forEach(i -> {
			var itemId = i.getId();
			if (log.isDebugEnabled() && this.items.containsKey(itemId)) {
				duplicates.computeIfAbsent(itemId, k -> new ArrayList<>()).add(this.items.get(itemId));
				duplicates.get(itemId).add(i);
				log.debug("Duplicated item: {}, original was: {}", i, this.items.get(itemId));
			}
			this.items.put(itemId, i);
		});
		
		if (! duplicates.isEmpty()) {
			log.warn("Found {} duplicate IDs, lost {} items", duplicates.size(), duplicates.values().stream().mapToInt(List::size).sum() - duplicates.size());
		}
	}
	
	public Map<String, ModItem> items() {
		return Collections.unmodifiableMap(items);
	}
	
	public ModItem getItem(String key) {
		return items.get(key);
	}
	
	public boolean containsItem(String key) {
		return items.containsKey(key);
	}
	
	public boolean containsItem(ModItem value) {
		return items.containsValue(value);
	}
	
	public void setConfig(Map<String, String> input) {
		config.clear();
		config.putAll(input);
	}
	
	public void setLocal(Map<Language, Map<String, String>> input) {
		localizations.clear();
		localizations.putAll(input);
	}
	
	public Map<String, String> getLang(Language language) {
		return Collections.unmodifiableMap(localizations.getOrDefault(language, Map.of()));
	}
	
	public void addLocal(Language language, Map<String, String> input) {
		final var existing = localizations.get(language);
		final Map<String, String> map;
		if (existing != null)
			map = new HashMap<>(existing);
		else
			map = new HashMap<>();
		map.putAll(input);
		localizations.put(language, map);
	}
	
	public void setIcon(Map<String, IconService.Icon> input) {
		iconIndex.clear();
		iconIndex.putAll(input);
	}
	
	public IconService.Icon getIcon(String key) {
		return iconIndex.get(key);
	}
	
	public void setSupportsGameVersions(Collection<String> input) {
		supportsGameVersions.clear();
		supportsGameVersions.addAll(input);
	}
}