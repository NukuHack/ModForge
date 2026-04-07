package modforge.backend.service;

import modforge.Util;
import modforge.backend.DataPoint;
import modforge.backend.ModData;
import modforge.backend.model.ModItem;
import modforge.backend.model.storm.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Extensive test suite for {@link StormService} and its inner class {@link StormService.StormParser}.
 */
@ExtendWith(MockitoExtension.class)
class StormServiceTest {
	
	@TempDir
	Path tempDir;
	
	@Mock
	private UserConfig userConfig;
	
	@Mock
	private ModData modData;
	
	private StormService stormService;
	
	@BeforeEach
	void setUp() {
		when(userConfig.getGameDirectory()).thenReturn(tempDir.toString());
		stormService = new StormService(userConfig);
	}
	
	// -------------------------------------------------------------------------
	// Helper methods for creating test PAK files and XML content
	// -------------------------------------------------------------------------
	
	/**
	 * Creates a .pak file (ZIP) at the given path containing the specified entries.
	 *
	 * @param pakPath   destination path for the PAK file
	 * @param entries   map from entry name (e.g. "Libs/Storm/Combat/melee.xml") to content
	 */
	private void createPak(Path pakPath, Map<String, String> entries) throws IOException {
		try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(pakPath))) {
			for (var entry : entries.entrySet()) {
				zos.putNextEntry(new ZipEntry(entry.getKey()));
				zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
				zos.closeEntry();
			}
		}
	}
	
	/**
	 * Creates a minimal valid Storm XML document.
	 */
	private String minimalStormXml() {
		return "<?xml version=\"1.0\"?>\n<storm/>";
	}
	
	/**
	 * A real Storm I use in game - truncated
	 */
	private String realStormXml() {
		return """
				<?xml version="1.0"?>
				<!DOCTYPE storm SYSTEM "storm.dtd">
				<storm>
					<rules>
						<rule name="underwear_man">
							<selectors>
								<isMan/>
							</selectors>
							<operations>
							</operations>
						</rule>
						<rule name="underwear_woman">
							<selectors>
								<isWoman/>
							</selectors>
							<operations>
							</operations>
						</rule>
						<rule name="underwear_papezskyLegat_rozaNaked">
							<selectors>
								<hasName Name="papezskyLegat_cin_rozaNaked"/>
							</selectors>
							<operations>
							</operations>
						</rule>
						<rule name="underwear_tarasMura_taras">
							<selectors>
								<hasName name="ksta_taras"/>
							</selectors>
							<operations>
								<setUnderwear name="tarasMura_underwear"/>
							</operations>
						</rule>
						<rule name="underwear_utopenci">
							<selectors>
								<or>
									<hasName name="tvez_utopenec_1"/>
									<hasName name="tvez_utopenec_2"/>
									<hasName name="tvez_utopenec_3"/>
								</or>
							</selectors>
							<operations>
								<setUnderwear name="prepadeni*"/>
							</operations>
						</rule>
						<rule name="underwear_kmis_man_14">
							<selectors>
								<hasName name="kmis_man_14"/>
							</selectors>
							<operations>
								<setUnderwear name="m_underwear01_m05"/>
							</operations>
						</rule>
						<rule name="underwear_kkut_man_124">
							<selectors>
								<hasName name="kkut_man_124"/>
							</selectors>
							<operations>
								<setUnderwear name="m_underwear01_m05"/>
							</operations>
						</rule>
						<rule name="underwear_kkut_man_125">
							<selectors>
								<hasName name="kkut_man_125"/>
							</selectors>
							<operations>
								<setUnderwear name="m_underwear02_m03"/>
							</operations>
						</rule>
					</rules>
				</storm>
				""";
	}
	
	/**
	 * Creates a Storm XML document with various sections for testing.
	 */
	private String fullStormXml() {
		return """
				<?xml version="1.0"?>
				<storm category="TestCat">
				    <common>
				        <source path="common/base.xml"/>
				    </common>
				    <tasks>
				        <task name="CombatTask" class="CombatClass" comment="Main combat">
				            <source path="combat/main.xml"/>
				        </task>
				        <task name="SimpleTask" sources="simple.xml"/>
				    </tasks>
				    <customSelectors>
				        <selector name="mySelector" comment="test">
				            <attribute name="attr1"/>
				        </selector>
				    </customSelectors>
				    <customOperations>
				        <operation name="myOp" mode="add">
				            <attribute stat="Strength" minMod="0" maxMod="1"/>
				        </operation>
				    </customOperations>
				    <rules>
				        <rule name="testRule" comment="test comment" mode="override">
				            <selectors>
				                <and>
				                    <hasName name="foo"/>
				                    <or>
				                        <isMan/>
				                        <not><isWoman/></not>
				                    </or>
				                </and>
				            </selectors>
				            <operations>
				                <setUnderwear name="bar"/>
				                <nestedOp>
				                    <childOp value="x"/>
				                </nestedOp>
				            </operations>
				        </rule>
				    </rules>
				</storm>
				""";
	}
	
	/**
	 * Creates a Storm XML document with mixed-case tags to test case insensitivity.
	 */
	private String mixedCaseStormXml() {
		return """
				<?xml version="1.0"?>
				<STORM>
				    <Common>
				        <Source path="common/base.xml"/>
				    </Common>
				    <Tasks>
				        <Task name="MixedTask" Class="MixedClass">
				            <Source path="mixed.xml"/>
				        </Task>
				    </Tasks>
				    <CustomSelectors>
				        <Selector Name="mixedSelector">
				            <Attribute name="attr1"/>
				        </Selector>
				    </CustomSelectors>
				    <CustomOperations>
				        <Operation Name="mixedOp" Mode="multiply">
				            <Attribute Stat="Health" MinMod="-1" MaxMod="2"/>
				        </Operation>
				    </CustomOperations>
				    <Rules>
				        <Rule Name="mixedRule">
				            <Selectors>
				                <HasName Name="target"/>
				            </Selectors>
				            <Operations>
				                <SetUnderwear Name="mixedUnderwear"/>
				            </Operations>
				        </Rule>
				    </Rules>
				</STORM>
				""";
	}
	
	// -------------------------------------------------------------------------
	// Nested test class for StormParser (parsing and serialization)
	// -------------------------------------------------------------------------
	
	@Nested
	class StormParserTest {
		
		private DataPoint dummyDataPoint;
		
		@BeforeEach
		void setUp() {
			dummyDataPoint = new DataPoint("test", "test.xml", Storm.class);
		}
		
		@Test
		void parseMinimalXml() throws Exception {
			try (InputStream is = new ByteArrayInputStream(minimalStormXml().getBytes(StandardCharsets.UTF_8))) {
				StormData data = StormService.StormParser.parse(is, dummyDataPoint, "test/id");
				assertNotNull(data);
				assertEquals("test/id", data.getId());
				assertNull(data.getCategory());
				assertTrue(data.getCommonSources().isEmpty());
				assertTrue(data.getTasks().isEmpty());
				assertTrue(data.getCustomSelectors().isEmpty());
				assertTrue(data.getCustomOperations().isEmpty());
				assertTrue(data.getRules().isEmpty());
			}
		}
		
		@Test
		void parseFullXml() throws Exception {
			try (InputStream is = new ByteArrayInputStream(fullStormXml().getBytes(StandardCharsets.UTF_8))) {
				StormData data = StormService.StormParser.parse(is, dummyDataPoint, "full/id");
				assertNotNull(data);
				assertEquals("full/id", data.getId());
				assertEquals("TestCat", data.getCategory());
				
				// Common sources
				assertEquals(1, data.getCommonSources().size());
				assertEquals("common/base.xml", data.getCommonSources().get(0));
				
				// Tasks
				assertEquals(2, data.getTasks().size());
				StormTask task1 = data.getTasks().get(0);
				assertEquals("CombatTask", task1.getName());
				assertEquals("CombatClass", task1.getTaskClass());
				assertEquals("Main combat", task1.getComment());
				assertEquals("combat/main.xml", task1.getSources());
				StormTask task2 = data.getTasks().get(1);
				assertEquals("SimpleTask", task2.getName());
				assertEquals("simple.xml", task2.getSources());
				
				// Custom selectors
				assertEquals(1, data.getCustomSelectors().size());
				CustomStormSelector selector = data.getCustomSelectors().get(0);
				assertEquals("mySelector", selector.getName());
				assertEquals("test", selector.getComment());
				assertEquals(1, selector.getAttributeNames().size());
				assertTrue(selector.getAttributeNames().contains("attr1"));
				
				// Custom operations
				assertEquals(1, data.getCustomOperations().size());
				CustomStormOperation op = data.getCustomOperations().get(0);
				assertEquals("myOp", op.getName());
				assertEquals("add", op.getMode());
				assertEquals(1, op.getModAttributes().size());
				CustomStormOperation.ModAttribute attr = op.getModAttributes().get(0);
				assertEquals("Strength", attr.getStat());
				assertEquals(0.0, attr.getMinMod());
				assertEquals(1.0, attr.getMaxMod());
				
				// Rules
				assertEquals(1, data.getRules().size());
				StormRule rule = data.getRules().get(0);
				assertEquals("testRule", rule.getName());
				assertEquals("test comment", rule.getComment());
				assertEquals("override", rule.getMode());
				
				// Selectors tree
				assertEquals(1, rule.getSelectors().size());
				GenericSelector andSelector = rule.getSelectors().get(0);
				assertEquals("and", andSelector.getName());
				assertEquals(2, andSelector.getChildren().size());
				// First child: hasName
				GenericSelector hasName = andSelector.getChildren().get(0);
				assertEquals("hasName", hasName.getName());
				assertEquals("foo", hasName.getAttributes().get("name"));
				// Second child: or
				GenericSelector orSelector = andSelector.getChildren().get(1);
				assertEquals("or", orSelector.getName());
				assertEquals(2, orSelector.getChildren().size());
				GenericSelector isMan = orSelector.getChildren().get(0);
				assertEquals("isMan", isMan.getName());
				GenericSelector notSelector = orSelector.getChildren().get(1);
				assertEquals("not", notSelector.getName());
				assertEquals(1, notSelector.getChildren().size());
				GenericSelector isWoman = notSelector.getChildren().get(0);
				assertEquals("isWoman", isWoman.getName());
				
				// Operations
				assertEquals(2, rule.getOperations().size());
				GenericOperation setUnderwear = rule.getOperations().get(0);
				assertEquals("setUnderwear", setUnderwear.getName());
				assertEquals("bar", setUnderwear.getAttributes().get("name"));
				GenericOperation nestedOp = rule.getOperations().get(1);
				assertEquals("nestedOp", nestedOp.getName());
				assertEquals(1, nestedOp.getChildren().size());
				GenericOperation childOp = nestedOp.getChildren().get(0);
				assertEquals("childOp", childOp.getName());
				assertEquals("x", childOp.getAttributes().get("value"));
			}
		}
		
		@Test
		void parseMixedCaseXml() throws Exception {
			try (InputStream is = new ByteArrayInputStream(mixedCaseStormXml().getBytes(StandardCharsets.UTF_8))) {
				StormData data = StormService.StormParser.parse(is, dummyDataPoint, "mixed/id");
				assertNotNull(data);
				assertEquals("mixed/id", data.getId());
				
				// Common sources (should be lowercased after parse)
				assertEquals(1, data.getCommonSources().size());
				assertEquals("common/base.xml", data.getCommonSources().get(0));
				
				// Tasks
				assertEquals(1, data.getTasks().size());
				StormTask task = data.getTasks().get(0);
				assertEquals("MixedTask", task.getName());
				assertEquals("MixedClass", task.getTaskClass());
				assertEquals("mixed.xml", task.getSources());
				
				// Custom selectors: attribute names normalized to lower case
				assertEquals(1, data.getCustomSelectors().size());
				CustomStormSelector selector = data.getCustomSelectors().get(0);
				assertEquals("mixedSelector", selector.getName());
				assertEquals(1, selector.getAttributeNames().size());
				assertTrue(selector.getAttributeNames().contains("attr1"));
				
				// Custom operations
				assertEquals(1, data.getCustomOperations().size());
				CustomStormOperation op = data.getCustomOperations().get(0);
				assertEquals("mixedOp", op.getName());
				assertEquals("multiply", op.getMode());
				assertEquals(1, op.getModAttributes().size());
				CustomStormOperation.ModAttribute attr = op.getModAttributes().get(0);
				assertEquals("Health", attr.getStat());
				assertEquals(- 1.0, attr.getMinMod());
				assertEquals(2.0, attr.getMaxMod());
				
				// Rules: selector attribute name normalized to lower case
				assertEquals(1, data.getRules().size());
				StormRule rule = data.getRules().get(0);
				assertEquals("mixedRule", rule.getName());
				assertEquals(1, rule.getSelectors().size());
				GenericSelector hasName = rule.getSelectors().get(0);
				assertEquals("hasName", hasName.getName());
				assertEquals("target", hasName.getAttributes().get("name"));
				assertEquals(1, rule.getOperations().size());
				GenericOperation setUnderwear = rule.getOperations().get(0);
				assertEquals("setUnderwear", setUnderwear.getName());
				assertEquals("mixedUnderwear", setUnderwear.getAttributes().get("name"));
			}
		}
		
		@Test
		void parseMalformedXmlReturnsEmptyData() throws Exception {
			String malformed = "<?xml version=\"1.0\"?><storm><unclosed>";
			try (InputStream is = new ByteArrayInputStream(malformed.getBytes(StandardCharsets.UTF_8))) {
				StormData data = StormService.StormParser.parse(is, dummyDataPoint, "bad/id");
				assertNotNull(data);
				assertEquals("bad/id", data.getId());
				assertTrue(data.getCommonSources().isEmpty());
				assertTrue(data.getTasks().isEmpty());
				assertTrue(data.getCustomSelectors().isEmpty());
				assertTrue(data.getCustomOperations().isEmpty());
				assertTrue(data.getRules().isEmpty());
				// No exception thrown, just logs error and returns empty structure
			}
		}
		
		@Test
		void parseTaskWithFlatSourcesAttribute() throws Exception {
			String xml = """
					<?xml version="1.0"?>
					<storm>
					    <tasks>
					        <task name="flatTask" sources="file1.xml,file2.xml" class="Test"/>
					    </tasks>
					</storm>
					""";
			try (InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
				StormData data = StormService.StormParser.parse(is, dummyDataPoint, "flat/id");
				assertEquals(1, data.getTasks().size());
				StormTask task = data.getTasks().get(0);
				assertEquals("file1.xml,file2.xml", task.getSources());
			}
		}
		
		@Test
		void parseTaskWithChildSourceElements() throws Exception {
			String xml = """
					<?xml version="1.0"?>
					<storm>
					    <tasks>
					        <task name="childTask" class="Test">
					            <source path="first.xml"/>
					            <source path="second.xml"/>
					        </task>
					    </tasks>
					</storm>
					""";
			try (InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
				StormData data = StormService.StormParser.parse(is, dummyDataPoint, "child/id");
				assertEquals(1, data.getTasks().size());
				StormTask task = data.getTasks().get(0);
				// Should be joined with commas
				assertEquals("first.xml,second.xml", task.getSources());
			}
		}
		
		@Test
		void serializeAndRoundTrip() throws Exception {
			// Build a StormData object programmatically
			StormData original = new StormData();
			original.setId("roundtrip/id");
			original.setCategory("RoundTripCat");
			
			original.getCommonSources().add("common/test.xml");
			
			StormTask task = new StormTask();
			task.setName("testTask");
			task.setTaskClass("TestClass");
			task.setComment("test comment");
			task.setSources("src1.xml,src2.xml");
			original.getTasks().add(task);
			
			CustomStormSelector cs = new CustomStormSelector();
			cs.setName("customSel");
			cs.setComment("selComment");
			cs.getAttributeNames().add("attrA");
			original.getCustomSelectors().add(cs);
			
			CustomStormOperation co = new CustomStormOperation();
			co.setName("customOp");
			co.setMode("set");
			CustomStormOperation.ModAttribute ma = new CustomStormOperation.ModAttribute();
			ma.setStat("StatX");
			ma.setMinMod(1.5);
			ma.setMaxMod(2.5);
			co.getModAttributes().add(ma);
			original.getCustomOperations().add(co);
			
			StormRule rule = new StormRule();
			rule.setName("testRule");
			rule.setComment("ruleComment");
			rule.setMode("add");
			
			GenericSelector andSel = new GenericSelector("and");
			GenericSelector hasName = new GenericSelector("hasName");
			hasName.getAttributes().put("name", "target");
			andSel.getChildren().add(hasName);
			rule.getSelectors().add(andSel);
			
			GenericOperation setOp = new GenericOperation("setUnderwear");
			setOp.getAttributes().put("name", "underwearItem");
			rule.getOperations().add(setOp);
			
			original.getRules().add(rule);
			
			// Serialize
			String serialized = StormService.StormParser.serialize(original);
			assertNotNull(serialized);
			assertTrue(serialized.contains("<storm"));
			assertTrue(serialized.contains("category=\"RoundTripCat\""));
			assertTrue(serialized.contains("<common>"));
			assertTrue(serialized.contains("<source path=\"common/test.xml\"/>"));
			assertTrue(serialized.contains("<task name=\"testTask\" class=\"TestClass\" comment=\"test comment\">"));
			assertTrue(serialized.contains("<source path=\"src1.xml\"/>"));
			assertTrue(serialized.contains("<source path=\"src2.xml\"/>"));
			assertTrue(serialized.contains("<customSelectors>"));
			assertTrue(serialized.contains("<selector name=\"customSel\" comment=\"selComment\">"));
			assertTrue(serialized.contains("<attribute name=\"attrA\"/>"));
			assertTrue(serialized.contains("<customOperations>"));
			assertTrue(serialized.contains("<operation name=\"customOp\" mode=\"set\">"));
			assertTrue(serialized.contains("<attribute stat=\"StatX\" minMod=\"1.5\" maxMod=\"2.5\"/>"));
			assertTrue(serialized.contains("<rule name=\"testRule\" mode=\"add\" comment=\"ruleComment\">"));
			assertTrue(serialized.contains("<selectors><and><hasName name=\"target\"/></and></selectors>"));
			assertTrue(serialized.contains("<operations><setUnderwear name=\"underwearItem\"/></operations>"));
			
			// Parse back
			try (InputStream is = new ByteArrayInputStream(serialized.getBytes(StandardCharsets.UTF_8))) {
				StormData parsed = StormService.StormParser.parse(is, dummyDataPoint, "roundtrip/id");
				assertEquals(original.getId(), parsed.getId());
				assertEquals(original.getCategory(), parsed.getCategory());
				assertEquals(original.getCommonSources(), parsed.getCommonSources());
				assertEquals(original.getTasks().size(), parsed.getTasks().size());
				StormTask parsedTask = parsed.getTasks().get(0);
				assertEquals(task.getName(), parsedTask.getName());
				assertEquals(task.getTaskClass(), parsedTask.getTaskClass());
				assertEquals(task.getComment(), parsedTask.getComment());
				assertEquals(task.getSources(), parsedTask.getSources());
				assertEquals(original.getCustomSelectors().size(), parsed.getCustomSelectors().size());
				assertEquals(original.getCustomSelectors().get(0).getName(), parsed.getCustomSelectors().get(0).getName());
				assertEquals(original.getCustomOperations().size(), parsed.getCustomOperations().size());
				assertEquals(original.getCustomOperations().get(0).getName(), parsed.getCustomOperations().get(0).getName());
				assertEquals(original.getRules().size(), parsed.getRules().size());
				StormRule parsedRule = parsed.getRules().get(0);
				assertEquals(rule.getName(), parsedRule.getName());
				assertEquals(rule.getComment(), parsedRule.getComment());
				assertEquals(rule.getMode(), parsedRule.getMode());
				assertEquals(rule.getSelectors().size(), parsedRule.getSelectors().size());
				assertEquals(rule.getOperations().size(), parsedRule.getOperations().size());
			}
		}
	}
	
	// -------------------------------------------------------------------------
	// Nested test class for StormService public methods (indexing, writing, etc.)
	// -------------------------------------------------------------------------
	
	@Nested
	class StormServiceIndexingTest {
		
		@Test
		void initScansGameDirectoryPaks() throws Exception {
			// Create a fake Data folder with a .pak file containing Storm XML
			Path dataDir = tempDir.resolve("Data");
			Files.createDirectories(dataDir);
			Path pakFile = dataDir.resolve("scripts.pak");
			
			Map<String, String> pakEntries = new HashMap<>();
			pakEntries.put("Libs/Storm/Combat/melee.xml", minimalStormXml());
			pakEntries.put("Libs/Storm/Ranged/archery.xml", fullStormXml());
			pakEntries.put("some/other/file.txt", "ignore me");
			createPak(pakFile, pakEntries);
			
			// Mock Util.gameDataDir to return our dataDir
			try (MockedStatic<Util> utilMock = mockStatic(Util.class)) {
				utilMock.when(() -> Util.gameDataDir(anyString())).thenReturn(dataDir);
				
				stormService.init();
				
				List<StormData> allData = stormService.getAllStormData();
				assertEquals(2, allData.size());
				
				StormData melee = stormService.getById("Libs/Storm/Combat/melee");
				assertNotNull(melee);
				assertEquals("Combat", melee.getCategory());
				
				StormData archery = stormService.getById("Libs/Storm/Ranged/archery");
				assertNotNull(archery);
				assertEquals("Ranged", archery.getCategory());
				
				Set<String> categories = stormService.getCategories();
				assertEquals(Set.of("Combat", "Ranged"), categories);
				
				List<StormData> combatData = stormService.getByCategory("Combat");
				assertEquals(1, combatData.size());
				assertEquals("Libs/Storm/Combat/melee", combatData.get(0).getId());
			}
		}
		
		@Test
		void initHandlesMissingDataFolder() {
			try (MockedStatic<Util> utilMock = mockStatic(Util.class)) {
				// Data folder does not exist
				Path nonExistent = tempDir.resolve("NonExistent");
				utilMock.when(() -> Util.gameDataDir(anyString())).thenReturn(nonExistent);
				stormService.init();
				assertTrue(stormService.getAllStormData().isEmpty());
			}
		}
		
		@Test
		void initHandlesInvalidPakFilesGracefully() throws Exception {
			Path dataDir = tempDir.resolve("Data");
			Files.createDirectories(dataDir);
			Path pakFile = dataDir.resolve("corrupt.pak");
			// Create a non-ZIP file with .pak extension
			Files.writeString(pakFile, "This is not a zip file");
			
			try (MockedStatic<Util> utilMock = mockStatic(Util.class)) {
				utilMock.when(() -> Util.gameDataDir(anyString())).thenReturn(dataDir);
				stormService.init();
				// Should not throw, and index remains empty (no valid entries)
				assertTrue(stormService.getAllStormData().isEmpty());
			}
		}
		
		@Test
		void loadForModAttachesStormDataToModItems() throws Exception {
			// Create a mod data folder with a PAK containing Storm XML
			Path modDataDir = tempDir.resolve("Mods/myMod/Data");
			Files.createDirectories(modDataDir);
			Path pakFile = modDataDir.resolve("mod_storm.pak");
			
			Map<String, String> pakEntries = new HashMap<>();
			String stormId = "Libs/Storm/MyMod/custom";
			pakEntries.put(stormId + ".xml", minimalStormXml());
			createPak(pakFile, pakEntries);
			
			// Prepare modData mock with a Storm item that has matching ID
			Storm stormItem = mock(Storm.class);
			when(stormItem.getId()).thenReturn(stormId);
			List<ModItem> items = new ArrayList<>();
			items.add(stormItem);
			when(modData.getItems()).thenReturn(items);
			when(modData.id).thenReturn("myMod");
			
			try (MockedStatic<Util> utilMock = mockStatic(Util.class)) {
				utilMock.when(() -> Util.modData(anyString(), eq("myMod"))).thenReturn(modDataDir.toString());
				
				stormService.loadForMod(modData);
				
				verify(stormItem).setStormData(any(StormData.class));
				// Also ensure the StormData is correctly populated
				// We can capture and check
				var captor = org.mockito.ArgumentCaptor.forClass(StormData.class);
				verify(stormItem).setStormData(captor.capture());
				StormData attached = captor.getValue();
				assertNotNull(attached);
				assertEquals(stormId, attached.getId());
			}
		}
		
		@Test
		void loadForModIgnoresNonStormItems() throws Exception {
			Path modDataDir = tempDir.resolve("Mods/myMod/Data");
			Files.createDirectories(modDataDir);
			Path pakFile = modDataDir.resolve("mod_storm.pak");
			createPak(pakFile, Map.of("Libs/Storm/test.xml", minimalStormXml()));
			
			// ModData contains a non-Storm item
			ModItem nonStorm = mock(Storm.class);
			when(modData.getItems()).thenReturn(List.of(nonStorm));
			when(modData.id).thenReturn("myMod");
			
			try (MockedStatic<Util> utilMock = mockStatic(Util.class)) {
				utilMock.when(() -> Util.modData(anyString(), eq("myMod"))).thenReturn(modDataDir.toString());
				stormService.loadForMod(modData);
				// No Storm item, so setStormData never called
				verify((Storm) nonStorm, never()).setStormData(any());
			}
		}
		
		@Test
		void loadForModHandlesMissingModDataFolder() {
			try (MockedStatic<Util> utilMock = mockStatic(Util.class)) {
				Path missing = tempDir.resolve("DoesNotExist");
				utilMock.when(() -> Util.modData(anyString(), eq("myMod"))).thenReturn(missing.toString());
				// Should not throw
				stormService.loadForMod(modData);
				// No interaction with storm items
				verifyNoInteractions(modData);
			}
		}
	}
	
	@Nested
	class StormServiceWritingTest {
		
		@Test
		void writeStormFileCreatesXmlInStagingFolder() throws Exception {
			StormData data = new StormData();
			data.setId("Libs/Storm/Test/myStorm");
			data.getCommonSources().add("test.xml");
			
			String modId = "testMod";
			Path stagingDir = tempDir.resolve("Mods/" + modId + "/Data/_stage/" + modId + "/Libs/Storm/");
			Path expectedFile = stagingDir.resolve("myStorm.xml");
			
			try (MockedStatic<Util> utilMock = mockStatic(Util.class)) {
				utilMock.when(() -> Util.modStormStaging(anyString(), eq(modId))).thenReturn(stagingDir);
				
				boolean result = StormService.writeStormFile(tempDir.toString(), modId, data);
				assertTrue(result);
				assertTrue(Files.exists(expectedFile));
				String content = Files.readString(expectedFile);
				assertTrue(content.contains("<storm"));
				assertTrue(content.contains("<common>"));
				assertTrue(content.contains("<source path=\"test.xml\"/>"));
			}
		}
		
		@Test
		void writeStormFileReturnsFalseForNullData() {
			try (MockedStatic<Util> utilMock = mockStatic(Util.class)) {
				boolean result = StormService.writeStormFile(tempDir.toString(), "mod", null);
				assertFalse(result);
			}
		}
		
		@Test
		void writeStormFileReturnsFalseForBlankId() {
			StormData data = new StormData();
			data.setId("   ");
			try (MockedStatic<Util> utilMock = mockStatic(Util.class)) {
				boolean result = StormService.writeStormFile(tempDir.toString(), "mod", data);
				assertFalse(result);
			}
		}
	}
	
	@Nested
	class StormServiceHelpersTest {
		
		@Test
		void entryToIdRemovesExtension() {
			assertEquals("Libs/Storm/Combat/melee", StormService.entryToId("Libs/Storm/Combat/melee.xml"));
			assertEquals("path/without/dot", StormService.entryToId("path/without/dot"));
		}
		
		@Test
		void categoryFromPathExtractsCorrectly() {
			assertEquals("Combat", StormService.categoryFromPath("Libs/Storm/Combat/melee.xml"));
			assertEquals("Ranged", StormService.categoryFromPath("Libs/Storm/Ranged/archery.xml"));
			assertNull(StormService.categoryFromPath("Libs/Storm/melee.xml"));
			assertNull(StormService.categoryFromPath("some/other/path.xml"));
			// Case-insensitive matching
			assertEquals("Combat", StormService.categoryFromPath("libs/storm/combat/melee.xml"));
		}
		
		@Test
		void idToFileNameConvertsCorrectly() {
			assertEquals("melee.xml", StormService.idToFileName("Libs/Storm/Combat/melee"));
			assertEquals("melee.xml", StormService.idToFileName("Libs/Storm/Combat/melee.xml"));
			assertEquals("simple.xml", StormService.idToFileName("simple"));
		}
	}
}