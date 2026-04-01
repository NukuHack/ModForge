package modforge.backend;

import modforge.backend.model.IAttribute;
import modforge.backend.model.IBuildHandler;
import modforge.backend.model.IModItem;
import modforge.backend.model.item.BaseModItem;
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
	public IModItem handle(Element el) {
		try {
			M item = type.getDeclaredConstructor().newInstance();
			item.setIdKey(idAttrKey);

			String idValue = el.getAttribute(idAttrKey);
			item.setId(idValue.isBlank() ? null : idValue);

			var xmlAttrs = el.getAttributes();
			var list = new ArrayList<IAttribute>(xmlAttrs.getLength());
			for (int i = 0; i < xmlAttrs.getLength(); i++) {
				var a = (org.w3c.dom.Attr) xmlAttrs.item(i);
				list.add(AttributeFactory.create(a.getLocalName(), a.getValue()));
			}
			item.setAttributes(list);
			return item;
		} catch (Exception e) {
			Logger.getLogger(GenericBuildHandler.class.getName())
					.warning("Handler failed for " + type.getSimpleName() + ": " + e.getMessage());
			return null;
		}
	}
}
