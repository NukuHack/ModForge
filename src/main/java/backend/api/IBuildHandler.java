package backend.api;

import org.w3c.dom.Element;

public interface IBuildHandler {
	boolean isResponsible(Element element);

	IModItem handle(Element element);
}
