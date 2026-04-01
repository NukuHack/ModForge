package modforge.backend.service;

/**
 * Wires all dependencies together in the correct order.
 * Use as the single entry-point for bootstrapping the application.
 */
public final class ServiceRegistry {
	public final UserConfigurationService userConfig;
	public final LocalizationAdapter localizationAdapter;
	public final LocalizationService localizationService;
	public final ModItemBuilder builder;
	public final XmlAdapter xmlAdapter;
	public final JsonAdapter jsonAdapter;
	public final XmlService xmlService;
	public final IconService iconService;
	public final NavigationService navigationService;
	public final ModService modService;

	public ServiceRegistry() {
		this("about:blank");
	}

	public ServiceRegistry(String initialUri) {
		userConfig = new UserConfigurationService();
		localizationAdapter = new LocalizationAdapter();
		localizationService = new LocalizationService(localizationAdapter, userConfig);

		builder = ModItemBuilder.createDefault();
		xmlAdapter = new XmlAdapter(userConfig, builder);
		jsonAdapter = new JsonAdapter(resolveAppDataDir());

		xmlService = new XmlService(xmlAdapter, localizationService, userConfig);
		iconService = new IconService(userConfig);
		navigationService = new NavigationService(initialUri);
		modService = new ModService(xmlAdapter, userConfig, localizationService);
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
		xmlService.tryReadXmlFiles();
		modService.initiateModCollections();

		System.out.printf(
				"Loaded: %d perks, %d buffs, %d weapons, %d armors%n",
				xmlService.perks.size(),
				xmlService.buffs.size(),
				xmlService.weapons.size(),
				xmlService.armors.size()
		);
	}

	private static String resolveAppDataDir() {
		String appData = System.getenv("APPDATA");
		return (appData != null && !appData.isBlank())
				? appData
				: System.getProperty("user.home") + "/AppData/Roaming";
	}
}
