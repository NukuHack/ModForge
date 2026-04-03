package modforge.backend.service;

import modforge.backend.AttributeFactory;
import modforge.backend.ItemType;
import modforge.backend.model.BaseModItem;
import modforge.backend.model.ModItem;
import modforge.backend.model.attributes.Attribute;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

public final class ModItemBuilder {
	private static final Logger log = Logger.getLogger(ModItemBuilder.class.getName());
	
	// O(1) lookup map - element name to handler
	private static final Map<String, BuildHandler> HANDLER_MAP = new HashMap<>();
	
	static {
		// Build the handler map once at class initialization
		for (var spec : ItemType.getHandlerSpecs()) {
			BuildHandler handler = new GBuildHandler<>(
					(Class<? extends BaseModItem>) spec.clazz(),
					spec.idKey()
			);
			// Index by the element name this handler is responsible for
			String elementName = spec.clazz().getSimpleName().toLowerCase(Locale.ROOT);
			HANDLER_MAP.put(elementName, handler);
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
	
	public static ModItem build(Element element) {
		// glue it together yet again, barely works
		final String elementName = element.getLocalName().toLowerCase(Locale.ROOT).replace("_", "");
		final var handler = HANDLER_MAP.get(elementName);
		
		if (handler != null) {
			return handler.handle(element);
		}
		
		log.fine("No handler matched element <" + element.getLocalName() + ">");
		return null;
	}
	
	/**
	 * Generic build handler: recognizes elements whose local name matches the
	 * simple class name (case-insensitive) and populates a configurable ID attribute.
	 */
	protected static final class GBuildHandler<M extends BaseModItem> implements BuildHandler {
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
	
	protected interface BuildHandler {
		ModItem handle(final Element element);
	}
}