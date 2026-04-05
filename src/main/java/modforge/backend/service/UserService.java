package modforge.backend.service;

import modforge.Singleton;
import modforge.Util;
import modforge.backend.ModData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class UserService {
	private static final Logger log = Logger.getLogger(UserService.class.getName());
	
	private final Path configFile;
	
	public String gameDirectory = "";
	public String userName = "";
	public String language = "en";
	
	public UserService() {
		configFile = Singleton.INSTANCE.getUserConfig();
		
		try {
			final var parsed = JsonIO.read(configFile);
			fromJson(parsed);
			log.info("User configuration loaded from " + configFile);
		} catch (Exception e) {
			log.severe("Config load error: " + e.getMessage());
		}
	}
	
	// Convert to JsonValue
	private JsonIO.JsonObject toJsonObject() {
		final JsonIO.JsonObject obj = new JsonIO.JsonObject();
		obj.put("gameDirectory", new JsonIO.JsonString(gameDirectory));
		obj.put("userName", new JsonIO.JsonString(userName));
		obj.put("language", new JsonIO.JsonString(language));
		return obj;
	}
	
	// Create from JsonValue
	private void fromJson(JsonIO.JsonValue value) {
		if (value instanceof JsonIO.JsonObject obj) {
			if (obj.get("gameDirectory") instanceof JsonIO.JsonString string) {
				gameDirectory = string.getValue();
			}
			if (obj.get("userName") instanceof JsonIO.JsonString string) {
				userName = string.getValue();
			}
			if (obj.get("language") instanceof JsonIO.JsonString string) {
				language = string.getValue();
			}
		}
	}
	
	public void save() {
		final var good = JsonIO.write(configFile, toJsonObject());
		if (good)
			log.info("User configuration saved.");
		else
			log.severe("Config save error");
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
			log.warning("Cannot write mod_order.txt: " + e.getMessage());
		}
	}
}