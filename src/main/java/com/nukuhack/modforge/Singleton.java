package com.nukuhack.modforge;

import com.nukuhack.modforge.backend.ModData;
import com.nukuhack.modforge.backend.model.E;
import com.nukuhack.modforge.backend.service.ServiceRegistry;
import com.nukuhack.modforge.frontend.MainWindow;
import com.nukuhack.util.IOUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@UtilityClass
public class Singleton {
	@Getter
	private final Path userConfigDir = Util.getConfigDir();
	@Getter
	private final Path userConfig = userConfigDir.resolve("userconfig.json");
	@Getter
	private final Map<E.Language, Map<String, String>> langMap = new EnumMap<>(E.Language.class);
	@Getter
	private final ModData game = new ModData(
		"kdc2", "Kingdom Come Deliverance 2",
		"The game itself : Kingdom Come Deliverance II",
		"Warhorse Studios",
		"1.*", "2026", true
	);
	@Setter
	@Getter
	private ServiceRegistry registry;
	@Setter
	@Getter
	private MainWindow mainWindow;

	/**
	 * Thread-local XMLInputFactory — one pre-configured instance per thread,
	 * avoids repeated factory construction and carries the entity-size fix.
	 */
	public static final ThreadLocal<XMLInputFactory> XML_FACTORY = ThreadLocal.withInitial(() -> {
		var f = XMLInputFactory.newInstance();
		f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		f.setProperty(XMLInputFactory.IS_VALIDATING, false);
		f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);

		f.setProperty(XMLInputFactory.IS_COALESCING, true);
		f.setProperty("jdk.xml.maxGeneralEntitySizeLimit", 0);
		f.setProperty("jdk.xml.totalEntitySizeLimit", 0);
		return f;
	});

	public static final ThreadLocal<DocumentBuilder> DOC_BUILDER = ThreadLocal.withInitial(() -> {
		try {
			var f = DocumentBuilderFactory.newInstance();
			f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			f.setFeature("http://xml.org/sax/features/validation", false);
			f.setFeature("http://xml.org/sax/features/external-general-entities", false);
			f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			return f.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			throw new RuntimeException(e);
		}
	});
	
	static {
		game.setSupportsGameVersions(List.of("*"));
		
		try {
			IOUtil.ensureDirExists(userConfig);
		} catch (IOException e) {
			log.error("Failed to create config directory: {}", userConfig, e);
		}
	}
}