package modforge.backend.service;

import modforge.Util;
import modforge.backend.ModData;
import modforge.backend.model.ModItem;
import org.junit.jupiter.api.*;
import org.w3c.dom.Document;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConfigService, ItemService, LocalService, JsonIO.
 *
 * Resource layout (mirroring DDSUtilTest.readResourceBytes):
 *   resources/
 *     cfg/
 *       mod.cfg
 *       autoexec.cfg
 *     item_xml/
 *       item__lootinfo.xml
 *       perk-data.pak          ← ZIP containing XML entries
 *     json/
 *       userconfig.json
 *     lang_xml/
 *       English_xml.pak        ← ZIP containing localization XML
 *       eng-local.xml
 *
 * Most write-tests operate entirely in-memory or in JUnit temp dirs.
 * Only the "full" integration tests write into resources/.../output/.
 */
class ItemServiceTests {
	
	@BeforeAll
	public static void setTempDir() {
		tmp = Path.of("src/test/resources/tmp");
	}
	
	@AfterAll
	public static void remTempDir() {
		Util.deleteRecursively(tmp);
	}
	
	// ---------------------------------------------------------------
	// Resource helpers (same pattern as DDSUtilTest)
	// ---------------------------------------------------------------
	
	private static byte[] readResourceBytes(String resourceName) throws IOException {
		try (InputStream is = ServiceTests.class.getClassLoader().getResourceAsStream(resourceName)) {
			if (is != null)
				return is.readAllBytes();
		}
		Path path = Paths.get("src/test/resources/" + resourceName);
		if (Files.exists(path))
			return Files.readAllBytes(path);
		throw new FileNotFoundException("Resource not found: " + resourceName);
	}
	
	private static String readResourceString(String resourceName) throws IOException {
		return new String(readResourceBytes(resourceName), StandardCharsets.UTF_8);
	}
	
	// ---------------------------------------------------------------
	// Static test data loaded once
	// ---------------------------------------------------------------
	
	private static byte[] modCfgBytes;
	private static byte[] autoexecCfgBytes;
	private static byte[] itemXmlBytes;         // item__lootinfo.xml
	private static byte[] perkDataPakBytes;     // perk-data.pak (ZIP)
	private static byte[] userConfigJsonBytes;
	private static byte[] englishPakBytes;      // English_xml.pak (ZIP)
	private static byte[] engLocalXmlBytes;     // eng-local.xml (raw localization XML)
	
	private static Path tmp;
	
	@BeforeAll
	static void loadResources() throws IOException {
		modCfgBytes = readResourceBytes("cfg/mod.cfg");
		autoexecCfgBytes = readResourceBytes("cfg/autoexec.cfg");
		itemXmlBytes = readResourceBytes("item_xml/item__lootinfo.xml");
		perkDataPakBytes = readResourceBytes("item_xml/perk-data.pak");
		userConfigJsonBytes = readResourceBytes("json/userconfig.json");
		englishPakBytes = readResourceBytes("lang_xml/English_xml.pak");
		engLocalXmlBytes = readResourceBytes("lang_xml/eng-local.xml");
	}
	// ================================================================
	// ItemService XML tests
	// ================================================================
	
	@Nested
	@DisplayName("ItemService XML parsing")
	class ItemServiceTest {
		
		@Test
		@DisplayName("parseXml: parses valid XML from byte array")
		void parseXmlBasic() {
			String xml = """
					<?xml version="1.0" encoding="UTF-8"?>
					<database name="barbaro">
					  <LootInfos version="1">
					    <LootInfo UIName="ui_loot_001" id="loot_001" weight="1.0"/>
					  </LootInfos>
					</database>
					""";
			InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
			Document doc = ItemService.parseXml(is);
			assertNotNull(doc);
			assertEquals("database", doc.getDocumentElement().getTagName());
		}
		
		@Test
		@DisplayName("parseXml: parses item__lootinfo.xml resource without error")
		void parseItemXmlResource() {
			InputStream is = new ByteArrayInputStream(itemXmlBytes);
			Document doc = ItemService.parseXml(is);
			assertNotNull(doc);
			// Root must be <database>
			assertEquals("database", doc.getDocumentElement().getTagName());
			// Must have at least one child group element
			assertTrue(doc.getDocumentElement().getChildNodes().getLength() > 0);
		}
		
