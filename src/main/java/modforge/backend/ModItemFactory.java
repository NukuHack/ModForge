package modforge.backend;

import modforge.backend.model.IAttribute;
import modforge.backend.model.IModItem;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.stream.Collectors;

final class ModItemFactory {
	private ModItemFactory() {
	}

	/**
	 * Instantiate a mod item of the given type from an XML element.
	 */
	public static IModItem create(Element element, Class<? extends IModItem> type, String path) {
		try {
			var item = type.getDeclaredConstructor().newInstance();
			item.setPath(path);

			var attrs = element.getAttributes();
			var list = new ArrayList<IAttribute>(attrs.getLength());
			for (int i = 0; i < attrs.getLength(); i++) {
				var a = (org.w3c.dom.Attr) attrs.item(i);
				list.add(AttributeFactory.create(a.getLocalName(), a.getValue()));
			}
			item.setAttributes(list);
			return item;
		} catch (Exception e) {
			throw new RuntimeException("Cannot instantiate " + type.getSimpleName(), e);
		}
	}

	/**
	 * Deep-copy a mod item, optionally changing its path.
	 */
	public static IModItem deepCopy(IModItem src, String newPath) {
		try {
			var copy = src.getClass().getDeclaredConstructor().newInstance();
			copy.setId(src.getId());
			copy.setIdKey(src.getIdKey());
			copy.setPath(newPath);
			var cloned = src.getAttributes().stream()
					.map(IAttribute::deepClone)
					.collect(Collectors.toCollection(ArrayList::new));
			copy.setAttributes(cloned);
			return copy;
		} catch (Exception e) {
			throw new RuntimeException("Deep copy failed for " + src.getClass().getSimpleName(), e);
		}
	}
}
