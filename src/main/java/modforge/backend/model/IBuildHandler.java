package modforge.backend.model;

import org.w3c.dom.Element;

public interface IBuildHandler {
	boolean isResponsible(Element element);

	IModItem handle(Element element);
}
