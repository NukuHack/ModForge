package com.nukuhack.modforge.backend.service;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Wires all dependencies together in the correct order.
 * Use as the single entry-point for bootstrapping the application.
 */
@Slf4j
@NonNull
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
		
		userConfig.load();
		if (userConfig.isAutoLoadGameData()) {
			configService.init();
			localService.init();
			itemService.init();
			// todo : rework
			// iconService.init();
		}
		
		modService.init();
	}
	
	/**
	 * Convenience method: set the game directory and reload everything.
	 */
	public void init() {
		userConfig.save();
		
		configService.init();
		localService.init();
		itemService.init();
		
		modService.init();
	}

	public void shutdown() {
		userConfig.save();

	}
}

