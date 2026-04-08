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
	public final ModService modService;
	
	public ServiceRegistry() {
		userConfig = new UserConfig.UserConfigImpl();
		configService = new ConfigService(userConfig);
		localService = new LocalService(userConfig);
		itemService = new ItemService(userConfig);
		iconService = new IconService(userConfig);
		modService = new ModService(this);
		
		// used to load main game data
		userConfig.load();
		localService.init();
		itemService.init();
		// DISABLING since it uses up over 3gigs of memory, so i will rework it
		// TODO : rework
		//iconService.init();
		
		// used to load mod data
		modService.init();
	}
	
	/**
	 * Convenience method: set the game directory and reload everything.
	 */
	public void init() {
		userConfig.save();
		
		localService.init();
		itemService.init();
		// TODO : rework
		//iconService.init();
		
		modService.init();
		Singleton.INSTANCE.getGame().setConfig(configService.loadGameConfig());
	}
}
