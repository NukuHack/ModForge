package modforge;

import modforge.backend.ModData;
import modforge.backend.service.ServiceRegistry;
import modforge.frontend.MainWindow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public enum Singleton {
	INSTANCE;

	public static final String APP_NAME = "ModForge";

	private ServiceRegistry registry;
	private MainWindow mainWindow;
	private final Path userConfigDir;
	private final Path userConfigFile;
	private final ModData game = new ModData();

	Singleton() {
		this.userConfigDir = getPlatformConfigDir();
		this.userConfigFile = userConfigDir.resolve("userconfig.json");
		ensureConfigDirExists();

		game.name = "Kingdom Come Deliverance 2";
		game.description = "The game itself : Kingdom Come Deliverance II";
		game.author = "warhorse studios";
		game.modVersion = "1.*";
		game.createdOn = "2025";
		game.id = "kdc2";
		game.modifiesLevel = true;
		game.supportsGameVersions.add("*");
	}

	private Path getPlatformConfigDir() {
		String os = System.getProperty("os.name").toLowerCase();

		if (os.contains("win")) {
			// Windows: %APPDATA%\ {APP_NAME}
			String appData = System.getenv("APPDATA");
			if (appData == null || appData.isBlank()) {
				appData = System.getProperty("user.home") + "\\AppData\\Roaming";
			}
			return Paths.get(appData, APP_NAME);

		} else if (os.contains("mac")) {
			// macOS: ~/Library/Application Support/ {APP_NAME}
			return Paths.get(System.getProperty("user.home"),
					"Library", "Application Support", APP_NAME);

		} else {
			// Linux/Unix: ~/.config/ {APP_NAME} (XDG Base Directory Specification)
			String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
			if (xdgConfigHome != null && !xdgConfigHome.isBlank()) {
				return Paths.get(xdgConfigHome, APP_NAME.toLowerCase());
			}
			// Fallback to ~/.config/ {APP_NAME}
			return Paths.get(System.getProperty("user.home"), ".config", APP_NAME.toLowerCase());
		}
	}

	private void ensureConfigDirExists() {
		try {
			if (!Files.exists(userConfigDir)) {
				Files.createDirectories(userConfigDir);
				System.out.println("Created config directory: " + userConfigDir);
			}
		} catch (final IOException e) {
			throw new RuntimeException("Failed to create config directory: " + userConfigDir, e);
		}
	}

	public void setRegistry(ServiceRegistry registry) {
		this.registry = registry;
	}

	public ServiceRegistry getRegistry() {
		return registry;
	}

	public MainWindow getMainWindow() {
		return mainWindow;
	}

	public void setMainWindow(MainWindow mainWindow) {
		this.mainWindow = mainWindow;
	}

	public Path getUserConfig() {
		return userConfigFile;
	}

	public ModData game() {
		return game;
	}
}