package modforge;

import modforge.backend.service.ServiceRegistry;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.IOException;

public enum Singleton {
	INSTANCE;

	private ServiceRegistry registry;
	private final Path userConfigDir;
	private final Path userConfigFile;

	Singleton() {
		this.userConfigDir = getPlatformConfigDir();
		this.userConfigFile = userConfigDir.resolve("userconfig.json");
		ensureConfigDirExists();
	}

	private Path getPlatformConfigDir() {
		String os = System.getProperty("os.name").toLowerCase();

		if (os.contains("win")) {
			// Windows: %APPDATA%\ModForge
			String appData = System.getenv("APPDATA");
			if (appData == null || appData.isBlank()) {
				appData = System.getProperty("user.home") + "\\AppData\\Roaming";
			}
			return Paths.get(appData, "ModForge");

		} else if (os.contains("mac")) {
			// macOS: ~/Library/Application Support/ModForge
			return Paths.get(System.getProperty("user.home"),
					"Library", "Application Support", "ModForge");

		} else {
			// Linux/Unix: ~/.config/modforge (XDG Base Directory Specification)
			String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
			if (xdgConfigHome != null && !xdgConfigHome.isBlank()) {
				return Paths.get(xdgConfigHome, "modforge");
			}
			// Fallback to ~/.config/modforge
			return Paths.get(System.getProperty("user.home"), ".config", "modforge");
		}
	}

	private void ensureConfigDirExists() {
		try {
			if (!Files.exists(userConfigDir)) {
				Files.createDirectories(userConfigDir);
				System.out.println("Created config directory: " + userConfigDir);
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to create config directory: " + userConfigDir, e);
		}
	}

	public void setRegistry(ServiceRegistry registry) {
		this.registry = registry;
	}

	public ServiceRegistry getRegistry() {
		return registry;
	}

	public Path getUserConfig() {
		return userConfigFile;
	}
}