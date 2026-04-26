package com.nukuhack.modforge.backend.service;

import com.nukuhack.modforge.backend.model.Attribute;
import com.nukuhack.modforge.backend.model.Storm;
import com.nukuhack.modforge.backend.model.Storm.StormRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extensive test suite for {@link StormService}.
 */
@ExtendWith(MockitoExtension.class)
class StormServiceTest extends BaseServiceTest {

	@BeforeEach
	void setUp() {
	}

	/**
	 * Creates a minimal valid Storm XML document.
	 */
	private String minimalStormXml() {
		return "<?xml version=\"1.0\"?>\n<storm/>";
	}

	/**
	 * A real Storm XML snippet from the game - truncated for testing.
	 */
	private String realStormXml() throws IOException {
		return readResourceString("item_xml/underwear.xml");
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

	private String attrValue(Attribute.XmlNode node, String attrName) {
		return node.attributes().stream()
				.filter(a -> attrName.equals(a.getName()))
				.map(a -> a.getValue().toString())
				.findFirst().orElse("");
	}

	private Attribute.XmlNode findChildByTag(Attribute.XmlNode node, String tag) {
		return node.children().stream()
				.filter(c -> c.tag().equalsIgnoreCase(tag))
				.findFirst().orElse(null);
	}

	@Nested
	class StormParserTest {

		@Test
		void parseMinimalXml() throws Exception {
			try (InputStream is = new ByteArrayInputStream(minimalStormXml().getBytes(StandardCharsets.UTF_8))) {
				Storm data = StormService.parse(is);
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
		void fullXmlRoundTrip() throws Exception {
			var xml = realStormXml().replaceAll(">\\s+<", ">\n<").trim();
			try (InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
				Storm data = StormService.parse(is);
				String reSerialized = StormService.serialize(data).replaceAll(">\\s+<", ">\n<").trim();
				var inter = xml.substring(xml.indexOf('>') + 2);
				assertEquals(inter.substring(inter.indexOf('>') + 2), reSerialized.substring(reSerialized.indexOf('>') + 2));
			}
		}

		@Test
		void parseRealXml() throws Exception {
			try (InputStream is = new ByteArrayInputStream(realStormXml().getBytes(StandardCharsets.UTF_8))) {
				Storm data = StormService.parse(is);
				assertNotNull(data);
				assertNull(data.getCategory());
				assertEquals(45, data.getRules().size());

				StormRule rule1 = data.getRules().get(0);
				assertEquals("underwear_man", rule1.name());
				assertEquals(1, rule1.selectors().size());
				Attribute.XmlNode sel = rule1.selectors().get(0);
				assertEquals("isMan", sel.tag());

				StormRule tarasRule = data.getRules().stream()
						.filter(r -> "underwear_tarasMura_taras".equals(r.name()))
						.findFirst().orElse(null);
				assertNotNull(tarasRule);
				assertEquals(1, tarasRule.operations().size());
				Attribute.XmlNode op = tarasRule.operations().get(0);
				assertEquals("setUnderwear", op.tag());
				assertEquals("tarasMura_underwear", attrValue(op, "name"));

				StormRule utopenciRule = data.getRules().stream()
						.filter(r -> "underwear_utopenci".equals(r.name()))
						.findFirst().orElse(null);
				assertNotNull(utopenciRule);
				assertEquals(1, utopenciRule.selectors().size());
				Attribute.XmlNode orSel = utopenciRule.selectors().get(0);
				assertEquals("or", orSel.tag());
				assertEquals(3, orSel.children().size());
			}
		}

		@Test
		void parseFullXml() throws Exception {
			try (InputStream is = new ByteArrayInputStream(fullStormXml().getBytes(StandardCharsets.UTF_8))) {
				Storm data = StormService.parse(is);
				assertNotNull(data);
				assertEquals("TestCat", data.getCategory());

				assertEquals(1, data.getCommonSources().size());
				assertEquals("common/base.xml", data.getCommonSources().get(0));

				assertEquals(2, data.getTasks().size());
				Attribute.XmlNode task1 = data.getTasks().get(0);
				assertEquals("task", task1.tag());
				assertEquals("CombatTask", attrValue(task1, "name"));
				assertEquals("CombatClass", attrValue(task1, "class"));
				assertEquals("Main combat", attrValue(task1, "comment"));
				assertEquals("combat/main.xml", attrValue(task1, "sources"));

				Attribute.XmlNode task2 = data.getTasks().get(1);
				assertEquals("SimpleTask", attrValue(task2, "name"));
				assertEquals("simple.xml", attrValue(task2, "sources"));

				assertEquals(1, data.getCustomSelectors().size());
				Attribute.XmlNode selector = data.getCustomSelectors().get(0);
				assertEquals("selector", selector.tag());
				assertEquals("mySelector", attrValue(selector, "name"));
				assertEquals("test", attrValue(selector, "comment"));
				assertEquals(1, selector.children().size());
				Attribute.XmlNode attrNode = selector.children().get(0);
				assertEquals("attribute", attrNode.tag());
				assertEquals("attr1", attrValue(attrNode, "name"));

				assertEquals(1, data.getCustomOperations().size());
				Attribute.XmlNode op = data.getCustomOperations().get(0);
				assertEquals("operation", op.tag());
				assertEquals("myOp", attrValue(op, "name"));
				assertEquals("add", attrValue(op, "mode"));
				assertEquals(1, op.children().size());
				Attribute.XmlNode modAttr = op.children().get(0);
				assertEquals("attribute", modAttr.tag());
				assertEquals("Strength", attrValue(modAttr, "stat"));
				assertEquals("0", attrValue(modAttr, "minMod"));
				assertEquals("1", attrValue(modAttr, "maxMod"));

				assertEquals(1, data.getRules().size());
				StormRule rule = data.getRules().get(0);
				assertEquals("testRule", rule.name());
				assertEquals("test comment", rule.comment());
				assertEquals("override", rule.mode());

				assertEquals(1, rule.selectors().size());
				Attribute.XmlNode andSelector = rule.selectors().get(0);
				assertEquals("and", andSelector.tag());
				assertEquals(2, andSelector.children().size());

				Attribute.XmlNode hasName = andSelector.children().get(0);
				assertEquals("hasName", hasName.tag());
				assertEquals("foo", attrValue(hasName, "name"));

				Attribute.XmlNode orSelector = andSelector.children().get(1);
				assertEquals("or", orSelector.tag());
				assertEquals(2, orSelector.children().size());
				Attribute.XmlNode isMan = orSelector.children().get(0);
				assertEquals("isMan", isMan.tag());
				Attribute.XmlNode notSelector = orSelector.children().get(1);
				assertEquals("not", notSelector.tag());
				assertEquals(1, notSelector.children().size());
				Attribute.XmlNode isWoman = notSelector.children().get(0);
				assertEquals("isWoman", isWoman.tag());

				assertEquals(2, rule.operations().size());
				Attribute.XmlNode setUnderwear = rule.operations().get(0);
				assertEquals("setUnderwear", setUnderwear.tag());
				assertEquals("bar", attrValue(setUnderwear, "name"));
				Attribute.XmlNode nestedOp = rule.operations().get(1);
				assertEquals("nestedOp", nestedOp.tag());
				assertEquals(1, nestedOp.children().size());
				Attribute.XmlNode childOp = nestedOp.children().get(0);
				assertEquals("childOp", childOp.tag());
				assertEquals("x", attrValue(childOp, "value"));
			}
		}

		@Test
		void parseMixedCaseXml() throws Exception {
			try (InputStream is = new ByteArrayInputStream(mixedCaseStormXml().getBytes(StandardCharsets.UTF_8))) {
				Storm data = StormService.parse(is);
				assertNotNull(data);
				assertEquals("MixedCat", data.getCategory());

				assertEquals(1, data.getCommonSources().size());
				assertEquals("common/base.xml", data.getCommonSources().get(0));

				assertEquals(1, data.getTasks().size());
				Attribute.XmlNode task = data.getTasks().get(0);
				assertEquals("Task", task.tag());
				assertEquals("MixedTask", attrValue(task, "name"));
				assertEquals("MixedClass", attrValue(task, "class"));
				assertEquals("mixed.xml", attrValue(task, "sources"));

				assertEquals(1, data.getCustomSelectors().size());
				Attribute.XmlNode selector = data.getCustomSelectors().get(0);
				assertEquals("Selector", selector.tag());
				assertEquals("mixedSelector", attrValue(selector, "name"));
				assertEquals(1, selector.children().size());
				assertEquals("attr1", attrValue(selector.children().get(0), "name"));

				assertEquals(1, data.getCustomOperations().size());
				Attribute.XmlNode op = data.getCustomOperations().get(0);
				assertEquals("Operation", op.tag());
				assertEquals("mixedOp", attrValue(op, "name"));
				assertEquals("multiply", attrValue(op, "mode"));

				assertEquals(1, data.getRules().size());
				StormRule rule = data.getRules().get(0);
				assertEquals("mixedRule", rule.name());
				assertEquals(1, rule.selectors().size());
				Attribute.XmlNode hasName = rule.selectors().get(0);
				assertEquals("HasName", hasName.tag());
				assertEquals("target", attrValue(hasName, "Name"));
				assertEquals(1, rule.operations().size());
				Attribute.XmlNode setUnderwear = rule.operations().get(0);
				assertEquals("SetUnderwear", setUnderwear.tag());
				assertEquals("mixedUnderwear", attrValue(setUnderwear, "Name"));
			}
		}

		@Test
		void parseMalformedXmlReturnsEmptyData() throws Exception {
			String malformed = "<?xml version=\"1.0\"?><storm><unclosed>";
			try (InputStream is = new ByteArrayInputStream(malformed.getBytes(StandardCharsets.UTF_8))) {
				Storm data = StormService.parse(is);
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
				Storm data = StormService.parse(is);
				assertEquals(1, data.getTasks().size());
				Attribute.XmlNode task = data.getTasks().get(0);
				assertEquals("file1.xml,file2.xml", attrValue(task, "sources"));
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
				Storm data = StormService.parse(is);
				assertEquals(1, data.getTasks().size());
				Attribute.XmlNode task = data.getTasks().get(0);
				assertEquals("first.xml,second.xml", attrValue(task, "sources"));
			}
		}

		@Test
		void serializeAndRoundTrip() throws Exception {

			Storm original = new Storm();
			original.setCategory("RoundTripCat");

			original.getCommonSources().add("common/test.xml");

			Map<String, String> taskAttrs = new LinkedHashMap<>();
			taskAttrs.put("name", "testTask");
			taskAttrs.put("class", "TestClass");
			taskAttrs.put("comment", "test comment");
			taskAttrs.put("sources", "src1.xml,src2.xml");
			original.getTasks().add(Storm.node("task", taskAttrs));

			Map<String, String> selAttrs = new LinkedHashMap<>();
			selAttrs.put("name", "customSel");
			selAttrs.put("comment", "selComment");
			Attribute.XmlNode selNode = Storm.node("selector", selAttrs);
			Map<String, String> attrAttrs = new LinkedHashMap<>();
			attrAttrs.put("name", "attrA");
			selNode.children().add(Storm.node("attribute", attrAttrs));
			original.getCustomSelectors().add(selNode);

			Map<String, String> opAttrs = new LinkedHashMap<>();
			opAttrs.put("name", "customOp");
			opAttrs.put("mode", "set");
			Attribute.XmlNode opNode = Storm.node("operation", opAttrs);
			Map<String, String> modAttrs = new LinkedHashMap<>();
			modAttrs.put("stat", "StatX");
			modAttrs.put("minMod", "1.5");
			modAttrs.put("maxMod", "2.5");
			opNode.children().add(Storm.node("attribute", modAttrs));
			original.getCustomOperations().add(opNode);

			Map<String, String> ruleAttrs = new LinkedHashMap<>();
			ruleAttrs.put("name", "testRule");
			ruleAttrs.put("comment", "ruleComment");
			ruleAttrs.put("mode", "add");
			Attribute.XmlNode ruleAttrsNode = Storm.node("rule", ruleAttrs);

			Attribute.XmlNode andSel = Storm.node("and");
			Map<String, String> hasNameAttrs = new LinkedHashMap<>();
			hasNameAttrs.put("name", "target");
			andSel.children().add(Storm.node("hasName", hasNameAttrs));

			Map<String, String> setOpAttrs = new LinkedHashMap<>();
			setOpAttrs.put("name", "underwearItem");
			Attribute.XmlNode setOp = Storm.node("setUnderwear", setOpAttrs);

			StormRule rule = new StormRule(ruleAttrsNode,
					List.of(andSel),
					List.of(setOp));
			original.getRules().add(rule);

			String serialized = StormService.serialize(original);
			assertNotNull(serialized);

			String normalized = serialized.replaceAll(">\\s+<", "><");

			assertTrue(normalized.contains("<storm"), "missing <storm root");
			assertTrue(normalized.contains("category=\"RoundTripCat\""), "missing category attribute");
			assertTrue(normalized.contains("<common>"), "missing <common>");
			assertTrue(normalized.contains("<source path=\"common/test.xml\"/>"), "missing common source");
			assertTrue(normalized.contains("name=\"testTask\""), "missing task name");
			assertTrue(normalized.contains("class=\"TestClass\""), "missing task class");
			assertTrue(normalized.contains("comment=\"test comment\""), "missing task comment");
			assertTrue(normalized.contains("<source path=\"src1.xml\"/>"), "missing src1");
			assertTrue(normalized.contains("<source path=\"src2.xml\"/>"), "missing src2");
			assertTrue(normalized.contains("<customSelectors>"), "missing customSelectors");
			assertTrue(normalized.contains("name=\"customSel\""), "missing customSel name");
			assertTrue(normalized.contains("comment=\"selComment\""), "missing selComment");
			assertTrue(normalized.contains("<attribute name=\"attrA\"/>"), "missing attrA");
			assertTrue(normalized.contains("<customOperations>"), "missing customOperations");
			assertTrue(normalized.contains("name=\"customOp\""), "missing customOp");
			assertTrue(normalized.contains("mode=\"set\""), "missing mode=set");
			assertTrue(normalized.contains("stat=\"StatX\""), "missing StatX");
			assertTrue(normalized.contains("minMod=\"1.5\""), "missing minMod");
			assertTrue(normalized.contains("maxMod=\"2.5\""), "missing maxMod");
			assertTrue(normalized.contains("name=\"testRule\""), "missing testRule");
			assertTrue(normalized.contains("mode=\"add\""), "missing mode=add");
			assertTrue(normalized.contains("comment=\"ruleComment\""), "missing ruleComment");
			assertTrue(normalized.contains("<and>"), "missing and selector");
			assertTrue(normalized.contains("<hasName name=\"target\"/>"), "missing hasName");
			assertTrue(normalized.contains("<setUnderwear name=\"underwearItem\"/>"), "missing setUnderwear");

			try (InputStream is = new ByteArrayInputStream(serialized.getBytes(StandardCharsets.UTF_8))) {
				Storm parsed = StormService.parse(is);
				assertEquals(original.getCategory(), parsed.getCategory());
				assertEquals(original.getCommonSources(), parsed.getCommonSources());

				assertEquals(original.getTasks().size(), parsed.getTasks().size());
				Attribute.XmlNode parsedTask = parsed.getTasks().get(0);
				assertEquals("testTask", attrValue(parsedTask, "name"));
				assertEquals("TestClass", attrValue(parsedTask, "class"));
				assertEquals("test comment", attrValue(parsedTask, "comment"));
				assertEquals("src1.xml,src2.xml", attrValue(parsedTask, "sources"));

				assertEquals(original.getCustomSelectors().size(), parsed.getCustomSelectors().size());
				assertEquals(original.getCustomOperations().size(), parsed.getCustomOperations().size());
				assertEquals(original.getRules().size(), parsed.getRules().size());

				StormRule parsedRule = parsed.getRules().get(0);
				assertEquals(rule.name(), parsedRule.name());
				assertEquals(rule.comment(), parsedRule.comment());
				assertEquals(rule.mode(), parsedRule.mode());
				assertEquals(rule.selectors().size(), parsedRule.selectors().size());
				assertEquals(rule.operations().size(), parsedRule.operations().size());
			}
		}

		@Test
		void serializeSkipsUnderscorePrefixedAttributes() throws Exception {
			Storm original = new Storm();

			Map<String, String> opAttrs = new LinkedHashMap<>();
			opAttrs.put("name", "testOp");
			opAttrs.put("_isStat", "true");
			opAttrs.put("_isSpan", "false");
			Attribute.XmlNode opNode = Storm.node("setUnderwear", opAttrs);

			Map<String, String> ruleAttrs = new LinkedHashMap<>();
			ruleAttrs.put("name", "testRule");
			Attribute.XmlNode ruleAttrsNode = Storm.node("rule", ruleAttrs);

			StormRule rule = new StormRule(ruleAttrsNode, List.of(), List.of(opNode));
			original.getRules().add(rule);

			String serialized = StormService.serialize(original);

			assertFalse(serialized.contains("_isStat"), "UI-only flag leaked to XML");
			assertFalse(serialized.contains("_isSpan"), "UI-only flag leaked to XML");
			assertTrue(serialized.contains("name=\"testOp\""), "valid attribute missing");
		}

		@Test
		void testRoundtrip() throws Exception {
			try (InputStream is = new ByteArrayInputStream(realStormXml().getBytes(StandardCharsets.UTF_8))) {
				Storm data = StormService.parse(is);
				String serialized = StormService.serialize(data);

				try (InputStream is2 = new ByteArrayInputStream(serialized.getBytes(StandardCharsets.UTF_8))) {
					Storm reparsed = StormService.parse(is2);

					assertEquals(data.getRules().size(), reparsed.getRules().size());
					for (int i = 0; i < data.getRules().size(); i++) {
						StormRule origRule = data.getRules().get(i);
						StormRule newRule = reparsed.getRules().get(i);
						assertEquals(origRule.name(), newRule.name());
						assertEquals(origRule.selectors().size(), newRule.selectors().size());
						assertEquals(origRule.operations().size(), newRule.operations().size());
					}
				}
			}
		}

		@Test
		void parseRulesWithConditionsAlias() throws Exception {
			String xml = """
                    <?xml version="1.0"?>
                    <storm>
                        <rules>
                            <rule name="testRule">
                                <conditions>
                                    <isMan/>
                                </conditions>
                                <operations>
                                    <setUnderwear name="test"/>
                                </operations>
                            </rule>
                        </rules>
                    </storm>
                    """;
			try (InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
				Storm data = StormService.parse(is);
				assertEquals(1, data.getRules().size());
				StormRule rule = data.getRules().get(0);
				assertEquals(1, rule.selectors().size());
				assertEquals("isMan", rule.selectors().get(0).tag());
			}
		}

		@Test
		void parseSourceWithTextContent() throws Exception {
			String xml = """
                    <?xml version="1.0"?>
                    <storm>
                        <common>
                            <source>path/from/text.xml</source>
                        </common>
                    </storm>
                    """;
			try (InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
				Storm data = StormService.parse(is);
				assertEquals(1, data.getCommonSources().size());
				assertEquals("path/from/text.xml", data.getCommonSources().get(0));
			}
		}
	}

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

	@Nested
	class StormModelTest {

		@Test
		void stormRuleAccessorsWork() {
			Map<String, String> attrs = new LinkedHashMap<>();
			attrs.put("name", "testRule");
			attrs.put("mode", "override");
			attrs.put("comment", "test comment");
			Attribute.XmlNode attrsNode = Storm.node("rule", attrs);

			StormRule rule = new StormRule(attrsNode, List.of(), List.of());

			assertEquals("testRule", rule.name());
			assertEquals("override", rule.mode());
			assertEquals("test comment", rule.comment());
		}

		@Test
		void stormRuleAccessorsReturnEmptyForMissingAttrs() {
			Attribute.XmlNode attrsNode = Storm.node("rule");
			StormRule rule = new StormRule(attrsNode, List.of(), List.of());

			assertEquals("", rule.name());
			assertEquals("", rule.mode());
			assertEquals("", rule.comment());
		}

		@Test
		void isStormLoadedReturnsCorrectly() {
			Storm empty = new Storm();
			assertFalse(empty.isStormLoaded());

			Storm withRule = new Storm();
			withRule.getRules().add(new StormRule(Storm.node("rule"), List.of(), List.of()));
			assertTrue(withRule.isStormLoaded());

			Storm withTask = new Storm();
			withTask.getTasks().add(Storm.node("task"));
			assertTrue(withTask.isStormLoaded());

			Storm withCommon = new Storm();
			withCommon.getCommonSources().add("test.xml");
			assertTrue(withCommon.isStormLoaded());
		}

		@Test
		void isStatOpAndIsSpanOpWork() {
			Map<String, String> attrsWithFlags = new LinkedHashMap<>();
			attrsWithFlags.put("_isStat", "true");
			attrsWithFlags.put("_isSpan", "false");
			Attribute.XmlNode opNode = Storm.node("operation", attrsWithFlags);

			assertTrue(Storm.isStatOp(opNode));
			assertFalse(Storm.isSpanOp(opNode));

			Attribute.XmlNode noFlagsNode = Storm.node("operation");
			assertFalse(Storm.isStatOp(noFlagsNode));
			assertFalse(Storm.isSpanOp(noFlagsNode));
		}

		@Test
		void nodeFactoryMethodsWork() {
			Map<String, String> attrs = new LinkedHashMap<>();
			attrs.put("key", "value");

			Attribute.XmlNode withAttrs = Storm.node("test", attrs);
			assertEquals("test", withAttrs.tag());
			assertEquals(1, withAttrs.attributes().size());
			assertEquals("value", attrValue(withAttrs, "key"));
			assertTrue(withAttrs.children().isEmpty());

			Attribute.XmlNode withoutAttrs = Storm.node("leaf");
			assertEquals("leaf", withoutAttrs.tag());
			assertTrue(withoutAttrs.attributes().isEmpty());
			assertTrue(withoutAttrs.children().isEmpty());
		}
	}

	@Nested
	class DomSerializationTest {

		@Test
		void serializeToDocument() throws Exception {
			Storm storm = new Storm();
			storm.setCategory("DomTest");

			Document doc = DocumentBuilderFactory.newInstance()
					.newDocumentBuilder().newDocument();

			org.w3c.dom.Element element = StormService.serialize(storm, doc);

			assertEquals("storm", element.getTagName());
			assertEquals("DomTest", element.getAttribute("category"));
		}

		@Test
		void serializeEmptyStormToDocument() throws Exception {
			Storm storm = new Storm();
			Document doc = DocumentBuilderFactory.newInstance()
					.newDocumentBuilder().newDocument();

			org.w3c.dom.Element element = StormService.serialize(storm, doc);

			assertEquals("storm", element.getTagName());
			assertFalse(element.hasAttribute("category"));
			assertEquals(0, element.getChildNodes().getLength());
		}
	}
}