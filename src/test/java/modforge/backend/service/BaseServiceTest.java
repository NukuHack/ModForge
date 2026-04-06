// In BaseServiceTest.java - fix the static mock and temp dir initialization

package modforge.backend.service;

import modforge.Util;
import modforge.backend.model.Language;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public abstract class BaseServiceTest {
	
	// ================================================================
	// Temp directory management
	// ================================================================
	
	@TempDir
	protected static Path tempDir;
	
	protected static Path tmp;
	
	// Make this non-static to avoid static mock issues, or remove if not needed
	// @Mock
	// protected static UserService userService;  // Static mocks cause problems
	
	protected void assumeResourcesOutputWritable() {
		var _ = RESOURCES_OUTPUT;
	}
	
	protected static final Path RESOURCES_OUTPUT = Paths.get("src/test/resources/out");
	
	@BeforeAll
	static void initBaseTempDir() {
		tmp = tempDir.resolve("tmp");
		try {
			Files.createDirectories(tmp);
		} catch (IOException e) {
			throw new RuntimeException("Failed to create tmp directory", e);
		}
	}
	
	@AfterAll
	static void cleanupBaseTempDir() {
		if (tmp != null && Files.exists(tmp)) {
			Util.deleteRecursively(tmp);
		}
	}
	
	protected Path createTestDir(String name) throws IOException {
		Path dir = tempDir.resolve(name);
		Files.createDirectories(dir);
		return dir;
	}
	
	// ================================================================
	// Resource loading (now instance methods that use static fields)
	// ================================================================
	
	protected static byte[] readResourceBytes(String resourceName) throws IOException {
		try (InputStream is = BaseServiceTest.class.getClassLoader().getResourceAsStream(resourceName)) {
			if (is != null) {
				return is.readAllBytes();
			}
		}
		
		Path path = Paths.get("src/test/resources/" + resourceName);
		if (Files.exists(path)) {
			return Files.readAllBytes(path);
		}
		
		throw new FileNotFoundException("Resource not found: " + resourceName);
	}
	
	protected String readResourceString(String resourceName) throws IOException {
		return new String(readResourceBytes(resourceName), StandardCharsets.UTF_8);
	}
	
	protected boolean resourceExists(String resourceName) {
		try (InputStream is = BaseServiceTest.class.getClassLoader().getResourceAsStream(resourceName)) {
			if (is != null) return true;
		} catch (IOException e) {
			// fall through
		}
		return Files.exists(Paths.get("src/test/resources/" + resourceName));
	}
	
	protected Path copyResourceToTemp(String resourceName, String targetFileName) throws IOException {
		byte[] content = readResourceBytes(resourceName);
		Path target = tempDir.resolve(targetFileName);
		Files.write(target, content);
		return target;
	}
	
	// ================================================================
	// Common test data - now instance fields, loaded lazily or via init
	// ================================================================
	
	protected byte[] modCfgBytes;
	protected byte[] autoexecCfgBytes;
	protected byte[] itemXmlBytes;
	protected byte[] perkDataPakBytes;
	protected byte[] userConfigJsonBytes;
	protected byte[] englishPakBytes;
	protected byte[] engLocalXmlBytes;
	protected boolean commonResourcesLoaded = false;
	
	@BeforeEach
	protected void loadCommonResources() throws IOException {
		if (commonResourcesLoaded) return;
		modCfgBytes = readResourceBytes("cfg/mod.cfg");
		autoexecCfgBytes = readResourceBytes("cfg/autoexec.cfg");
		itemXmlBytes = readResourceBytes("item_xml/item__lootinfo.xml");
		perkDataPakBytes = readResourceBytes("item_xml/perk-data.pak");
		userConfigJsonBytes = readResourceBytes("json/userconfig.json");
		englishPakBytes = readResourceBytes("lang_xml/English_xml.pak");
		engLocalXmlBytes = readResourceBytes("lang_xml/eng-local.xml");
		commonResourcesLoaded = true;
	}
	
	// ================================================================
	// Service stub helpers
	// ================================================================
	
	protected UserConfig createStubUserService(String gameDir) {
		UserConfig us = new UserConfig();
		us.gameDirectory = gameDir;
		us.language = Language.ENGLISH;
		return us;
	}
	
	protected UserConfig createStubUserService(String gameDir, Language language) {
		UserConfig us = new UserConfig();
		us.gameDirectory = gameDir;
		us.language = language;
		return us;
	}
	
	// ================================================================
	// Config file parsing helper
	// ================================================================
	
	protected Map<String, String> parseConfigBytes(byte[] cfgBytes) throws Exception {
		Path file = tmp.resolve("cfg.cfg");
		Files.write(file, cfgBytes);
		
		Map<String, String> result = new java.util.LinkedHashMap<>();
		ConfigService.loadConfigFile(file, result);
		return result;
	}
	
	protected Map<String, String> parseConfigString(String content) throws Exception {
		return parseConfigBytes(content.getBytes(StandardCharsets.UTF_8));
	}
	
	// ================================================================
	// Assertion helpers (now instance methods)
	// ================================================================
	
	protected void assertDirectoryContainsExtension(Path dir, String extension) throws IOException {
		assertTrue(Files.exists(dir), "Directory does not exist: " + dir);
		long count = Files.list(dir)
							 .filter(p -> p.getFileName().toString().endsWith(extension))
							 .count();
		assertTrue(count > 0, "Directory " + dir + " contains no files with extension " + extension);
	}
	
	protected void assertNonEmptyFile(Path file) throws IOException {
		assertTrue(Files.exists(file), "File does not exist: " + file);
		assertTrue(Files.size(file) > 0, "File is empty: " + file);
	}
	
	protected <K, V> void assertNonEmptyMap(Map<K, V> map, String message) {
		assertNotNull(map, message + " - map is null");
		assertFalse(map.isEmpty(), message + " - map is empty");
	}
	
	protected void cleanUp() {
		// Default: no-op
	}
}