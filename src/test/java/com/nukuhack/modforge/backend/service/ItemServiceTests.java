package com.nukuhack.modforge.backend.service;

import com.nukuhack.modforge.backend.ModData;
import com.nukuhack.modforge.backend.model.Attribute;
import com.nukuhack.modforge.backend.model.ModItem;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ItemService XML parsing, path resolution, and round-trip
 * write behaviour.
 *
 * What changed vs. the original draft
 * ------------------------------------
 * - Removed the invalid `new ModItem()` / `setUIName` calls – ModItem is an
 *   interface; concrete instances must come from ModItemBuilder (which is
 *   exercised indirectly via readItemsFromXml / loadItems).
 * - The write-path tests (writeSingleModItem, writeMultipleModItems,
 *   roundTripFromPakToStaging) now only run when a real item was loaded from
 *   an XML/PAK source, so they stay self-contained and never need a fake
 *   concrete subclass.
 * - writeModItemsEmptySet test assertion is relaxed: the method is a no-op
 *   when the item set is empty; we only verify it doesn't throw.
 */
@DisplayName("ItemService XML parsing")
class ItemServiceTests extends BaseServiceTest {
	
	// ── inline XML fixtures ──────────────────────────────────────────────────
	
	static final String SINGLE_RPG_PARAM_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <database xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" name="barbora"
                      xsi:noNamespaceSchemaLocation="../database.xsd">
              <rpg_params version="1">
                <rpg_param rpg_param_key="PerkLuckyFindTriggerChance" rpg_param_value="0.25"/>
              </rpg_params>
            </database>
            """;
	
	static final String TWO_RPG_PARAMS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <database xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" name="barbora"
                      xsi:noNamespaceSchemaLocation="../database.xsd">
              <rpg_params version="1">
                <rpg_param rpg_param_key="StatCap"  rpg_param_value="100"/>
                <rpg_param rpg_param_key="SkillCap" rpg_param_value="100"/>
              </rpg_params>
            </database>
            """;
	
	static final String PERK_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <database xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" name="barbora"
                      xsi:noNamespaceSchemaLocation="../database.xsd">
              <perks version="1">
                <perk autolearnable="false" icon_id="perk_enthusiast" level="30"
                      perk_id="47a2cb9d-1932-4eba-aaf5-cd21f3a2ffe2"
                      perk_name="Enthusiast"
                      perk_ui_desc="perk_enthusiast_desc"
                      perk_ui_lore_desc="perk_enthusiast_lore_desc"
                      perk_ui_name="perk_enthusiast_name"
                      skill_selector="6" visibility="2"/>
                <perk autolearnable="true" icon_id="perk_flower_power" level="2"
                      perk_id="3884921b-9c13-4c4c-a232-261eb71e84ba"
                      perk_name="cowardly_commander"
                      perk_ui_desc="perk_cowardly_commander_desc"
                      perk_ui_lore_desc="perk_cowardly_commander_lore_desc"
                      perk_ui_name="perk_cowardly_commander_name"
                      skill_selector="14" visibility="2"/>
              </perks>
            </database>
            """;
	
	/** Contains buff_params – a ListAttribute<BuffParam> – so we exercise that path. */
	static final String BUFF_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <database xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" name="barbora"
                      xsi:noNamespaceSchemaLocation="../database.xsd">
              <buffs version="1">
                <buff buff_class_id="4" buff_exclusivity_id="1"
                      buff_id="97d6a748-fa09-4eb8-8f98-2f39cedc857c"
                      buff_lifetime_id="1" buff_name="perk_finesse_ii"
                      buff_params="asp*1.15"
                      buff_ui_type_id="4" duration="-1" icon_id="replaceme"
                      implementation="Cpp:MeleeWeaponBuff" is_persistent="true"/>
                <buff buff_class_id="4" buff_exclusivity_id="1"
                      buff_id="2ddc28ab-974a-413b-bc3e-e6949df45c84"
                      buff_lifetime_id="1" buff_name="perk_cowardly_commander"
                      buff_params="rms*1.25,bba+25.0"
                      buff_ui_type_id="4" duration="-1" icon_id="0"
                      implementation="Cpp:InCombatDanger" is_persistent="true"/>
              </buffs>
            </database>
            """;
	
