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
	
	public void addItem(@NonNull ModItem item) {
		items.add(item);
	}
	
	public void setItems(@NonNull Collection<ModItem> input) {
		this.items.clear();
		this.items.addAll(input);
	}
	
	public @NonNull Optional<ModItem> getItem(@NonNull String key) {
		return items.stream().filter(i -> i.getId().equals(key)).findAny();
	}
	
	public boolean containsItem(@NonNull String key) {
		return getItem(key).isPresent();
	}
	
	public boolean containsItem(@NonNull ModItem value) {
		return items.contains(value);
	}
	
	public void setConfig(@NonNull Map<String, String> input) {
		config.clear();
		config.putAll(input);
	}
	
	public void setLocal(@NonNull Map<Language, @NonNull Map<String, String>> input) {
		localizations.clear();
		localizations.putAll(input);
	}
	
	public @NonNull Map<String, String> getLang(@NonNull Language language) {
		return localizations.getOrDefault(language, Map.of());
	}
	
	public void addLocal(@NonNull Language language, @NonNull Map<String, String> input) {
		var existing = localizations.get(language);
		if (existing == null)
			existing = new HashMap<>();
		existing.putAll(input);
	}
	
	public void setIcon(@NonNull Set<IconService.Icon> input) {
		icons.clear();
		icons.addAll(input);
	}

	public @NonNull Optional<IconService.Icon> getIcon(@NonNull String key) {
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
	
	public void setSupportsGameVersions(@NonNull Collection<String> input) {
		supportsGameVersions.clear();
		supportsGameVersions.addAll(input);
	}
}