package modforge.backend.model.attributes;

import java.util.List;

public class ListAttribute<M> extends Attribute<List<M>> {
	public ListAttribute(String name, List<M> value) {
		super(name, value);
	}
}