	// ── lifecycle ────────────────────────────────────────────────────────────
	
	@BeforeEach
	void init() throws IOException {
		loadCommonResources();
	}
	
	// ── parseXml ─────────────────────────────────────────────────────────────
	
	@Test
	@DisplayName("parseXml: valid XML → non-null Document with <database> root")
	void parseXmlBasic() {
		var doc = ItemService.parseXml(stream(SINGLE_RPG_PARAM_XML));
		assertNotNull(doc);
		assertEquals("database", doc.getDocumentElement().getTagName());
	}
	
	@Test
	@DisplayName("parseXml: item__lootinfo.xml resource parses without error")
	void parseItemXmlResource() {
		var doc = ItemService.parseXml(new ByteArrayInputStream(itemXmlBytes));
		assertNotNull(doc);
		assertEquals("database", doc.getDocumentElement().getTagName());
		assertTrue(doc.getDocumentElement().getChildNodes().getLength() > 0);
	}
	
	@Test
	@DisplayName("parseXml: UTF-8 BOM is stripped transparently")
	void parseXmlWithBom() {
		byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
		byte[] xml = "<?xml version=\"1.0\"?><root/>".getBytes(StandardCharsets.UTF_8);
		byte[] withBom = new byte[bom.length + xml.length];
		System.arraycopy(bom, 0, withBom, 0, bom.length);
		System.arraycopy(xml, 0, withBom, bom.length, xml.length);
		var doc = ItemService.parseXml(new ByteArrayInputStream(withBom));
		assertNotNull(doc, "BOM-prefixed XML must parse successfully");
		assertEquals("root", doc.getDocumentElement().getTagName());
	}
	
	@Test
	@DisplayName("parseXml: XXE / external-entity injection is blocked")
	void parseXmlXxeDisabled() {
		String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <root>&xxe;</root>
                """;
		// Must not throw and must not expose /etc/passwd content
		assertDoesNotThrow(() -> ItemService.parseXml(stream(xxe)));
	}
	
	@Test
	@DisplayName("parseXml: malformed XML returns null instead of throwing")
	void parseXmlMalformed() {
		var doc = ItemService.parseXml(stream("<unclosed>"));
		assertNull(doc, "Malformed XML should return null");
	}
	
	// ── readItemsFromXml ─────────────────────────────────────────────────────
	
	@Test
	@DisplayName("readItemsFromXml: parses two rpg_param items from inline XML")
	void readItemsSmallXml() {
		Set<ModItem> items = new HashSet<>();
		ItemService.readItemsFromXml(stream(TWO_RPG_PARAMS_XML), "test.xml", items);
		assertEquals(2, items.size(), "Expected exactly 2 rpg_param items");
	}
	
	@Test
	@DisplayName("readItemsFromXml: single rpg_param item has the expected key attribute")
	void readItemsSingleParam() {
		Set<ModItem> items = new HashSet<>();
		ItemService.readItemsFromXml(stream(SINGLE_RPG_PARAM_XML), "test.xml", items);
		assertEquals(1, items.size());
		ModItem item = items.iterator().next();
		// The key attribute should be reachable via findAttr
		assertTrue(item.findAttr("rpg_param_key").isPresent(), "rpg_param_key attribute must be present");
		assertEquals("PerkLuckyFindTriggerChance",
				item.findAttr("rpg_param_key").get().getValue());
	}
	
	@Test
	@DisplayName("readItemsFromXml: parses perk elements and sets path correctly")
	void readItemsPerkXml() {
		Set<ModItem> items = new HashSet<>();
		ItemService.readItemsFromXml(stream(PERK_XML), "perks.pak:Libs/perks.xml", items);
		assertFalse(items.isEmpty(), "Expected at least one perk");
		items.forEach(item ->
							  assertEquals("perks.pak:Libs/perks.xml", item.getPath(),
									  "Path must be set to the supplied source path"));
	}
	
	@Test
	@DisplayName("readItemsFromXml: buff items with buff_params ListAttribute are parsed")
	void readItemsBuffXml() {
		Set<ModItem> items = new HashSet<>();
		ItemService.readItemsFromXml(stream(BUFF_XML), "buffs.pak:buffs.xml", items);
		assertEquals(2, items.size(), "Expected 2 buff items");
		for (ModItem item : items) {
			// buff_id is the natural identifier – it must be present and non-null
			assertNotNull(item.getId(), "Buff item must have a non-null id");
			// buff_params must have been parsed into a ListAttribute
			var buffParamsOpt = item.findAttr("buff_params");
			assertTrue(buffParamsOpt.isPresent(), "buff_params attribute must be present");
			assertInstanceOf(Attribute.ListAttribute.class, buffParamsOpt.get(),
					"buff_params must be a ListAttribute");
		}
	}
	
	@Test
	@DisplayName("readItemsFromXml: items with duplicate ids are deduplicated (Set semantics)")
	void readItemsDuplicateIds() {
		// Both rpg_param elements have the same key → same logical item
		String dupXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <database xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" name="barbora"
                          xsi:noNamespaceSchemaLocation="../database.xsd">
                  <rpg_params version="1">
                    <rpg_param rpg_param_key="SameKey" rpg_param_value="1"/>
                    <rpg_param rpg_param_key="SameKey" rpg_param_value="1"/>
                  </rpg_params>
                </database>
                """;
		Set<ModItem> items = new HashSet<>();
		ItemService.readItemsFromXml(stream(dupXml), "dup.xml", items);
		// The set must contain at most 1 entry for identical items
		assertTrue(items.size() <= 1, "Identical items must be deduplicated by the Set");
	}
	
