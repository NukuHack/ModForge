package modforge.backend.service;

/**
 * Wires all dependencies together in the correct order.
 * Use as the single entry-point for bootstrapping the application.
 */
public final class ServiceRegistry {
	public final UserService userConfig;
	public final LocalizationService localizationService;
	public final ModItemBuilder builder;
	public final JsonAdapter jsonAdapter;
	public final ItemService itemService;
	public final IconService iconService;
	public final ModService modService;

	public ServiceRegistry() {
		this("about:blank");
	}

	public ServiceRegistry(String initialUri) {
		userConfig = new UserService();
		var localizationAdapter = new LocalizationAdapter();
		localizationService = new LocalizationService(localizationAdapter, userConfig);

		builder = ModItemBuilder.createDefault();
		var itemAdapter = new ItemAdapter(userConfig, builder);
		jsonAdapter = new JsonAdapter(resolveAppDataDir());

		itemService = new ItemService(itemAdapter, userConfig);
		iconService = new IconService(userConfig);
		modService = new ModService(itemService, userConfig, localizationService);
	}

	/**
	 * Convenience method: set the game directory and reload everything.
	 * Equivalent to the user browsing to their game folder in the UI.
	 */
	public void init(String path) {
		var config = userConfig.getCurrent();
		if (config.gameDirectory.isBlank())
			config.gameDirectory = path;
		userConfig.save();
		localizationService.init();
		itemService.tryReadXmlFiles();
		modService.initiateModCollections();
	}

	private static String resolveAppDataDir() {
		String appData = System.getenv("APPDATA");
		return (appData != null && !appData.isBlank())
				? appData
				: System.getProperty("user.home") + "/AppData/Roaming";
	}
}
