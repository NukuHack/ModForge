package modforge.backend.service;

import modforge.Singleton;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Wires all dependencies together in the correct order.
 * Use as the single entry-point for bootstrapping the application.
 */
public final class ServiceRegistry {
	private static final Logger log = Logger.getLogger(ServiceRegistry.class.getName());

	public final UserService userConfig;
	public final ConfigService configService;
	public final LocalizationService localizationService;
	public final ModItemBuilder builder;
	public final JsonAdapter jsonAdapter;
	public final ItemService itemService;
	public final IconService iconService;
	public final ModService modService;

	public ServiceRegistry() {
		userConfig = new UserService();
		configService = new ConfigService(userConfig);
		localizationService = new LocalizationService(userConfig);
		builder = ModItemBuilder.createDefault();
		jsonAdapter = new JsonAdapter(resolveAppDataDir());
		itemService = new ItemService(userConfig, builder);
		iconService = new IconService(userConfig);
		modService = new ModService(this);
	}

	/**
	 * Convenience method: set the game directory and reload everything.
	 */
	public void init(String path) {
		var config = userConfig.getCurrent();
		if (config.gameDirectory.isBlank())
			config.gameDirectory = Objects.requireNonNull(path);
		userConfig.save();
		localizationService.init();
		itemService.init();
		// Load game config after game directory is set
		Singleton.INSTANCE.game().config = configService.loadGameConfig();
	}

	private static String resolveAppDataDir() {
		String appData = System.getenv("APPDATA");
		return (appData != null && !appData.isBlank())
				? appData
				: System.getProperty("user.home") + "/AppData/Roaming";
	}
}