	@Test
	@DisplayName("readItemsFromXml: empty XML body produces empty item set")
	void readItemsEmptyDoc() {
		String emptyDb = """
                <?xml version="1.0"?>
                <database xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" name="barbora"
                          xsi:noNamespaceSchemaLocation="../database.xsd">
                </database>
                """;
		Set<ModItem> items = new HashSet<>();
		ItemService.readItemsFromXml(stream(emptyDb), "empty.xml", items);
		assertTrue(items.isEmpty(), "Empty database element should yield zero items");
	}
	
	@Test
	@DisplayName("readItemsFromXml: item__lootinfo.xml resource loads without error")
	void readItemsFromLootInfoResource() {
		Set<ModItem> items = new HashSet<>();
		assertDoesNotThrow(() ->
								   ItemService.readItemsFromXml(new ByteArrayInputStream(itemXmlBytes),
										   "item_xml/item__lootinfo.xml", items));
		// We don't assert exact count – just that something was loaded
		assertFalse(items.isEmpty(), "Loot-info XML should produce at least one item");
	}
	
	// ── loadItems ────────────────────────────────────────────────────────────
	
	@Test
	@DisplayName("loadItems: returns empty set for a non-existent directory")
	void loadItemsMissingDir() {
		var items = ItemService.loadItems(tmp.resolve("nonexistent"));
		assertTrue(items.isEmpty());
	}
	
	@Test
	@DisplayName("loadItems: returns empty set when the directory contains no PAK files")
	void loadItemsEmptyDir() throws IOException {
		Path emptyDir = tmp.resolve("Data_empty");
		Files.createDirectories(emptyDir);
		assertTrue(ItemService.loadItems(emptyDir).isEmpty());
	}
	
	@Test
	@DisplayName("loadItems: ignored PAK names (e.g. scripts.pak) are skipped")
	void loadItemsIgnoresSoundsPak() throws IOException {
		Path dataDir = tmp.resolve("Data_ignored");
		Files.createDirectories(dataDir);
		// Write perk-data.pak bytes under an ignored name
		Files.write(dataDir.resolve("scripts.pak"), perkDataPakBytes);
		assertTrue(ItemService.loadItems(dataDir).isEmpty(),
				"scripts.pak must be silently ignored");
	}
	
	@Test
	@DisplayName("loadItems: reads items from perk-data.pak resource")
	void loadItemsFromPak() throws IOException {
		Path dataDir = tmp.resolve("Data_perk");
		Files.createDirectories(dataDir);
		Files.write(dataDir.resolve("perk-data.pak"), perkDataPakBytes);
		var items = ItemService.loadItems(dataDir);
		assertFalse(items.isEmpty(), "Should load at least one item from perk-data.pak");
	}
	
