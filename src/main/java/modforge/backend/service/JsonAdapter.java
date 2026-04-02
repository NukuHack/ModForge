package modforge.backend.service;

import modforge.backend.model.ModItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

public final class JsonAdapter {
	private static final Logger log = Logger.getLogger(JsonAdapter.class.getName());

	private final Path baseDir;
	private final ObjectMapper mapper;

	public JsonAdapter(String appDataDir) {
		this.baseDir = Path.of(appDataDir, "ModForge");
		this.mapper = new ObjectMapper()
				.enable(SerializationFeature.INDENT_OUTPUT)
				.activateDefaultTyping(
						BasicPolymorphicTypeValidator.builder()
								.allowIfBaseType(Object.class)
								.build(),
						ObjectMapper.DefaultTyping.NON_FINAL
				);
	}

	public List<ModItem> readFromJson(String filePath) {
		File f = new File(filePath);
		if (!f.exists()) return new ArrayList<>();
		try {
			return mapper.readValue(f,
					mapper.getTypeFactory()
							.constructCollectionType(List.class, ModItem.class));
		} catch (IOException e) {
			log.warning("JSON read failed (" + filePath + "): " + e.getMessage());
			return new ArrayList<>();
		}
	}

	public void writeAsJson(List<ModItem> items) {
		if (items == null || items.isEmpty()) return;
		String filename = items.getFirst().getClass().getSimpleName()
				.toLowerCase(Locale.ROOT) + "s.json";
		Path target = baseDir.resolve(filename);
		try {
			Files.createDirectories(target.getParent());
			mapper.writeValue(target.toFile(), items);
		} catch (IOException e) {
			log.warning("JSON write failed: " + e.getMessage());
		}
	}
}
