package modforge.backend.service;

import modforge.Singleton;
import modforge.backend.ModData;
import modforge.backend.model.IModItem;
import modforge.backend.model.Language;

import java.util.*;
import java.util.logging.Logger;

public final class LocalizationService {
	private static final Logger log = Logger.getLogger(LocalizationService.class.getName());

	private final LocalizationAdapter adapter;
	private final UserService configService;

	public LocalizationService(LocalizationAdapter adapter, UserService configService) {
		this.adapter = adapter;
		this.configService = configService;
		init();
	}

	/**
	 * (Re-)load from disk. Call after the user sets a new game directory.
	 *
	 */
	public void init() {
		final String dir = configService.getCurrent().gameDirectory;
		final var game = Singleton.INSTANCE.game();
		if (dir != null && !dir.isBlank()) {
			try {
				game.localizations = adapter.readLocalizationFromXml(dir);
			} catch (Exception ex) {
				log.severe("Localisation read failed: " + ex.getMessage());
				game.localizations = new EnumMap<Language, Map<String, String>>(Language.class);
			}
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

	// ------------------------------------------------------------------

	/**
	 * Write per-language localization files into the mod's Localization folder.
	 * Each language gets: Localization/<Lang>_xml/text__<modId>.xml
	 */
	public void writeModLocalization(ModData mod) {

		final var gameDir = configService.getCurrent().gameDirectory;

		boolean ok = adapter.writeModLocalization(gameDir, mod.id, mod.localizations);
		if (ok) {
			log.info("Localization written for mod: " + mod.id);
		} else {
			log.warning("Localization write had errors for mod: " + mod.id);
		}
	}

	private String resolve(IModItem item, String... candidates) {
		final Language lang = Language.fromIsoCode(configService.getCurrent().language);
		final var langMap = Singleton.INSTANCE.game().localizations.get(lang);

		for (String candidate : candidates) {
			String clo = candidate.toLowerCase(Locale.ROOT);
			var attr = item.getAttributes().stream()
					.filter(a -> a.getName().toLowerCase(Locale.ROOT).contains(clo))
					.findFirst().orElse(null);
			if (attr == null) continue;

			String key = String.valueOf(attr.getValue());
			if (langMap.containsKey(key)) return langMap.get(key);
			// Fall back to the raw value stored in the attribute
			return key;
		}
		return null;
	}
}