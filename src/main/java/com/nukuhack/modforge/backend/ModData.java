package com.nukuhack.modforge.backend;

import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.backend.model.E.Language;
import com.nukuhack.modforge.backend.model.ModItem;
import com.nukuhack.modforge.backend.service.IconService;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
@Getter
@Setter
@NonNull
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public final class ModData {
	private final Set<ModItem> items = new HashSet<>();
	private final Map<String, String> config = new HashMap<>();
	/**
	 * lang-code -> (string-key -> localized-value)
	 */
	private final Map<Language, Map<String, String>> localizations = new EnumMap<>(Language.class);
	/**
	 * Per-mod icon store: icon stem (lowercase, no extension) -> raw DDS bytes.
	 * Populated by IconService.loadModIconsForMod() from the mod's own PAK files.
	 */
	private final Set<IconService.Icon> icons = new HashSet<>();
	/**
	 * Set of supported versions of the KDC2 game
	 */
	private final Set<String> supportsGameVersions = new HashSet<>();
	private String id = "";
	private String name = "";
	private String description = "";
	private String author = "";
	private String modVersion = "";
	private String createdOn = "";
	private boolean modifiesLevel = false;
	
	@Override
	public String toString() {
		return "ModData{" + "id='" + id + '\'' + ", name='" + name + '\'' + ", description='" + description + '\'' + ", author='" + author + '\'' + ", modVersion='" + modVersion + '\'' + ", createdOn='" + createdOn + '\'' + ", modifiesLevel=" + modifiesLevel + ", supportsGameVersions=" + supportsGameVersions + ", item_size=" + items.size() + ", lang_size=" + localizations.size() + ", icon_size=" + icons.size() + '}';
	}
	
	public void addItem(ModItem item) {
		items.add(item);
	}
	
	public void setItems(Collection<ModItem> input) {
		this.items.clear();
		var duplicates = new ArrayList<ModItem>();
		input.forEach(i -> {
			if (log.isDebugEnabled() && this.items.contains(i)) {
				duplicates.add(i);
				log.debug("Duplicated item: {}", i);
			}
			this.items.add(i);
		});
		
		if (! duplicates.isEmpty()) {
			log.warn("Found {} duplicate IDs, lost that much items", duplicates.size());
		}
	}
	
	public Optional<ModItem> getItem(String key) {
		return items.stream().filter(i -> i.getId().equals(key)).findAny();
	}
	
	public boolean containsItem(String key) {
		return getItem(key).isPresent();
	}
	
	public boolean containsItem(ModItem value) {
		return items.contains(value);
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
		return localizations.getOrDefault(language, Map.of());
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
	
	public void setIcon(Set<IconService.Icon> input) {
		icons.clear();
		icons.addAll(input);
	}

	public Optional<IconService.Icon> getIcon(String key) {
		var equalRandomCase = icons.stream().filter(i -> i.path().equals(key)).findAny();
		if (equalRandomCase.isPresent())
			return equalRandomCase;
		var stemRandomCase = icons.stream().filter(i -> Util.stemOf(i.path()).equals(key)).findAny();
		if (stemRandomCase.isPresent())
			return stemRandomCase;

		var keyLower = key.toLowerCase(Locale.ROOT);
		var equalLowerCase = icons.stream().filter(i -> i.path().toLowerCase(Locale.ROOT).equals(keyLower)).findAny();
		if (equalLowerCase.isPresent())
			return equalLowerCase;
		var stemLowerCase = icons.stream().filter(i -> Util.stemOf(i.path()).toLowerCase(Locale.ROOT).equals(keyLower)).findAny();
		return stemLowerCase;
	}
	
	public void setSupportsGameVersions(Collection<String> input) {
		supportsGameVersions.clear();
		supportsGameVersions.addAll(input);
	}
}