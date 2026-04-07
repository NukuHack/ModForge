package modforge.backend;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import modforge.backend.model.ModItem;
import modforge.backend.model.item.E.Language;

import java.util.*;

@NoArgsConstructor(access = AccessLevel.PUBLIC)
@lombok.extern.slf4j.Slf4j
public final class ModData {
	private final Map<String, ModItem> items = new HashMap<>();
	private final Map<String, String> config = new HashMap<>();
	/**
	 * lang-code -> (string-key -> localized-value)
	 */
	private final Map<Language, Map<String, String>> localizations = new EnumMap<>(Language.class);
	/**
	 * Per-mod icon store: icon stem (lowercase, no extension) -> raw DDS bytes.
	 * Populated by IconService.loadModIconsForMod() from the mod's own PAK files.
	 */
	private final Map<String, byte[]> iconIndex = new HashMap<>();
	public String id = "";
	public String name = "";
	public String description = "";
	public String author = "";
	public String modVersion = "";
	public String createdOn = "";
	public boolean modifiesLevel = false;
	public List<String> supportsGameVersions = new ArrayList<>();
	
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
			if (log.isDebugEnabled() && this.items.containsKey(i.getId())) {
				duplicates.computeIfAbsent(i.getId(), k -> new ArrayList<>()).add(this.items.get(i.getId()));
				duplicates.get(i.getId()).add(i);
				log.debug("Duplicated item: {}", i);
			}
			this.items.put(i.getId(), i);
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
	
	public Map<String, String> getConfig() {
		return Collections.unmodifiableMap(config);
	}
	
	public void setConfig(Map<String, String> input) {
		config.clear();
		config.putAll(input);
	}
	
	public Map<Language, Map<String, String>> getLocal() {
		return Collections.unmodifiableMap(localizations);
	}
	
	public void setLocal(Map<Language, Map<String, String>> input) {
		localizations.clear();
		localizations.putAll(input);
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
	
	public Map<String, byte[]> getIcon() {
		return Collections.unmodifiableMap(iconIndex);
	}
	
	public void setIcon(Map<String, byte[]> input) {
		iconIndex.clear();
		iconIndex.putAll(input);
	}
}