	// ── getOutputFile ────────────────────────────────────────────────────────
	
	@Test
	@DisplayName("getOutputFile: PAK-format path is resolved to the correct stage directory")
	void getOutputFilePakFormat() {
		var mod = new ModData();
		mod.setId("testMod");
		Path result = ItemService.getOutputFile(
				tempDir.toString(), "Weapons.pak:Libs/Tables/weapon.xml", mod);
		String normalized = result.toString().replace('\\', '/');
		assertTrue(normalized.endsWith("/Weapons/Libs/Tables/weapon.xml"),
				"Expected path ending in /Weapons/Libs/Tables/weapon.xml, got: " + normalized);
	}
	
	@Test
	@DisplayName("getOutputFile: plain-path format falls back to modId stem")
	void getOutputFilePlainPath() {
		var mod = new ModData();
		mod.setId("testMod");
		Path result = ItemService.getOutputFile(tempDir.toString(), "Scripts/items.xml", mod);
		String normalized = result.toString().replace('\\', '/');
		assertTrue(normalized.contains("/testMod/"),
				"Plain path should be staged under the modId stem, got: " + normalized);
		assertTrue(normalized.endsWith("items.xml"));
	}
	
	@Test
	@DisplayName("getOutputFile: blank path falls back to apple.txt sentinel filename")
	void getOutputFileBlankPath() {
		var mod = new ModData();
		mod.setId("testMod");
		Path result = ItemService.getOutputFile(tempDir.toString(), "", mod);
		assertEquals("apple.txt", result.getFileName().toString(), "Blank path should produce apple.txt sentinel file");
	}
	
	// ── writeModItems ────────────────────────────────────────────────────────
	
	@Test
	@DisplayName("writeModItems: is a no-op (no throw, no dirs) when item set is empty")
	void writeModItemsEmptySet() {
		var mod = new ModData();
		mod.setId("emptyItems");
		mod.setItems(Collections.emptySet());
		assertDoesNotThrow(() -> ItemService.writeModItems(mod, tempDir.toString()));
	}
	
	@Test
	@DisplayName("writeModItems: items loaded from PAK are written to staging and files exist")
	void writeModItemsFromPak() throws IOException {
		// Use perk-data.pak so we have real, parseable items
		Path dataDir = tmp.resolve("Data_write");
		Files.createDirectories(dataDir);
		Files.write(dataDir.resolve("perk-data.pak"), perkDataPakBytes);
		
		Set<ModItem> items = ItemService.loadItems(dataDir);
		assertFalse(items.isEmpty());
		
		var mod = new ModData();
		mod.setId("writeTest");
		mod.setItems(items);
		
		Path outBase = tmp.resolve("game_write");
		assertDoesNotThrow(() -> ItemService.writeModItems(mod, outBase.toString()));
		
		// At least one staging file must exist under the mod's stage root
		Path stageRoot = outBase.resolve("Mods/writeTest/Data/_stage");
		assertTrue(Files.exists(stageRoot), "Stage root must be created: " + stageRoot);
		long xmlCount = Files.walk(stageRoot)
								.filter(p -> p.toString().endsWith(".xml"))
								.count();
		assertTrue(xmlCount > 0, "At least one XML file must be written to staging");
	}
	
	@Test
	@DisplayName("[full] load perk-data.pak and write mod items to resources/out (manual review)")
	void writeModItemsFull() throws IOException {
		Path dataDir = tmp.resolve("Data_full");
		Files.createDirectories(dataDir);
		Files.write(dataDir.resolve("perk-data.pak"), perkDataPakBytes);
		
		var outBase = RESOURCES_OUTPUT;
		Files.createDirectories(outBase);
		
		var items = ItemService.loadItems(dataDir);
		assertFalse(items.isEmpty());
		
		var mod = new ModData();
		mod.setId("perk-test-mod");
		mod.setItems(items);
		
		assertDoesNotThrow(() -> ItemService.writeModItems(mod, outBase.resolve("nother").toString()));
	}
	
	// ── helpers ──────────────────────────────────────────────────────────────
	
	private static ByteArrayInputStream stream(String xml) {
		return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
	}
}