package modforge.backend;

import modforge.backend.model.attributes.IAttribute;
import modforge.backend.model.*;
import modforge.backend.service.*;
import org.w3c.dom.Element;

import java.util.*;

public final class ModItemFactory {
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
			final var copy = src.getClass().getDeclaredConstructor().newInstance();
			copy.setId(src.getId());
			copy.setIdKey(src.getIdKey());
			copy.setPath(newPath);
			final List<IAttribute> cloned = src.getAttributes().stream()
					.map(a -> (IAttribute) a.deepClone()).toList();
			copy.setAttribute(cloned);
			return copy;
		} catch (Exception e) {
			throw new RuntimeException("Deep copy failed for " + src.getClass().getSimpleName(), e);
		}
	}
	public static ModItem deepCopy(ModItem src) {
		return deepCopy(src, src.getPath());
	}
}
