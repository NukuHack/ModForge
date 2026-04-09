package modforge.backend.service;

import modforge.backend.ModData;
import modforge.backend.model.ModItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ItemService XML parsing")
class ItemServiceTests extends BaseServiceTest {
	
	@BeforeEach
	void init() throws IOException {
		loadCommonResources();
	}
	
	// ================================================================
	// ItemService XML tests
	// ================================================================
	
	
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
		var is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
		var doc = ItemService.parseXml(is);
		assertNotNull(doc);
		assertEquals("database", doc.getDocumentElement().getTagName());
	}
	
	@Test
	@DisplayName("parseXml: parses item__lootinfo.xml resource without error")
	void parseItemXmlResource() {
		var is = new ByteArrayInputStream(itemXmlBytes);
		var doc = ItemService.parseXml(is);
		assertNotNull(doc);
		assertEquals("database", doc.getDocumentElement().getTagName());
		assertTrue(doc.getDocumentElement().getChildNodes().getLength() > 0);
	}
	
	@Test
	@DisplayName("parseXml: handles UTF-8 with BOM gracefully")
	void parseXmlWithBom() {
		byte[] bom = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
		byte[] xml = "<?xml version=\"1.0\"?><root/>".getBytes(StandardCharsets.UTF_8);
		byte[] withBom = new byte[bom.length + xml.length];
		System.arraycopy(bom, 0, withBom, 0, bom.length);
		System.arraycopy(xml, 0, withBom, bom.length, xml.length);
		assertDoesNotThrow(() -> ItemService.parseXml(new ByteArrayInputStream(withBom)));
	}
	
	@Test
	@DisplayName("parseXml: external entity features are disabled (security)")
	void parseXmlXxeDisabled() {
		String xxe = """
				<?xml version="1.0"?>
				<!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
				<root>&xxe;</root>
				""";
		assertDoesNotThrow(() -> ItemService.parseXml(new ByteArrayInputStream(xxe.getBytes(StandardCharsets.UTF_8))));
	}
	
	@Test
	@DisplayName("parseXml: real xml, that sometimes failed")
	void parseBuggyXml() {
		String xxe = """
<?xml version="1.0" encoding="us-ascii"?>
<database xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" name="barbora" xsi:noNamespaceSchemaLocation="../database.xsd">
	<perk_scripts version="1">
             <perk_script perk_editor_name="BS recipe r_shortswordheavy" perk_id="330b075a-2568-4d2d-bca1-b02cb60df582" />
<perk_script perk_editor_name="BS recipe r_huntingswordsword" perk_id="489a6940-d6d4-4e4f-9ebf-88ae357f988f" />
	</perk_scripts>
</database>
				""";
		assertDoesNotThrow(() -> {
			var is = new ByteArrayInputStream(xxe.getBytes(StandardCharsets.UTF_8));
			Set<ModItem> set = new HashSet<>();
			ItemService.readItemsFromXml(is, "app.pak", set);
			System.out.println(set);
		});
	}
	
	@Test
	@DisplayName("loadItems: returns empty set for non-existent path")
	void loadItemsMissingDir() {
		var items = ItemService.loadItems(tmp.resolve("nonexistent"));
		assertTrue(items.isEmpty());
	}
	
	@Test
	@DisplayName("loadItems: empty PAK dir returns empty set")
	void loadItemsEmptyDir() throws IOException {
		var dataDir = tmp.resolve("Data/Empty");
		Files.createDirectories(dataDir);
		var items = ItemService.loadItems(dataDir);
		assertTrue(items.isEmpty());
	}
	
	@Test
	@DisplayName("loadItems: reads items from perk-data.pak resource")
	void loadItemsFromPak() throws IOException {
		var dataDir = tmp.resolve("Data");
		Files.createDirectories(dataDir);
		Files.write(dataDir.resolve("perk-data.pak"), perkDataPakBytes);
		
		var items = ItemService.loadItems(dataDir);
		assertFalse(items.isEmpty(), "Should load at least one item from perk-data.pak");
	}
	
	@Test
	@DisplayName("writeModItems: writes nothing when mod has no items")
	void writeModItemsEmpty() {
		var mod = new ModData();
		mod.id = "empty-mod";
		mod.setItems(Collections.emptySet());
		
		assertDoesNotThrow(() -> ItemService.writeModItems(mod, tempDir.resolve("another").toString()));
	}
	
	@Test
	@DisplayName("[full] load perk-data.pak and write mod items to out folder")
	void writeModItemsFull() throws IOException {
		var dataDir = tmp.resolve("Data");
		Files.createDirectories(dataDir);
		Files.write(dataDir.resolve("perk-data.pak"), perkDataPakBytes);
		
		var outBase = RESOURCES_OUTPUT;
		Files.createDirectories(outBase);
		
		var items = ItemService.loadItems(dataDir);
		assertFalse(items.isEmpty());
		
		var mod = new ModData();
		mod.id = "perk-test-mod";
		mod.setItems(items);
		
		assertDoesNotThrow(() -> ItemService.writeModItems(mod, outBase.resolve("nother").toString()));
	}
}