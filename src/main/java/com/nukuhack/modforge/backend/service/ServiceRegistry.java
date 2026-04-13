package com.nukuhack.modforge.backend.service;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Wires all dependencies together in the correct order.
 * Use as the single entry-point for bootstrapping the application.
 */
@Slf4j
public final class ServiceRegistry {
	@NonNull
	public final UserConfig userConfig;
	@NonNull
	public final ConfigService configService;
	@NonNull
	public final LocalService localService;
	@NonNull
	public final ItemService itemService;
	@NonNull
	public final IconService iconService;
	@NonNull
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
			// DISABLING since it uses up over 3gigs of memory, so i will rework it
			// TODO : rework
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
		// TODO : rework
		//iconService.init();
		
		modService.init();
	}
}
