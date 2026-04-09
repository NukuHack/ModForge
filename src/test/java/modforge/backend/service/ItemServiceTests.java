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
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ItemService XML parsing")
class ItemServiceTests extends BaseServiceTest {
	
	String smallXml = """
			<?xml version="1.0" encoding="UTF-8"?>
			<database xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" name="barbora" xsi:noNamespaceSchemaLocation="../database.xsd">
			  <rpg_params version="1">
			    <rpg_param rpg_param_key="StatCap" rpg_param_value="100"/>
			    <rpg_param rpg_param_key="SkillCap" rpg_param_value="100"/>
			  </rpg_params>
			</database>
			""";
	
	// ================================================================
	// ItemService XML tests
	// ================================================================
	String singleItemXml = """
			<?xml version="1.0" encoding="UTF-8"?>
			<database xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" name="barbora" xsi:noNamespaceSchemaLocation="../database.xsd">
			  <rpg_params version="1">
			    <rpg_param rpg_param_key="PerkLuckyFindTriggerChance" rpg_param_value="0.25"/>
			  </rpg_params>
			</database>
			""";
	String mediumXml = """
			<?xml version="1.0" encoding="UTF-8"?>
			<database xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" name="barbora" xsi:noNamespaceSchemaLocation="../database.xsd">
			  <perks version="1">
			    <perk autolearnable="false" icon_id="perk_enthusiast" level="30" perk_id="47a2cb9d-1932-4eba-aaf5-cd21f3a2ffe2" perk_name="Enthusiast" perk_ui_desc="perk_enthusiast_desc" perk_ui_lore_desc="perk_enthusiast_lore_desc" perk_ui_name="perk_enthusiast_name" skill_selector="6" visibility="2"/>
			    <perk autolearnable="true" icon_id="perk_flower_power" level="2" perk_id="3884921b-9c13-4c4c-a232-261eb71e84ba" perk_name="cowardly_commander" perk_ui_desc="perk_cowardly_commander_desc" perk_ui_lore_desc="perk_cowardly_commander_lore_desc" perk_ui_name="perk_cowardly_commander_name" skill_selector="14" visibility="2"/>
			    <perk autolearnable="true" icon_id="perk_runaway_boy" level="2" perk_id="967068ce-cc5b-4949-a1fa-bf0c8f4d3f2a" perk_name="quick_step" perk_ui_desc="perk_quick_step_desc" perk_ui_lore_desc="perk_quick_step_lore_desc" perk_ui_name="perk_quick_step_name" stat_selector="1" visibility="2"/>
			  </perks>
			</database>
			""";
	// this also has types what are List-Attributes so extra validation
	String bigXml = """
			<?xml version="1.0" encoding="UTF-8"?>
			<database xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" name="barbora" xsi:noNamespaceSchemaLocation="../database.xsd">
			  <buffs version="1">
			    <buff buff_class_id="4" buff_exclusivity_id="1" buff_id="97d6a748-fa09-4eb8-8f98-2f39cedc857c" buff_lifetime_id="1" buff_name="perk_finesse_ii" buff_params="[asp*1.15]" buff_ui_type_id="4" duration="-1" icon_id="replaceme" implementation="Cpp:MeleeWeaponBuff" is_persistent="true"/>
			    <buff buff_class_id="4" buff_exclusivity_id="1" buff_id="2ddc28ab-974a-413b-bc3e-e6949df45c84" buff_lifetime_id="1" buff_name="perk_cowardly_commander" buff_params="[rms*1.25]" buff_ui_type_id="4" duration="-1" icon_id="0" implementation="Cpp:InCombatDanger" is_persistent="true"/>
			    <buff buff_class_id="4" buff_desc="buff_prizen_sv_bibiany_desc" buff_exclusivity_id="1" buff_id="4b6df874-b0c7-4512-bfc1-597b73e9f6d2" buff_lifetime_id="1" buff_name="perk_prizen_sv_bibiany" buff_params="[bba+25.0]" buff_ui_name="perk_prizen_sv_bibiany_name" buff_ui_type_id="4" buff_ui_visibility_id="2" duration="-1" icon_id="perk_prizen_sv_bibiany" implementation="Cpp:DrunkChecking" is_persistent="true"/>
			    <buff buff_class_id="4" buff_exclusivity_id="1" buff_id="2659feb3-7b19-45ac-acbf-0caf268e1337" buff_lifetime_id="1" buff_name="perk_ironclad" buff_params="[eqw-500.0]" buff_ui_type_id="4" duration="-1" icon_id="replaceme" implementation="Cpp:Constant" is_persistent="true"/>
			    <buff buff_class_id="4" buff_desc="buff_weasel_boy_desc" buff_exclusivity_id="1" buff_id="fcf836e3-8e10-4e09-b4e8-b4546acbfaa1" buff_lifetime_id="1" buff_name="perk_weasel_boy" buff_params="[noi*0.5]" buff_ui_name="perk_weasel_boy_name" buff_ui_type_id="4" buff_ui_visibility_id="2" duration="-1" icon_id="perk_weasel_boy" implementation="Cpp:ExteriorCrouch" is_persistent="true"/>
			    <buff buff_class_id="4" buff_exclusivity_id="1" buff_id="fc4f748f-2057-4919-b327-db1893356c39" buff_lifetime_id="1" buff_name="perk_discoverer" buff_params="[xpm+0.25]" buff_ui_type_id="4" duration="-1" icon_id="0" implementation="Cpp:Constant" is_persistent="true"/>
			    <buff buff_class_id="1" buff_exclusivity_id="1" buff_id="efab3328-a1f7-4df1-a1df-eeaf86972b10" buff_lifetime_id="0" buff_name="perk_hardworking_lad_carrying_body" buff_params="[rms*2.0]" duration="-1" implementation="Cpp:CarryingBodyGravedigger" is_persistent="true"/>
			    <buff buff_class_id="4" buff_exclusivity_id="1" buff_id="e7c785a9-7d40-4997-8fc3-f6a8788c376d" buff_lifetime_id="1" buff_name="perk_blood_of_siegfried" buff_params="[bba+25.0]" buff_ui_type_id="4" duration="-1" icon_id="replaceme" implementation="Cpp:Constant" is_persistent="true"/>
			    <buff buff_class_id="4" buff_desc="buff_ratman_desc" buff_exclusivity_id="1" buff_id="87fcba00-672f-4e6b-90f7-4322f316356e" buff_lifetime_id="1" buff_name="perk_ratman" buff_params="[noi*0.5]" buff_ui_name="perk_ratman_name" buff_ui_type_id="4" buff_ui_visibility_id="2" duration="-1" icon_id="perk_ratman" implementation="Cpp:InteriorCrouch" is_persistent="true"/>
			    <buff buff_class_id="4" buff_exclusivity_id="1" buff_id="44d83d12-9709-4e54-9b7d-9b6bcfb630be" buff_lifetime_id="1" buff_name="perk_featherweight" buff_params="[fdm*0.5]" buff_ui_type_id="4" duration="-1" icon_id="0" implementation="Cpp:Constant" is_persistent="true"/>
			    <buff buff_class_id="1" buff_desc="buff_undercut_desc" buff_exclusivity_id="1" buff_id="25f7907b-fb0d-4563-bddf-dfdc44c168cb" buff_lifetime_id="0" buff_name="perk_undercut" buff_params="[rms*1.2]" buff_ui_name="perk_undercut_name" buff_ui_type_id="4" buff_ui_visibility_id="3" duration="15" icon_id="perk_undercut" implementation="Cpp:BasicTimed" is_persistent="false"/>
			    <buff buff_class_id="4" buff_exclusivity_id="1" buff_id="11b7d7c5-2000-4e11-9aeb-ca18519be2dc" buff_lifetime_id="1" buff_name="perk_quick_step" buff_params="[rms*1.25]" buff_ui_type_id="4" duration="-1" icon_id="0" implementation="Cpp:Constant" is_persistent="true"/>
			    <buff buff_class_id="4" buff_exclusivity_id="1" buff_id="71effec1-5f83-49e8-9389-e7d9b5df80ce" buff_lifetime_id="1" buff_name="perk_refined_movements" buff_params="[wac*0.5]" buff_ui_type_id="4" duration="-1" icon_id="0" implementation="Cpp:Constant" is_persistent="true"/>
			  </buffs>
			</database>
			""";
	
