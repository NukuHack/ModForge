package modforge.backend;

import modforge.backend.model.attributes.IAttribute;
import modforge.backend.model.IBuildHandler;
import modforge.backend.model.ModItem;
import modforge.backend.model.item.BaseModItem;
import modforge.backend.service.ModItemBuilder;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Generic build handler: recognizes elements whose local name matches the
 * simple class name (case-insensitive) and populates a configurable ID attribute.
 */
public final class GenericBuildHandler<M extends BaseModItem> implements IBuildHandler {
	private final Class<M> type;
	private final String idAttrKey;

	public GenericBuildHandler(Class<M> type, String idAttrKey) {
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
			item.setIdKey(idAttrKey);

			final String idValue = element.getAttribute(idAttrKey);
			item.setId(idValue.isBlank() ? null : idValue);

			return ModItemBuilder.create(element, item);
		} catch (final Exception e) {
			Logger.getLogger(GenericBuildHandler.class.getName())
				.warning("Handler failed for " + type.getSimpleName() + ": " + e.getMessage());
			return null;
		}
	}
}
