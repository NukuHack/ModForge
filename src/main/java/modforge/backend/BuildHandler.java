package modforge.backend;

import modforge.backend.model.ModItem;
import org.w3c.dom.Element;

public interface BuildHandler {

	boolean isResponsible(Element el);

	ModItem handle(final Element element);

}
