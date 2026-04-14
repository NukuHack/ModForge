package com.nukuhack.modforge.backend.service;

import com.nukuhack.modforge.Singleton;
import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.backend.ModData;
import com.nukuhack.modforge.backend.model.E.Language;
import com.nukuhack.util.IOUtil;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public interface UserConfig {
	
	@NonNull
	String getGameDir();
	
	void setGameDir(@NonNull String gameDir);
	
	@NonNull
	Language getLanguage();
	
	void setLanguage(@NonNull Language language);
	
	@NonNull
	String getUserName();
	
	void setUserName(@NonNull String username);
	
	boolean isAutoLoadGameData();
	
	void setAutoLoadGameData(boolean bool);
	
	void load();
	
	void save();
	
	@Slf4j
	@Getter
	@Setter
	@NonNull
	@NoArgsConstructor(access = AccessLevel.PUBLIC)
	final class UserConfigImpl implements UserConfig {
		
		private static final Path configFile = Singleton.getUserConfig();
		
		private String gameDir = "";
		private String userName = "";
		private Language language = Language.ENGLISH;
		/**
		 * Load all game-data or not at startup, added so debugging is faster
		 */
		private boolean autoLoadGameData = true;
		
		@Override
		public void setGameDir(@NonNull String gameDir) {
			try {
				IOUtil.ensureDirExists(Path.of(gameDir));
			} catch (IOException e) {
				log.warn("failed to create directory for game path", e);
				return;
			}
			this.gameDir = gameDir;
		}
		
		public void load() {
			try {
				final var parsed = JsonIO.read(configFile);
				fromJson(parsed);
				log.info("User configuration loaded from {}", configFile);
			} catch (Exception e) {
				log.error("Config load error: {}", e.getMessage());
			}
		}
		
		private @NonNull JsonIO.JsonObject toJsonObject() {
			final var obj = new JsonIO.JsonObject();
			obj.put("gameDir", new JsonIO.JsonString(gameDir));
			obj.put("userName", new JsonIO.JsonString(userName));
			obj.put("language", new JsonIO.JsonString(language.getIsoCode()));
			obj.put("autoLoadGameData", new JsonIO.JsonBoolean(autoLoadGameData));
			return obj;
		}
		
		private void fromJson(JsonIO.JsonValue value) {
			if (! (value instanceof JsonIO.JsonObject obj))
				return;
			if (obj.get("gameDir") instanceof JsonIO.JsonString string)
				gameDir = string.getValue();
			if (gameDir == null)
				gameDir = "";
			if (obj.get("userName") instanceof JsonIO.JsonString string)
				userName = string.getValue();
			if (userName == null)
				userName = "";
			if (obj.get("language") instanceof JsonIO.JsonString string)
				language = Language.fromIsoCode(string.getValue());
			if (language == null)
				language = Language.ENGLISH;
			if (obj.get("autoLoadGameData") instanceof JsonIO.JsonBoolean bool)
				autoLoadGameData = bool.getValue();
		}
		
		public void save() {
			var good = JsonIO.write(configFile, toJsonObject());
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
			if (gameDir.isBlank())
				return;
			Path loadOrder = Path.of(Util.modFolder(gameDir).toString(), "mod_order.txt");
			try {
				Files.deleteIfExists(loadOrder);
				var ids = orderedMods.stream().map(ModData::getId).collect(Collectors.toList());
				Files.write(loadOrder, ids, StandardCharsets.UTF_8);
			} catch (IOException e) {
				log.warn("Cannot write mod_order.txt: {}", e.getMessage());
			}
		}
	}
}