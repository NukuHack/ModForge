package modforge.backend.service;

import modforge.backend.AttributeFactory;
import modforge.backend.BuildHandler;
import modforge.backend.ItemType;
import modforge.backend.model.ModItem;
import modforge.backend.model.attributes.Attribute;
import modforge.backend.model.BaseModItem;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class ModItemBuilder {
	private static final Logger log = Logger.getLogger(ModItemBuilder.class.getName());
	private final List<BuildHandler> handlers;

	public ModItemBuilder(List<BuildHandler> handlers) {
		this.handlers = List.copyOf(handlers);
	}

	public ModItem build(Element element) {
		for (var h : handlers) {
			if (h.isResponsible(element)) return h.handle(element);
		}
		log.fine("No handler matched element <" + element.getLocalName() + ">");
		return null;
	}

	/**
	 * Builds the handler list from ItemType's single source of truth.
	 * Adding or reordering item types only requires a change in ItemType.
	 */
	@SuppressWarnings("unchecked")
	public static ModItemBuilder createDefault() {
		var handlers = ItemType.getHandlerSpecs().stream()
				               .map(spec -> (BuildHandler) new GBuildHandler<>((Class<BaseModItem>) spec.clazz(), spec.idKey()))
				               .toList();
		return new ModItemBuilder(handlers);
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
		public boolean isResponsible(Element el) {
			return el.getLocalName().equalsIgnoreCase(type.getSimpleName());
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
}