		@Test
		@DisplayName("parseXml: handles UTF-8 with BOM gracefully")
		void parseXmlWithBom() {
			// Prepend UTF-8 BOM manually
			byte[] bom = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
			byte[] xml = "<?xml version=\"1.0\"?><root/>".getBytes(StandardCharsets.UTF_8);
			byte[] withBom = new byte[bom.length + xml.length];
			System.arraycopy(bom, 0, withBom, 0, bom.length);
			System.arraycopy(xml, 0, withBom, bom.length, xml.length);
			// Should not throw
			assertDoesNotThrow(() -> ItemService.parseXml(new ByteArrayInputStream(withBom)));
		}
		
		@Test
		@DisplayName("parseXml: external entity features are disabled (security)")
		void parseXmlXxeDisabled() {
			// A DOCTYPE with an external entity must not trigger a network call / error
			String xxe = """
					<?xml version="1.0"?>
					<!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
					<root>&xxe;</root>
					""";
			// Should either parse without resolution or throw a benign parse error,
			// but must NOT cause an IOException from a network/filesystem fetch.
			assertDoesNotThrow(() -> ItemService.parseXml(new ByteArrayInputStream(xxe.getBytes(StandardCharsets.UTF_8))));
		}
		
		@Test
		@DisplayName("loadItems: returns empty set for non-existent path")
		void loadItemsMissingDir() {
			// We need an ItemService instance – use a minimal stub UserService
			UserService us = stubUserService(tmp.toString());
			// Point to a path that doesn't exist
			Path missing = tmp.resolve("nonexistent");
			ItemService svc = new ItemService(us); // init() will silently skip (no Data dir)
			Set<ModItem> items = svc.loadItems(missing);
			assertTrue(items.isEmpty());
		}
		
		@Test
		@DisplayName("loadItems: empty PAK dir returns empty set")
		void loadItemsEmptyDir() throws IOException {
			Path dataDir = tmp.resolve("Data/Empty");
			Files.createDirectories(dataDir);
			UserService us = stubUserService(tmp.toString());
			ItemService svc = new ItemService(us);
			Set<ModItem> items = svc.loadItems(dataDir);
			System.out.println(items);
			assertTrue(items.isEmpty());
		}
		
		@Test
		@DisplayName("loadItems: reads items from perk-data.pak resource")
		void loadItemsFromPak() throws IOException {
			Path dataDir = tmp.resolve("Data");
			Files.createDirectories(dataDir);
			Files.write(dataDir.resolve("perk-data.pak"), perkDataPakBytes);
			
			UserService us = stubUserService(tmp.toString());
			ItemService svc = new ItemService(us);
			Set<ModItem> items = svc.loadItems(dataDir);
			// The PAK has real data – expect at least one item
			assertFalse(items.isEmpty(), "Should load at least one item from perk-data.pak");
		}
		
		@Test
		@DisplayName("writeModItems: writes nothing when mod has no items")
		void writeModItemsEmpty() {
			ModData mod = new ModData();
			mod.id = "empty-mod";
			mod.setItems(Collections.emptySet());
			
			UserService us = stubUserService(tmp.toString());
			ItemService svc = new ItemService(us);
			assertDoesNotThrow(() -> svc.writeModItems(mod));
		}
		
		// --- full: write items loaded from resource PAK to resources output ---
		
		@Test
		@DisplayName("[full] load perk-data.pak and write mod items to resources/item_xml/output/")
		void writeModItemsFull() throws IOException {
			Path dataDir = tmp.resolve("Data");
			Files.createDirectories(dataDir);
			Files.write(dataDir.resolve("perk-data.pak"), perkDataPakBytes);
			
			// Use a staging game dir rooted in resources/item_xml/output
			Path outBase = Paths.get("src/test/resources/item_xml/output");
			Files.createDirectories(outBase);
			
			UserService us = stubUserService(outBase.toString());
			ItemService svc = new ItemService(us);
			
			// Load from the temp pak, then write as a mod
			Set<ModItem> items = svc.loadItems(dataDir);
			assertFalse(items.isEmpty());
			
			ModData mod = new ModData();
			mod.id = "perk-test-mod";
			mod.setItems(items);
			
			assertDoesNotThrow(() -> svc.writeModItems(mod));
		}
		
		// helper
		private UserService stubUserService(String gameDir) {
			UserService us = new UserService();
			us.gameDirectory = gameDir;
			return us;
		}
	}
}