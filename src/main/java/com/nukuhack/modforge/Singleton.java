package com.nukuhack.modforge;

import com.nukuhack.modforge.backend.ModData;
import com.nukuhack.modforge.backend.service.ServiceRegistry;
import com.nukuhack.modforge.frontend.MainWindow;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@lombok.extern.slf4j.Slf4j
public enum Singleton {
	INSTANCE;
	@Getter
	private static final Path userConfigDir = Util.getConfigDir();
	@Getter
	private static final Path userConfig = userConfigDir.resolve("userconfig.json");
	@Getter
	private final ModData game = new ModData();
	@Setter
	@Getter
	private ServiceRegistry registry;
	@Setter
	@Getter
	private MainWindow mainWindow;
	
	static {
		ensureConfigDirExists();
	}
	
	{
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
			log.info("Created config directory: {}", dir);
		} catch (final IOException e) {
			log.error("Failed to create config directory: {}", dir, e);
		}
	}
}