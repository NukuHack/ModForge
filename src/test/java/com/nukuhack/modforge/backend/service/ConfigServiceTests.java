package com.nukuhack.modforge.backend.service;

import com.nukuhack.modforge.backend.ModData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigService")
class ConfigServiceTests extends BaseServiceTest {
	
	@BeforeAll
	static void setUp() {
		// Need an instance to call loadCommonResources, but @BeforeAll is static
		// Workaround: create temporary instance or make loadCommonResources static
		// For simplicity, we'll call the static version after fixing BaseServiceTest
	}
	
	@BeforeEach
	void init() throws IOException {
		loadCommonResources();
	}
	
	@Test
	@DisplayName("parse mod.cfg resource – at least one key present")
	void parseModCfg() throws Exception {
		Map<String, String> cfg = parseConfigBytes(modCfgBytes);
		assertFalse(cfg.isEmpty(), "mod.cfg should contain at least one key=value entry");
	}
	
	@Test
	@DisplayName("parse autoexec.cfg resource – at least one key present")
	void parseAutoexecCfg() throws Exception {
		Map<String, String> cfg = parseConfigBytes(autoexecCfgBytes);
		assertFalse(cfg.isEmpty(), "autoexec.cfg should contain at least one key=value entry");
	}
	
	@Test
	@DisplayName("comments and blank lines are stripped")
	void commentsStripped() throws Exception {
		final var content = """
				; top-level comment
				# hash comment
				
				key1 = value1
				; inline comment line
				key2 = value2 ; trailing comment
				""";
		Map<String, String> cfg = parseConfigString(content);
		assertEquals(2, cfg.size());
		assertEquals("value1", cfg.get("key1"));
		assertEquals("value2", cfg.get("key2"));
	}
	
	@Test
	@DisplayName("mergeConfigs: source overwrites base")
	void mergeConfigs() {
		Map<String, String> base = new LinkedHashMap<>();
		base.put("a", "1");
		base.put("b", "2");
		Map<String, String> merge = Map.of("b", "99", "c", "3");
		ConfigService.mergeConfigs(base, merge);
		assertEquals("1", base.get("a"));
		assertEquals("99", base.get("b"));
		assertEquals("3", base.get("c"));
	}
	
	@Test
	@DisplayName("saveModConfig writes and can be re-parsed in temp dir")
	void saveAndReadModConfig() throws Exception {
		ModData mod = new ModData();
		mod.id = "my-mod";
		mod.setConfig(new LinkedHashMap<>(Map.of("g_difficulty", "2", "sys_spec", "4")));
		
		assertTrue(ConfigService.saveModConfig(tmp.toString(), mod));
		
		Path written = tmp.resolve("mod.cfg");
		assertNonEmptyFile(written);
		Map<String, String> readBack = parseConfigBytes(Files.readAllBytes(written));
		assertEquals("2", readBack.get("g_difficulty"));
		assertEquals("4", readBack.get("sys_spec"));
	}
	
	@Test
	@DisplayName("[full] save mod config to resources output dir (optional)")
	void saveModConfigFull() throws Exception {
		assumeResourcesOutputWritable();
		
		Map<String, String> cfg = parseConfigBytes(modCfgBytes);
		
		ModData mod = new ModData();
		mod.id = "resource-mod";
		mod.setConfig(cfg);
		
		Path outDir = RESOURCES_OUTPUT.resolve("cfg");
		Files.createDirectories(outDir);
		assertTrue(ConfigService.saveModConfig(outDir.toString(), mod));
		assertNonEmptyFile(outDir.resolve("mod.cfg"));
	}
}