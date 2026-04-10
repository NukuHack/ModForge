package com.nukuhack.modforge;

import com.nukuhack.modforge.backend.ModData;
import com.nukuhack.modforge.backend.model.E;
import com.nukuhack.modforge.backend.service.ServiceRegistry;
import com.nukuhack.modforge.frontend.MainWindow;
import com.nukuhack.modforge.frontend.pages.KCDConverterGUI;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@lombok.extern.slf4j.Slf4j
public enum Singleton {
	INSTANCE;
	@Getter
	private static final Path userConfigDir = Util.getConfigDir();
	@Getter
	private static final Path userConfig = userConfigDir.resolve("userconfig.json");
	@Getter
	private static final Map<E.Language, Map<String, String>> langMap = new EnumMap<>(E.Language.class);
	@Getter
	private final ModData game = new ModData();
	@Setter
	@Getter
	private ServiceRegistry registry;
	@Setter
	@Getter
	private MainWindow mainWindow;
	private KCDConverterGUI imageEditor;
	
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
		game.setSupportsGameVersions(List.of("*"));
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
	
	public KCDConverterGUI getImageEditor() {
		if (imageEditor == null)
			imageEditor = new KCDConverterGUI();
		imageEditor.setVisible(true);
		return imageEditor;
	}
}