package modforge.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
	private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	private UserConfiguration current;

	public UserService() {
		configFile = Singleton.INSTANCE.getUserConfig();
		load();
	}

	public UserConfiguration getCurrent() {
		return current;
	}

	private void load() {
		try {
			if (Files.exists(configFile)) {
				current = mapper.readValue(configFile.toFile(), UserConfiguration.class);
				log.info("User configuration loaded from " + configFile);
			} else {
				current = new UserConfiguration();
				log.info("No config file found - using defaults.");
			}
		} catch (Exception e) {
			log.severe("Config load error: " + e.getMessage());
			current = new UserConfiguration();
		}
	}

	public void save() {
		try {
			Files.createDirectories(configFile.getParent());
			mapper.writeValue(configFile.toFile(), current);
			log.info("User configuration saved.");
		} catch (IOException e) {
			log.severe("Config save error: " + e.getMessage());
		}
	}

	/**
	 * Write mod-load order file (mirrors C# WriteLoadout).
	 */
	public void writeLoadout(List<ModData> orderedMods) {
		String dir = current.gameDirectory;
		if (dir == null || dir.isBlank()) return;
		Path loadOrder = Path.of(dir, "Mods", "mod_order.txt");
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
