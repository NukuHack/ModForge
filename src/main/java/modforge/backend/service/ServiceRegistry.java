package modforge.backend.service;

import modforge.Singleton;

import java.util.logging.Logger;

/**
 * Wires all dependencies together in the correct order.
 * Use as the single entry-point for bootstrapping the application.
 */
public final class ServiceRegistry {
	private static final Logger log = Logger.getLogger(ServiceRegistry.class.getName());

	public final UserService userConfig;
	public final ConfigService configService;
	public final LocalService localService;
	public final ModItemBuilder builder;
	public final JsonAdapter jsonAdapter;
	public final ItemService itemService;
	public final IconService iconService;
	public final StormService stormService;
	public final ModService modService;

	public ServiceRegistry() {
		userConfig = new UserService();
		configService = new ConfigService(userConfig);
		localService = new LocalService(userConfig);
		builder = ModItemBuilder.createDefault();
		jsonAdapter = new JsonAdapter(resolveDataDir());
		itemService = new ItemService(userConfig, builder);
		iconService = new IconService(userConfig);
		stormService = new StormService(userConfig);
		modService = new ModService(this);
	}

	/**
	 * Convenience method: set the game directory and reload everything.
	 */
	public void init() {
		userConfig.save();
		localService.init();
		jsonAdapter.setBaseDir(userConfig.gameDirectory);
		itemService.init();
		iconService.init();
		stormService.init();
		modService.init();
		Singleton.INSTANCE.game().setConfig(configService.loadGameConfig());
	}

	private static String resolveDataDir() {
		final String appData = System.getenv("APPDATA");
		return (appData != null && !appData.isBlank()) ? appData : System.getProperty("user.home") + "/AppData/Roaming";
	}
}
