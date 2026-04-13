package com.nukuhack.modforge;

import com.nukuhack.modforge.backend.ModData;
import com.nukuhack.modforge.backend.model.E;
import com.nukuhack.modforge.backend.service.ServiceRegistry;
import com.nukuhack.modforge.frontend.MainWindow;
import com.nukuhack.util.IOUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@UtilityClass
public class Singleton {
	@Getter
	private final Path userConfigDir = Util.getConfigDir();
	@Getter
	private final Path userConfig = userConfigDir.resolve("userconfig.json");
	@Getter
	private final Map<E.Language, Map<String, String>> langMap = new EnumMap<>(E.Language.class);
	@Getter
	private final ModData game = new ModData(
		"kdc2", "Kingdom Come Deliverance 2",
		"The game itself : Kingdom Come Deliverance II",
		"Warhorse Studios",
		"1.*", "2026", true
	);
	@Setter
	@Getter
	private ServiceRegistry registry;
	@Setter
	@Getter
	private MainWindow mainWindow;
	
	static {
		game.setSupportsGameVersions(List.of("*"));
		
		try {
			IOUtil.ensureDirExists(userConfig);
		} catch (IOException e) {
			log.error("Failed to create config directory: {}", userConfig, e);
		}
	}
}