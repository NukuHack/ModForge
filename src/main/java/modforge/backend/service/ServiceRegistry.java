package modforge.backend.service;

import modforge.Singleton;

/**
 * Wires all dependencies together in the correct order.
 * Use as the single entry-point for bootstrapping the application.
 */
@lombok.extern.slf4j.Slf4j
public final class ServiceRegistry {
	
	public final UserConfig userConfig;
	public final ConfigService configService;
	public final LocalService localService;
	public final ItemService itemService;
	public final IconService iconService;
	public final StormService stormService;
	public final ModService modService;
	
	public ServiceRegistry() {
		userConfig = new UserConfig.UserConfigImpl();
		userConfig.load();
		
		configService = new ConfigService(userConfig);
		localService = new LocalService(userConfig);
		localService.init();
		itemService = new ItemService(userConfig);
		itemService.init();
		iconService = new IconService(userConfig);
		iconService.init();
		stormService = new StormService(userConfig);
		
		modService = new ModService(this);
	}
	
	/**
	 * Convenience method: set the game directory and reload everything.
	 */
	public void init() {
		userConfig.save();
		localService.init();
		itemService.init();
		iconService.init();
		stormService.init();
		modService.init();
		Singleton.INSTANCE.getGame().setConfig(configService.loadGameConfig());
	}
}
