package modforge.backend.service;

import modforge.backend.ModDescription;
import modforge.backend.model.IModItem;

import java.util.*;
import java.util.logging.Logger;

public final class LocalizationService {
	private static final Logger log =
			Logger.getLogger(LocalizationService.class.getName());

	private final LocalizationAdapter adapter;
	private final UserConfigurationService configService;
	/**
	 * lang-code -> (string-key -> localised-value)
	 */
	private Map<String, Map<String, String>> localizations = new HashMap<>();

	public LocalizationService(LocalizationAdapter adapter,
							   UserConfigurationService configService) {
		this.adapter = adapter;
		this.configService = configService;
		init();
	}

	/**
	 * (Re-)load from disk. Call after the user sets a new game directory.
	 */
	public void init() {
		String dir = configService.getCurrent().gameDirectory;
		if (dir != null && !dir.isBlank()) {
			localizations = adapter.readLocalizationFromXml(dir);
		}
	}

	public String getName(IModItem item) {
		return resolve(item, "ui_name", "UIName", "name");
	}

	public String getDescription(IModItem item) {
		return resolve(item, "ui_desc", "UIInfo");
	}

	public String getLoreDescription(IModItem item) {
		return resolve(item, "ui_lore_desc");
	}

	/**
	 * Delegate read to adapter (used by XmlService).
	 */
	public Map<String, Map<String, String>> readLocalizationFromXml(String path) {
		if (path == null || path.isBlank()) return new HashMap<>();
		try {
			return adapter.readLocalizationFromXml(path);
		} catch (Exception ex) {
			log.severe("Localisation read failed: " + ex.getMessage());
			return new HashMap<>();
		}
	}

	public void writeLocalizationAsXml(String path, ModDescription mod) {
		adapter.writeLocalizationAsXml(path, mod);
	}

	// ------------------------------------------------------------------

	private String resolve(IModItem item, String... candidates) {
		String lang = configService.getCurrent().language;
		var langMap = localizations.get(lang);

		for (String candidate : candidates) {
			String clo = candidate.toLowerCase(Locale.ROOT);
			var attr = item.getAttributes().stream()
					.filter(a -> a.getName().toLowerCase(Locale.ROOT).contains(clo))
					.findFirst().orElse(null);
			if (attr == null) continue;

			String key = String.valueOf(attr.getValue());
			if (langMap != null && langMap.containsKey(key)) return langMap.get(key);
			// Fall back to the raw value stored in the attribute
			return key;
		}
		return null;
	}
}
