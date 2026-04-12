package com.nukuhack.modforge;

import com.nukuhack.modforge.backend.ModData;
import com.nukuhack.modforge.backend.model.E;
import com.nukuhack.modforge.backend.service.ServiceRegistry;
import com.nukuhack.modforge.frontend.MainWindow;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@UtilityClass
public class Singleton {
	private final Path userConfigDir = Util.getConfigDir();
	private final Path userConfig = userConfigDir.resolve("userconfig.json");
	private final Map<E.Language, Map<String, String>> langMap = new EnumMap<>(E.Language.class);
	private final ModData game = new ModData();
	private ServiceRegistry registry;
	private MainWindow mainWindow;
	
	static {
		ensureConfigDirExists();
		
		game.name = "Kingdom Come Deliverance 2";
		game.description = "The game itself : Kingdom Come Deliverance II";
		game.author = "warhorse studios";
		game.modVersion = "1.*";
		game.createdOn = "2025";
		game.id = "kdc2";
		game.modifiesLevel = true;
		game.setSupportsGameVersions(List.of("*"));
	}
	
	private void ensureConfigDirExists() {
		final var dir = userConfig.getParent();
		try {
			if (Files.exists(dir))
				return;
			Files.createDirectories(dir);
			log.info("Created config directory: {}", dir);
		} catch (final IOException e) {
			log.error("Failed to create config directory: {}", dir, e);
		}
	}
	
	public Path getUserConfigDir() {
		return userConfigDir;
	}
	
	public MainWindow getMainWindow() {
		return mainWindow;
	}
	
	public ServiceRegistry getRegistry() {
		return registry;
	}
	
	public ModData getGame() {
		return game;
	}
	
	public Map<E.Language, Map<String, String>> getLangMap() {
		return langMap;
	}
	
	public Path getUserConfig() {
		return userConfig;
	}
	
	public void setRegistry(ServiceRegistry newRegistry) {
		registry = newRegistry;
	}
	
	public void setMainWindow(MainWindow newMainWindow) {
		mainWindow = newMainWindow;
	}
}