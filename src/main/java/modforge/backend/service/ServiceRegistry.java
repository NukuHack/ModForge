package modforge.backend.service;

import modforge.Singleton;

import java.util.logging.Logger;

/**
 * Wires all dependencies together in the correct order.
 * Use as the single entry-point for bootstrapping the application.
 */
public final class ServiceRegistry {
	private static final Logger log = Logger.getLogger(ServiceRegistry.class.getName());
	
	public final UserConfig userConfig;
	public final ConfigService configService;
	public final LocalService localService;
	public final ItemService itemService;
	public final IconService iconService;
	public final StormService stormService;
	public final ModService modService;
	
	public ServiceRegistry() {
		userConfig = new UserConfig();
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
		Singleton.INSTANCE.game().setConfig(configService.loadGameConfig());
	}
}
