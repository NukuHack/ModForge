package modforge.backend.model;

import java.util.*;
import java.util.stream.Collectors;

public enum Language {
	ENGLISH("English", "en"),
	GERMAN("German", "de"),
	FRENCH("French", "fr"),
	SPANISH("Spanish", "es"),
	ITALIAN("Italian", "it"),
	POLISH("Polish", "pl"),
	RUSSIAN("Russian", "ru"),
	CZECH("Czech", "cs"),
	HUNGARIAN("Hungarian", "hu"),
	SLOVAK("Slovak", "sk"),
	PORTUGUESE("Portuguese", "pt"),
	CHINESE("Chinese", "zh"),
	JAPANESE("Japanese", "ja"),
	KOREAN("Korean", "ko");

	private final String displayName;
	private final String isoCode;

	Language(String displayName, String isoCode) {
		this.displayName = displayName;
		this.isoCode = isoCode;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getIsoCode() {
		return isoCode;
	}

	/**
	 * Get Language enum from display name (e.g., "English" -> ENGLISH)
	 */
	public static Language fromDisplayName(String displayName) {
		return Arrays.stream(values())
				.filter(lang -> lang.displayName.equals(displayName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown language: " + displayName));
	}

	/**
	 * Get Language enum from ISO code (e.g., "en" -> ENGLISH)
	 */
	public static Language fromIsoCode(String isoCode) {
		return Arrays.stream(values())
				.filter(lang -> lang.isoCode.equalsIgnoreCase(isoCode))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown ISO code: " + isoCode));
	}

	/**
	 * Check if a display name or ISO code is a valid language
	 */
	public static boolean isValid(String language) {
		return Arrays.stream(values())
				.anyMatch(lang -> lang.displayName.equals(language) || lang.isoCode.equalsIgnoreCase(language));
	}

	/**
	 * Get all display names
	 */
	public static Set<String> getAllDisplayNames() {
		return Arrays.stream(values())
				.map(Language::getDisplayName)
				.collect(Collectors.toSet());
	}

	/**
	 * Get all ISO codes
	 */
	public static Set<String> getAllIsoCodes() {
		return Arrays.stream(values())
				.map(Language::getIsoCode)
				.collect(Collectors.toSet());
	}

	/**
	 * Get a map of display name -> ISO code (like the original LANG_MAP)
	 */
	public static Map<String, String> asMap() {
		return Arrays.stream(values())
				.collect(Collectors.toMap(
						Language::getDisplayName,
						Language::getIsoCode,
						(a, b) -> a,
						LinkedHashMap::new
				));
	}
}