package modforge.backend.service;

import modforge.Singleton;
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

	// Convert to JsonValue
	private JsonAdapter.JsonObject toJsonObject() {
		final JsonAdapter.JsonObject obj = new JsonAdapter.JsonObject();
		obj.put("gameDirectory", new JsonAdapter.JsonString(gameDirectory));
		obj.put("userName", new JsonAdapter.JsonString(userName));
		obj.put("language", new JsonAdapter.JsonString(language));
		return obj;
	}

	// Create from JsonValue
	private void fromJson(JsonAdapter.JsonValue value) {
		if (value instanceof JsonAdapter.JsonObject obj) {
			if (obj.get("gameDirectory") instanceof JsonAdapter.JsonString string) {
				gameDirectory = string.getValue();
			}
			if (obj.get("userName") instanceof JsonAdapter.JsonString string) {
				userName = string.getValue();
			}
			if (obj.get("language") instanceof JsonAdapter.JsonString string) {
				language = string.getValue();
			}
		}
	}

	public UserService() {
		configFile = Singleton.INSTANCE.getUserConfig();

		try {
			final var parsed = JsonAdapter.read(configFile);
			fromJson(parsed);
			log.info("User configuration loaded from " + configFile);
		} catch (Exception e) {
			log.severe("Config load error: " + e.getMessage());
		}
	}

	public void save() {
		try {
			JsonAdapter.write(configFile, toJsonObject());
			log.info("User configuration saved.");
		} catch (IOException e) {
			log.severe("Config save error: " + e.getMessage());
		}
	}

	/**
	 * Write mod-load order file (mirrors C# WriteLoadout).
	 */
	// idk why this is and how to use it so left it alone ...
	public void writeLoadout(List<ModData> orderedMods) {
		if (gameDirectory == null || gameDirectory.isBlank()) return;
		Path loadOrder = Path.of(gameDirectory, "Mods", "mod_order.txt");
		try {
			Files.deleteIfExists(loadOrder);
			var ids = orderedMods.stream()
					.map(m -> m.id)
					.collect(Collectors.toList());
			Files.write(loadOrder, ids, StandardCharsets.UTF_8);
		} catch (IOException e) {
			log.warning("Cannot write mod_order.txt: " + e.getMessage());
		}
	}
}