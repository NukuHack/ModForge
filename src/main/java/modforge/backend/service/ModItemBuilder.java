package modforge.backend.service;

import modforge.Util;
import modforge.backend.AttributeFactory;
import modforge.backend.ItemType;
import modforge.backend.ModData;
import modforge.backend.model.ModItem;
import modforge.backend.model.attributes.Attribute;
import org.w3c.dom.Element;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

public final class ModItemBuilder {
	private static final Logger log = Logger.getLogger(ModItemBuilder.class.getName());
	
	// O(1) lookup map - element name to handler
	private static final Map<String, BuildHandler> HANDLER_MAP = new HashMap<>();
	private static final Map<Class<? extends ModItem>, CreateHandler> MAKER_MAP = new HashMap<>();
	
	static {
		// Build the handler map once at class initialization
		for (var spec : ItemType.getHandlerSpecs()) {
			HANDLER_MAP.put(spec.name(), new GBuildHandler<>(spec.clazz(), spec.idKey()));
			MAKER_MAP.put(spec.clazz(), new GCreateHandler<>(spec.clazz(), spec.idKey()));
		}
	}
	
	private ModItemBuilder() {
	}
	
	public static ModItem create(final Element el, final ModItem item) {
		final var xmlAttrs = el.getAttributes();
		final var list = new ArrayList<Attribute>(xmlAttrs.getLength());
		for (int i = 0; i < xmlAttrs.getLength(); i++) {
			final var a = (org.w3c.dom.Attr) xmlAttrs.item(i);
			list.add(AttributeFactory.create(a.getLocalName(), a.getValue()));
		}
		item.setAttribute(list);
		return item;
	}
	
	public static ModItem build(final Element element) {
		// glue it together yet again, barely works
		final var elementName = element.getLocalName().toLowerCase(Locale.ROOT);
		final var handler = HANDLER_MAP.get(elementName);
		
		if (handler != null) {
			return handler.handle(element);
		}
		
		log.fine("No handler matched element <" + element.getLocalName() + ">");
		return null;
	}
	
	public static Element build(final ModItem item) {
		// getting the correct one from HANDLER_MAP
		final var maker = MAKER_MAP.get(item.getClass());
		
		if (maker != null) {
			return maker.handle(item);
		}
		
		log.fine("No maker matched item <" + item + ">");
		return null;
	}
	
	
	/**
	 * Deep-copy a mod item, changing its path.
	 */
	private static ModItem deepCopy(ModItem src, String newPath) {
		try {
			final var copy = src.getClass().getDeclaredConstructor().newInstance();
			copy.setId(src.getId());
			copy.setPath(newPath);
			copy.setAttribute(src.getAttributes().stream().map(Attribute::deepClone).toList());
			return copy;
		} catch (Exception e) {
			throw new RuntimeException("Deep copy failed for " + src.getClass().getSimpleName(), e);
		}
	}
	
	/**
	 * @param src ModItem - the data to be copied itself
	 * @param mod ModData - the mod it's going to be copied To
	 * @return ModItem - the element, it might be == to the base, and it could be .equal() but usually not
	 * since path change is reflected in the data of the item
	 */
	public static ModItem deepCopy(ModItem src, ModData mod) {
		final var path = src.getPath();
		final int colon = path.indexOf(':') + 1;
		
		final String prefix;   // e.g. "SomePak.pak:" or ""
		final String innerPath; // e.g. "inner/dir/entry.xml"
		// check for 0 since we incremented it once for later splitting
		if (colon != 0 && colon < path.length()) {
			prefix = path.substring(0, colon);   // includes ':'
			innerPath = path.substring(colon);
		} else {
			prefix = "";
			innerPath = path;
		}
		
		final var fullPath = Path.of(innerPath);
		final var name = fullPath.getFileName().toString();
		final var parent = fullPath.getParent();
		final var xmlFile = Util.modXmlFile(name, mod.id);
		final var fullFinal = prefix + (parent != null ? Util.join(parent.toString(), xmlFile) : xmlFile);
		return deepCopy(src, fullFinal);
	}
	
	protected interface BuildHandler {
		ModItem handle(final Element element);
	}
	
	protected interface CreateHandler {
		Element handle(final ModItem item);
	}
	
	/**
	 * Generic build handler: recognizes elements whose local name matches the
	 * simple class name (case-insensitive) and populates a configurable ID attribute.
	 */
	protected static final class GBuildHandler<M extends ModItem> implements BuildHandler {
		private static final Logger log = Logger.getLogger(GBuildHandler.class.getName());
		private final Class<M> type;
		private final String idAttrKey;
		
		public GBuildHandler(Class<M> type, String idAttrKey) {
			this.type = type;
			this.idAttrKey = idAttrKey;
		}
		
		@Override
		public ModItem handle(final Element element) {
			try {
				final M item = type.getDeclaredConstructor().newInstance();
				
				final String idValue = element.getAttribute(idAttrKey);
				item.setId(idValue.isBlank() ? null : idValue);
				
				return ModItemBuilder.create(element, item);
			} catch (final Exception e) {
				log.warning("Handler failed for " + type.getSimpleName() + ": " + e.getMessage());
				return null;
			}
		}
	}
	protected static final class GCreateHandler<M extends ModItem> implements CreateHandler {
		private static final Logger log = Logger.getLogger(GCreateHandler.class.getName());
		private final Class<M> type;
		private final String idAttrKey;
		
		public GCreateHandler(Class<M> type, String idAttrKey) {
			this.type = type;
			this.idAttrKey = idAttrKey;
		}
		
		@Override
		public Element handle(final ModItem item) {
			return null;
		}
	}
}