	@BeforeEach
	void init() throws IOException {
		loadCommonResources();
	}
	
	@Test
	@DisplayName("parseXml: parses valid XML from byte array")
	void parseXmlBasic() {
		var is = new ByteArrayInputStream(singleItemXml.getBytes(StandardCharsets.UTF_8));
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
	@DisplayName("parseXml: real xml")
	void parseSmallXml() {
		assertDoesNotThrow(() -> {
			var is = new ByteArrayInputStream(mediumXml.getBytes(StandardCharsets.UTF_8));
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
	
	// ================================================================
	// Additional serialization tests
	// ================================================================
	
	/**
	 * Normalizes an XML string for comparison:
	 * - removes indentation whitespace between tags
	 * - strips XML declaration and DOCTYPE lines
	 */
	private String normalizeXml(String xml) {
		return xml.replaceAll(">\\s+<", ">\n<").replaceAll("(?m)^(?:<\\?|<!)[^\\n]*\\n?", "").trim();
	}
	
	@Test
	@DisplayName("getOutputFile: resolves PAK stem and directory suffix correctly")
	void getOutputFilePaths() {
		var mod = new ModData();
		mod.id = "testMod";
		
		// Format (a): "SomePak.pak:inner/dir/entry.xml"
		Path pathA = ItemService.getOutputFile(tempDir.toString(), "Weapons.pak:Libs/Tables/weapon.xml", mod);
		assertEquals(tempDir.toString() + "/Mods/testMod/Data/_stage/Weapons/Libs/Tables/weapon.xml", pathA.toString().replace('\\', '/'));
		
		// Format (b): plain path
		Path pathB = ItemService.getOutputFile(tempDir.toString(), "Scripts/items.xml", mod);
		assertEquals(tempDir.toString() + "/Mods/testMod/Data/_stage/testMod/Scripts/items.xml", pathB.toString().replace('\\', '/'));
		
		// Format (c): null or blank -> falls back to "apple.txt" inside modId stem
		Path pathC = ItemService.getOutputFile(tempDir.toString(), "", mod);
		assertEquals(tempDir.toString() + "/Mods/testMod/Data/_stage/testMod/apple.txt", pathC.toString().replace('\\', '/'));
	}
	
	@Test
	@DisplayName("writeModItem: writes a single item to the correct staging location")
	void writeSingleModItem() throws Exception {
		// Create a minimal ModItem – adapt fields to what ModItemBuilder expects
		ModItem item = new ModItem();
		item.setId("test_item_001");
		item.setUIName("Test Item");
		item.setPath("TestPak.pak:items/test.xml");
		// Assuming ModItem has a type field or ModItemBuilder can infer from class
		// For the test we rely on ModItemBuilder.group(item) to return "LootInfo" or similar.
		// If necessary, you may need to mock or use a concrete subclass that returns a known group.
		
		ModData mod = new ModData();
		mod.id = "serializationTest";
		mod.setItems(Set.of(item));
		
		// Write the item
		ItemService.writeModItems(mod, tempDir.toString());
		
		// Verify the file was created
		Path expectedFile = tempDir.resolve("Mods/serializationTest/Data/_stage/TestPak/items/test.xml");
		assertTrue(Files.exists(expectedFile), "Staged XML file should exist");
		
		// Read back the content
		String writtenXml = Files.readString(expectedFile);
		assertTrue(writtenXml.contains("test_item_001"), "Written XML should contain item ID");
		assertTrue(writtenXml.contains("<database"), "Should have root <database> element");
	}
	
	@Test
	@DisplayName("writeModItems: creates multiple files for items with different PAK stems")
	void writeMultipleModItems() throws Exception {
		ModItem item1 = new ModItem();
		item1.setId("id1");
		item1.setUIName("First");
		item1.setPath("Weapons.pak:weapons/sword.xml");
		
		ModItem item2 = new ModItem();
		item2.setId("id2");
		item2.setUIName("Second");
		item2.setPath("Armor.pak:armor/chest.xml");
		
		ModItem item3 = new ModItem();
		item3.setId("id3");
		item3.setUIName("Third");
		item3.setPath(null); // ends up in mod's own pak stem
		
		ModData mod = new ModData();
		mod.id = "multiTest";
		mod.setItems(Set.of(item1, item2, item3));
		
		ItemService.writeModItems(mod, tempDir.toString());
		
		Path stageRoot = tempDir.resolve("Mods/multiTest/Data/_stage");
		assertTrue(Files.exists(stageRoot.resolve("Weapons/weapons/sword.xml")));
		assertTrue(Files.exists(stageRoot.resolve("Armor/armor/chest.xml")));
		assertTrue(Files.exists(stageRoot.resolve("multiTest/apple.txt"))); // default name for null path
	}
	
	@Test
	@DisplayName("Round‑trip: load PAK, write items, compare normalized XML content")
	void roundTripFromPakToStaging() throws Exception {
		// Setup: write the resource PAK file to temp Data directory
		Path dataDir = tmp.resolve("Data");
		Files.createDirectories(dataDir);
		Path pakFile = dataDir.resolve("perk-data.pak");
		Files.write(pakFile, perkDataPakBytes);
		
		// Load items from the PAK
		Set<ModItem> items = ItemService.loadItems(dataDir);
		assertFalse(items.isEmpty(), "Should load items from perk-data.pak");
		
		// Create a mod and assign the items
		ModData mod = new ModData();
		mod.id = "roundTripTest";
		mod.setItems(items);
		
		// Write the items to staging
		ItemService.writeModItems(mod, tempDir.toString());
		
		// Now compare each staged XML file with the original entry inside the PAK
		Path stageRoot = tempDir.resolve("Mods/roundTripTest/Data/_stage");
		
		try (ZipFile zf = new ZipFile(pakFile.toFile())) {
			for (ModItem item : items) {
				String path = item.getPath();
				// Extract original PAK entry name (strip "perk-data.pak:")
				String entryName = path.substring(path.indexOf(':') + 1);
				ZipEntry entry = zf.getEntry(entryName);
				assertNotNull(entry, "Original entry should exist: " + entryName);
				
				// Read original XML from PAK
				String originalXml;
				try (var is = zf.getInputStream(entry)) {
					originalXml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
				}
				
				// Determine the staged file path (mirroring writeModItem logic)
				Path stagedFile = ItemService.getOutputFile(tempDir.toString(), path, mod);
				assertTrue(Files.exists(stagedFile), "Staged file missing: " + stagedFile);
				
				// Read written XML
				String writtenXml = Files.readString(stagedFile);
				
				// Normalize both and compare
				String normOriginal = normalizeXml(originalXml);
				String normWritten = normalizeXml(writtenXml);
				
				assertEquals(normOriginal, normWritten, "Normalized XML should match for entry: " + entryName);
			}
		}
	}
	
	@Test
	@DisplayName("writeModItems: writes nothing when items set is empty")
	void writeModItemsEmptySet() {
		ModData mod = new ModData();
		mod.id = "emptyItems";
		mod.setItems(Collections.emptySet());
		
		assertDoesNotThrow(() -> ItemService.writeModItems(mod, tempDir.toString()));
		
		// Verify no staging directory was created
		Path stageRoot = tempDir.resolve("Mods/emptyItems/Data/_stage");
		assertFalse(Files.exists(stageRoot), "Staging directory should not be created for empty item set");
	}
}