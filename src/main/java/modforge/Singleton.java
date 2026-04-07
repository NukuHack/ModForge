package modforge;

import modforge.backend.ModData;
import modforge.backend.service.ServiceRegistry;
import modforge.frontend.MainWindow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@lombok.extern.slf4j.Slf4j
public enum Singleton {
	INSTANCE;
	
	private static final Path userConfigDir = Util.getConfigDir();
	private static final Path userConfig = userConfigDir.resolve("userconfig.json");
	private final ModData game = new ModData();
	private ServiceRegistry registry;
	private MainWindow mainWindow;
	
	static {
		ensureConfigDirExists();
	}
	
	Singleton() {
		game.name = "Kingdom Come Deliverance 2";
		game.description = "The game itself : Kingdom Come Deliverance II";
		game.author = "warhorse studios";
		game.modVersion = "1.*";
		game.createdOn = "2025";
		game.id = "kdc2";
		game.modifiesLevel = true;
		game.supportsGameVersions.add("*");
	}
	
	private static void ensureConfigDirExists() {
		final var dir = userConfig.getParent();
		try {
			if (Files.exists(dir))
				return;
			Files.createDirectories(dir);
			System.out.println("Created config directory: " + dir);
		} catch (final IOException e) {
			throw new RuntimeException("Failed to create config directory: " + dir, e);
		}
	}
	
	public ServiceRegistry getRegistry() {
		return registry;
	}
	
	public void setRegistry(ServiceRegistry registry) {
		this.registry = registry;
	}
	
	public MainWindow getMainWindow() {
		return mainWindow;
	}
	
	public void setMainWindow(MainWindow mainWindow) {
		this.mainWindow = mainWindow;
	}
	
	public static Path getUserConfig() {
		return userConfig;
	}
	
	public ModData game() {
		return game;
	}
}