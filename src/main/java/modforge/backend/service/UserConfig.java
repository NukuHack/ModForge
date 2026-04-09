package modforge.backend.service;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import modforge.Singleton;
import modforge.Util;
import modforge.backend.ModData;
import modforge.backend.model.E.Language;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public interface UserConfig {
	
	String getGameDirectory();
	
	void setGameDirectory(String gameDirectory);
	
	Language getLanguage();
	
	void setLanguage(Language language);
	
	String getUserName();
	
	void setUserName(String username);
	
	boolean isAutoLoadGameData();
	
	void setAutoLoadGameData(boolean bool);
	
	void load();
	
	void save();
	
	
	@NoArgsConstructor(access = AccessLevel.PUBLIC)
	@Slf4j
	@Getter
	@Setter
	final class UserConfigImpl implements UserConfig {
		
		private static final Path configFile = Singleton.getUserConfig();
		
		private String gameDirectory = "";
		private String userName = "";
		private Language language = Language.ENGLISH;
		/**
		 * Load all game-data or not at startup, added so debugging is faster
		 */
		private boolean autoLoadGameData = true;
		
		public void load() {
			try {
				final var parsed = JsonIO.read(configFile);
				fromJson(parsed);
				log.info("User configuration loaded from {}", configFile);
			} catch (Exception e) {
				log.error("Config load error: {}", e.getMessage());
			}
		}
		
		// Convert to JsonValue
		private JsonIO.JsonObject toJsonObject() {
			final var obj = new JsonIO.JsonObject();
			obj.put("gameDirectory", new JsonIO.JsonString(gameDirectory));
			obj.put("userName", new JsonIO.JsonString(userName));
			obj.put("language", new JsonIO.JsonString(language.getIsoCode()));
			obj.put("autoLoadGameData", new JsonIO.JsonBoolean(autoLoadGameData));
			return obj;
		}
		
		// Create from JsonValue
		private void fromJson(JsonIO.JsonValue value) {
			if (! (value instanceof JsonIO.JsonObject obj))
				return;
			if (obj.get("gameDirectory") instanceof JsonIO.JsonString string)
				gameDirectory = string.getValue();
			if (obj.get("userName") instanceof JsonIO.JsonString string)
				userName = string.getValue();
			if (obj.get("language") instanceof JsonIO.JsonString string)
				language = Language.fromIsoCode(string.getValue());
			if (obj.get("autoLoadGameData") instanceof JsonIO.JsonBoolean bool)
				autoLoadGameData = bool.getValue();
		}
		
		public void save() {
			final var good = JsonIO.write(configFile, toJsonObject());
			if (good)
				log.info("User configuration saved.");
			else
				log.error("Config save error");
		}
		
		/**
		 * Write mod-load order file (mirrors C# WriteLoadout).
		 * idk what this is and how to use it so left it alone ...
		 */
		public void writeLoadout(List<ModData> orderedMods) {
			if (gameDirectory == null || gameDirectory.isBlank())
				return;
			Path loadOrder = Path.of(Util.modFolder(gameDirectory).toString(), "mod_order.txt");
			try {
				Files.deleteIfExists(loadOrder);
				var ids = orderedMods.stream().map(m -> m.id).collect(Collectors.toList());
				Files.write(loadOrder, ids, StandardCharsets.UTF_8);
			} catch (IOException e) {
				log.warn("Cannot write mod_order.txt: {}", e.getMessage());
			}
		}
	}
}