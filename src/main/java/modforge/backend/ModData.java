package modforge.backend;

import modforge.backend.model.ModItem;
import modforge.backend.model.Language;

import java.util.*;

public final class ModData {
	public String id = "";
	public String name = "";
	public String description = "";
	public String author = "";
	public String modVersion = "";
	public String createdOn = "";
	public boolean modifiesLevel = false;
	public List<String> supportsGameVersions = new ArrayList<>();

	private final List<ModItem> items = new ArrayList<>();
	private final Map<String, String> config = new HashMap<>();

	/**
	 * lang-code -> (string-key -> localised-value)
	 */
	private final Map<Language, Map<String, String>> localizations = new EnumMap<>(Language.class);

	/**
	 * Per-mod icon store: icon stem (lowercase, no extension) -> raw DDS bytes.
	 * Populated by IconService.loadModIconsForMod() from the mod's own PAK files.
	 * When resolving an icon, the mod-local index is checked first; the base-game
	 * index in IconService is used as a fallback.
	 */
	private final Map<String, byte[]> iconIndex = new HashMap<>();

	/**
	 * Lazy PNG cache for this mod: icon stem -> "data:image/png;base64,…" string.
	 * Populated on first access via IconService.getBase64Icon(iconId, mod).
	 */
	private final Map<String, String> pngCache = new HashMap<>();

	@Override
	public String toString() {
		return "ModData{" +
				"id='" + id + '\'' +
				", name='" + name + '\'' +
				", description='" + description + '\'' +
				", author='" + author + '\'' +
				", modVersion='" + modVersion + '\'' +
				", createdOn='" + createdOn + '\'' +
				", modifiesLevel=" + modifiesLevel +
				", supportsGameVersions=" + supportsGameVersions +
				", item_size=" + items.size() +
				", lang_size=" + localizations.size() +
				", icon_size=" + iconIndex.size() +
				'}';
	}

	public void setItems(Collection<ModItem> input) {
		items.clear();
		items.addAll(input);
	}
	public void addItem(ModItem copy) {
		items.add(copy);
	}
	public List<ModItem> getItems() {
		return Collections.unmodifiableList(items);
	}

	public void setConfig(Map<String, String> input) {
		config.clear();
		config.putAll(input);
	}
	public Map<String, String> getConfig() {
		return Collections.unmodifiableMap(config);
	}

	public void setLocal(Map<Language, Map<String, String>> input) {
		localizations.clear();
		localizations.putAll(input);
	}
	public Map<Language, Map<String, String>> getLocal() {
		return Collections.unmodifiableMap(localizations);
	}

	public void setIcon(Map<String, byte[]> input) {
		iconIndex.clear();
		pngCache.clear();
		iconIndex.putAll(input);
	}
	public Map<String, byte[]> getIcon() {
		return Collections.unmodifiableMap(iconIndex);
	}

	public void setPng(Map<String, String> input) {
		pngCache.clear();
		pngCache.putAll(input);
	}
	public void clearPng() {
		pngCache.clear();
	}
	public void addPng(String key, String dataUri) {
		pngCache.put(key, dataUri);
	}
	public Map<String, String> getPng() {
		return Collections.unmodifiableMap(pngCache);
	}

}