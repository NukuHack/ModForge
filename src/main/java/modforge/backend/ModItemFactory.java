package modforge.backend;

import modforge.backend.model.attributes.IAttribute;
import modforge.backend.model.ModItem;
import modforge.backend.service.ModItemBuilder;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.stream.Collectors;

final class ModItemFactory {
	private ModItemFactory() {
	}

	/**
	 * Instantiate a mod item of the given type from an XML element.
	 */
	public static ModItem create(Element element, Class<? extends ModItem> type, String path) {
		try {
			final var item = type.getDeclaredConstructor().newInstance();
			item.setPath(path);

			return ModItemBuilder.create(element, item);
		} catch (final Exception e) {
			throw new RuntimeException("Cannot instantiate " + type.getSimpleName(), e);
		}
	}

	/**
	 * Deep-copy a mod item, optionally changing its path.
	 */
	public static ModItem deepCopy(ModItem src, String newPath) {
		try {
			var copy = src.getClass().getDeclaredConstructor().newInstance();
			copy.setId(src.getId());
			copy.setIdKey(src.getIdKey());
			copy.setPath(newPath);
			var cloned = src.getAttributes().stream()
					.map(IAttribute::deepClone)
					.collect(Collectors.toCollection(ArrayList::new));
			copy.setAttribute(cloned);
			return copy;
		} catch (Exception e) {
			throw new RuntimeException("Deep copy failed for " + src.getClass().getSimpleName(), e);
		}
	}
}
