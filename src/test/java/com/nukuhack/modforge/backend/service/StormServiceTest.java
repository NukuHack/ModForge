package com.nukuhack.modforge.backend.service;

import com.nukuhack.modforge.backend.ModData;
import com.nukuhack.modforge.backend.model.storm.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extensive test suite for {@link StormService}.
 */
@ExtendWith(MockitoExtension.class)
class StormServiceTest {
	
	@TempDir
	Path tempDir;
	
	@Mock
	private UserConfig userConfig;
	
	@Mock
	private ModData modData;
	
	@BeforeEach
	void setUp() {
	}
	
	// -------------------------------------------------------------------------
	// Helper methods for creating test PAK files and XML content
	// -------------------------------------------------------------------------
	
	/**
	 * Creates a minimal valid Storm XML document.
	 */
	private String minimalStormXml() {
		return "<?xml version=\"1.0\"?>\n<storm/>";
	}
	
	/**
	 * A real Storm XML snippet from the game - truncated for testing.
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
                            <operations/>
                        </rule>
                        <rule name="underwear_woman">
                            <selectors>
                                <isWoman/>
                            </selectors>
                            <operations/>
                        </rule>
                        <rule name="underwear_papezskyLegat_rozaNaked">
                            <selectors>
                                <hasName Name="papezskyLegat_cin_rozaNaked"/>
                            </selectors>
                            <operations/>
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
	
	@Test
	public void testRoundtrip() throws Exception {
		try (InputStream is = new ByteArrayInputStream(realStormXml().getBytes(StandardCharsets.UTF_8))) {
			StormData data = StormService.parse(is);
			String serialized = StormService.serialize(data);
			
			String raw = realStormXml().replaceAll(">\\s+<", ">\n<").replaceAll("(?m)^(?:<\\?|<!)[^\\n]*\\n?", "");
			String out = serialized.replaceAll(">\\s+<", ">\n<").replaceAll("(?m)^(?:<\\?|<!)[^\\n]*\\n?", "");
			
			assertEquals(raw, out);
			
		}
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
		// class no uppercase, name neither, Operation.mode neither, attr.stat same MinMod, MaxMod
		return """
                <?xml version="1.0"?>
                <STORM category="MixedCat">
                    <Common>
                        <Source path="common/base.xml"/>
                    </Common>
                    <Tasks>
                        <Task name="MixedTask" class="MixedClass">
                            <Source path="mixed.xml"/>
                        </Task>
                    </Tasks>
                    <CustomSelectors>
                        <Selector name="mixedSelector">
                            <Attribute name="attr1"/>
                        </Selector>
                    </CustomSelectors>
                    <CustomOperations>
                        <Operation name="mixedOp" mode="multiply">
                            <Attribute stat="Health" minMod="-1" maxMod="2"/>
                        </Operation>
                    </CustomOperations>
                    <Rules>
                        <Rule name="mixedRule">
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
		
		@Test
		void parseMinimalXml() throws Exception {
			try (InputStream is = new ByteArrayInputStream(minimalStormXml().getBytes(StandardCharsets.UTF_8))) {
				StormData data = StormService.parse(is);
				assertNotNull(data);
				assertNull(data.getCategory());
				assertTrue(data.getCommonSources().isEmpty());
				assertTrue(data.getTasks().isEmpty());
				assertTrue(data.getCustomSelectors().isEmpty());
				assertTrue(data.getCustomOperations().isEmpty());
				assertTrue(data.getRules().isEmpty());
			}
		}
		
		@Test
		void parseRealXml() throws Exception {
			try (InputStream is = new ByteArrayInputStream(realStormXml().getBytes(StandardCharsets.UTF_8))) {
				StormData data = StormService.parse(is);
				assertNotNull(data);
				assertNull(data.getCategory());
				assertEquals(8, data.getRules().size());
				
				// Check first rule
				StormRule rule1 = data.getRules().get(0);
				assertEquals("underwear_man", rule1.getName());
				assertEquals(1, rule1.getSelectors().size());
				GenericSelector sel = rule1.getSelectors().get(0);
				assertEquals("isMan", sel.getName());
				
				// Check rule with operation
				StormRule tarasRule = data.getRules().stream()
											  .filter(r -> "underwear_tarasMura_taras".equals(r.getName()))
											  .findFirst().orElse(null);
				assertNotNull(tarasRule);
				assertEquals(1, tarasRule.getOperations().size());
				GenericOperation op = tarasRule.getOperations().get(0);
				assertEquals("setUnderwear", op.getName());
				assertEquals("tarasMura_underwear", op.getAttributes().get("name"));
				
				// Check rule with OR combinator
				StormRule utopenciRule = data.getRules().stream()
												 .filter(r -> "underwear_utopenci".equals(r.getName()))
												 .findFirst().orElse(null);
				assertNotNull(utopenciRule);
				assertEquals(1, utopenciRule.getSelectors().size());
				GenericSelector orSel = utopenciRule.getSelectors().get(0);
				assertEquals("or", orSel.getName());
				assertEquals(3, orSel.getChildren().size());
			}
		}
		
		@Test
		void parseFullXml() throws Exception {
			try (InputStream is = new ByteArrayInputStream(fullStormXml().getBytes(StandardCharsets.UTF_8))) {
				StormData data = StormService.parse(is);
				assertNotNull(data);
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
				
				// Selectors tree - tags are preserved with original casing
				assertEquals(1, rule.getSelectors().size());
				GenericSelector andSelector = rule.getSelectors().get(0);
				assertEquals("and", andSelector.getName());
				assertEquals(2, andSelector.getChildren().size());
				
				GenericSelector hasName = andSelector.getChildren().get(0);
				assertEquals("hasName", hasName.getName());
				assertEquals("foo", hasName.getAttributes().get("name"));
				
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
				StormData data = StormService.parse(is);
				assertNotNull(data);
				assertEquals("MixedCat", data.getCategory());
				
				// Common sources
				assertEquals(1, data.getCommonSources().size());
				assertEquals("common/base.xml", data.getCommonSources().get(0));
				
				// Tasks
				assertEquals(1, data.getTasks().size());
				StormTask task = data.getTasks().get(0);
				assertEquals("MixedTask", task.getName());
				assertEquals("MixedClass", task.getTaskClass());
				assertEquals("mixed.xml", task.getSources());
				
				// Custom selectors - attribute names preserved
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
				assertEquals(-1.0, attr.getMinMod());
				assertEquals(2.0, attr.getMaxMod());
				
				// Rules - tags preserved with original casing, attributes as-is
				assertEquals(1, data.getRules().size());
				StormRule rule = data.getRules().get(0);
				assertEquals("mixedRule", rule.getName());
				assertEquals(1, rule.getSelectors().size());
				GenericSelector hasName = rule.getSelectors().get(0);
				// Tag is lowercased during parse (parseSelector uses toLowerCase)
				assertEquals("HasName", hasName.getName());
				// Attribute "Name" is preserved exactly as in XML
				assertEquals("target", hasName.getAttributes().get("Name"));
				assertEquals(1, rule.getOperations().size());
				GenericOperation setUnderwear = rule.getOperations().get(0);
				assertEquals("SetUnderwear", setUnderwear.getName());
				assertEquals("mixedUnderwear", setUnderwear.getAttributes().get("Name"));
			}
		}
		
		@Test
		void parseMalformedXmlReturnsEmptyData() throws Exception {
			String malformed = "<?xml version=\"1.0\"?><storm><unclosed>";
			try (InputStream is = new ByteArrayInputStream(malformed.getBytes(StandardCharsets.UTF_8))) {
				StormData data = StormService.parse(is);
				assertNotNull(data);
				assertTrue(data.getCommonSources().isEmpty());
				assertTrue(data.getTasks().isEmpty());
				assertTrue(data.getCustomSelectors().isEmpty());
				assertTrue(data.getCustomOperations().isEmpty());
				assertTrue(data.getRules().isEmpty());
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
				StormData data = StormService.parse(is);
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
				StormData data = StormService.parse(is);
				assertEquals(1, data.getTasks().size());
				StormTask task = data.getTasks().get(0);
				assertEquals("first.xml,second.xml", task.getSources());
			}
		}
		
		@Test
		void serializeAndRoundTrip() throws Exception {
			// Build a StormData object programmatically
			StormData original = new StormData();
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
			String serialized = StormService.serialize(original);
			assertNotNull(serialized);
			serialized = serialized.replaceAll(">\\s+<", "><");
			
			// Check for expected elements
			assertTrue(serialized.contains("<storm"), "missing <storm root");
			assertTrue(serialized.contains("category=\"RoundTripCat\""), "missing category attribute");
			assertTrue(serialized.contains("<common>"), "missing <common>");
			assertTrue(serialized.contains("<source path=\"common/test.xml\"/>"), "missing common source");
			assertTrue(serialized.contains("name=\"testTask\""), "missing task name");
			assertTrue(serialized.contains("class=\"TestClass\""), "missing task class");
			assertTrue(serialized.contains("comment=\"test comment\""), "missing task comment");
			assertTrue(serialized.contains("<source path=\"src1.xml\"/>"), "missing src1");
			assertTrue(serialized.contains("<source path=\"src2.xml\"/>"), "missing src2");
			assertTrue(serialized.contains("<customSelectors>"), "missing customSelectors");
			assertTrue(serialized.contains("name=\"customSel\""), "missing customSel name");
			assertTrue(serialized.contains("comment=\"selComment\""), "missing selComment");
			assertTrue(serialized.contains("<attribute name=\"attrA\"/>"), "missing attrA");
			assertTrue(serialized.contains("<customOperations>"), "missing customOperations");
			assertTrue(serialized.contains("name=\"customOp\""), "missing customOp");
			assertTrue(serialized.contains("mode=\"set\""), "missing mode=set");
			assertTrue(serialized.contains("stat=\"StatX\""), "missing StatX");
			assertTrue(serialized.contains("minMod=\"1.5\""), "missing minMod");
			assertTrue(serialized.contains("maxMod=\"2.5\""), "missing maxMod");
			assertTrue(serialized.contains("name=\"testRule\""), "missing testRule");
			assertTrue(serialized.contains("mode=\"add\""), "missing mode=add");
			assertTrue(serialized.contains("comment=\"ruleComment\""), "missing ruleComment");
			assertTrue(serialized.contains("<selectors><and><hasName name=\"target\"/></and></selectors>"), "missing selector tree");
			assertTrue(serialized.contains("<operations><setUnderwear name=\"underwearItem\"/></operations>"), "missing operations");
			
			// Parse back
			try (InputStream is = new ByteArrayInputStream(serialized.getBytes(StandardCharsets.UTF_8))) {
				StormData parsed = StormService.parse(is);
				assertEquals(original.getCategory(), parsed.getCategory());
				assertEquals(original.getCommonSources(), parsed.getCommonSources());
				assertEquals(original.getTasks().size(), parsed.getTasks().size());
				StormTask parsedTask = parsed.getTasks().get(0);
				assertEquals(task.getName(), parsedTask.getName());
				assertEquals(task.getTaskClass(), parsedTask.getTaskClass());
				assertEquals(task.getComment(), parsedTask.getComment());
				assertEquals(task.getSources(), parsedTask.getSources());
				assertEquals(original.getCustomSelectors().size(), parsed.getCustomSelectors().size());
				assertEquals(original.getCustomSelectors().get(0).getName(),
						parsed.getCustomSelectors().get(0).getName());
				assertEquals(original.getCustomOperations().size(), parsed.getCustomOperations().size());
				assertEquals(original.getCustomOperations().get(0).getName(),
						parsed.getCustomOperations().get(0).getName());
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
	// Helper methods for the indexing tests (these would be in the actual service)
	// -------------------------------------------------------------------------
	
	static String entryToId(String entryName) {
		if (entryName.endsWith(".xml")) {
			return entryName.substring(0, entryName.length() - 4);
		}
		return entryName;
	}
	
	static String categoryFromPath(String path) {
		String prefix = "Libs/Storm/";
		int idx = path.toLowerCase().indexOf(prefix.toLowerCase());
		if (idx < 0) return null;
		String after = path.substring(idx + prefix.length());
		int slash = after.indexOf('/');
		if (slash > 0) {
			return after.substring(0, slash);
		}
		return null;
	}
	
	static String idToFileName(String id) {
		if (id.endsWith(".xml")) {
			int lastSlash = id.lastIndexOf('/');
			return lastSlash >= 0 ? id.substring(lastSlash + 1) : id;
		}
		int lastSlash = id.lastIndexOf('/');
		String base = lastSlash >= 0 ? id.substring(lastSlash + 1) : id;
		return base + ".xml";
	}
	
	@Nested
	class StormServiceHelpersTest {
		
		@Test
		void entryToIdRemovesExtension() {
			assertEquals("Libs/Storm/Combat/melee", entryToId("Libs/Storm/Combat/melee.xml"));
			assertEquals("path/without/dot", entryToId("path/without/dot"));
		}
		
		@Test
		void categoryFromPathExtractsCorrectly() {
			assertEquals("Combat", categoryFromPath("Libs/Storm/Combat/melee.xml"));
			assertEquals("Ranged", categoryFromPath("Libs/Storm/Ranged/archery.xml"));
			assertNull(categoryFromPath("Libs/Storm/melee.xml"));
			assertNull(categoryFromPath("some/other/path.xml"));
			assertEquals("combat", categoryFromPath("libs/storm/combat/melee.xml"));
		}
		
		@Test
		void idToFileNameConvertsCorrectly() {
			assertEquals("melee.xml", idToFileName("Libs/Storm/Combat/melee"));
			assertEquals("melee.xml", idToFileName("Libs/Storm/Combat/melee.xml"));
			assertEquals("simple.xml", idToFileName("simple"));
		}
	}
}