package modforge.backend.model;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum Language {
	ENGLISH("English", "English", "en"),
	GERMAN("German", "Deutsch", "de"),
	FRENCH("French", "Français", "fr"),
	SPANISH("Spanish", "Español", "es"),
	ITALIAN("Italian", "Italiano", "it"),
	POLISH("Polish", "Polski", "pl"),
	RUSSIAN("Russian", "Русский", "ru"),
	CZECH("Czech", "Čeština", "cs"),
	HUNGARIAN("Hungarian", "Magyar", "hu"),
	SLOVAK("Slovak", "Slovenčina", "sk"),
	PORTUGUESE("Portuguese", "Português", "pt"),
	CHINESE("Chinese", "中文", "zh"),
	JAPANESE("Japanese", "日本語", "ja"),
	KOREAN("Korean", "한국어", "ko");
	
	private final String name;        // English name (e.g., "German")
	private final String displayName; // Real/native name (e.g., "Deutsch")
	private final String isoCode;
	
	Language(String name, String displayName, String isoCode) {
		this.name = name;
		this.displayName = displayName;
		this.isoCode = isoCode;
	}
	
	/**
	 * Get Language enum from English name (e.g., "English" -> ENGLISH)
	 */
	public static Language fromName(String name) {
		return Arrays.stream(values()).filter(lang -> lang.name.equals(name)).findFirst().orElse(null);
	}
	
	/**
	 * Get Language enum from display name (e.g., "Deutsch" -> GERMAN)
	 */
	public static Language fromDisplayName(String displayName) {
		return Arrays.stream(values()).filter(lang -> lang.displayName.equals(displayName)).findFirst().orElse(null);
	}
	
	/**
	 * Get Language enum from ISO code (e.g., "en" -> ENGLISH)
	 */
	public static Language fromIsoCode(String isoCode) {
		return Arrays.stream(values()).filter(lang -> lang.isoCode.equalsIgnoreCase(isoCode)).findFirst().orElse(null);
	}
	
	/**
	 * Get all English names
	 */
	public static Set<String> getAllNames() {
		return Arrays.stream(values()).map(Language::getName).collect(Collectors.toSet());
	}
	
	/**
	 * Get all display names (native names)
	 */
	public static Set<String> getAllDisplayNames() {
		return Arrays.stream(values()).map(Language::getDisplayName).collect(Collectors.toSet());
	}
	
	/**
	 * Get all ISO codes
	 */
	public static Set<String> getAllIsoCodes() {
		return Arrays.stream(values()).map(Language::getIsoCode).collect(Collectors.toSet());
	}
	
	/**
	 * Get all formatted strings (ISO - English name)
	 */
	public static Set<String> getAllFormattedNames() {
		return Arrays.stream(values()).map(lang -> lang.getIsoCode() + " - " + lang.getName()).collect(Collectors.toSet());
	}
	
	public String getName() {
		return name;
	}
	
	public String getDisplayName() {
		return displayName;
	}
	
	public String getIsoCode() {
		return isoCode;
	}
}