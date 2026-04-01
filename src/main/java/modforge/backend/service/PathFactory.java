package modforge.backend.service;

import modforge.backend.model.Language;

import java.util.*;
import java.util.stream.Collectors;

final class PathFactory {
	private PathFactory() {
	}

	// Language pack prefixes in the same order as the C# Language enum
	private static final Set<String> LANGUAGES = Language.getAllDisplayNames();

	public static String tables(String root) {
		return join(root, "Data", "Tables.pak");
	}

	public static String scripts(String root) {
		return join(root, "Data", "Scripts.pak");
	}

	public static String gameData(String root) {
		return join(root, "Data", "IPL_GameData.pak");
	}

	public static String locImport(String root, String lang) {
		return join(root, "Localization", lang + "_xml.pak");
	}

	public static String locExport(String root, String lang, String modId) {
		return join(root, "Mods", modId, "Localization", lang + "_xml", "text__" + modId + ".xml");
	}

	public static String modFolder(String root, String modId) {
		return join(root, "Mods", modId);
	}

	public static String modData(String root, String modId) {
		return join(root, "Mods", modId, "Data");
	}

	public static String stormDir(String root, String modId) {
		return join(root, "Mods", modId, "Data", "Libs", "Storm");
	}

	public static List<String> allLocPaths(String root) {
		return LANGUAGES.stream().map(l -> locImport(root, l)).collect(Collectors.toList());
	}

	/**
	 * Join path segments using forward slashes (cross-platform safe).
	 */
	public static String join(String... parts) {
		return String.join("/", parts);
	}
}
