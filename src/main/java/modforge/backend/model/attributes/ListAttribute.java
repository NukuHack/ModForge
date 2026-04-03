package modforge.backend.model.attributes;

import java.util.ArrayList;
import java.util.List;

public class ListAttribute<M> extends Attribute<List<M>> {
	public ListAttribute(String name, List<M> value) {
		super(name, value);
	}

	@Override
	@SuppressWarnings("unchecked")
	public ListAttribute<M> deepClone() {
		if (this.getValue() instanceof List<?> list) {
			final List<M> clonedList = new ArrayList<>(list.size());
			for (Object element : list) {
				if (element instanceof Attribute attr) {
					// Recursively deep clone nested IAttribute elements
					clonedList.add((M) attr.deepClone());
				} else {
					// Primitives, Strings, etc. are immutable — safe to reuse
					clonedList.add((M) element);
				}
			}
			return new ListAttribute<M>(getName(), clonedList);
		}
		throw new RuntimeException("No list inside a list Attribute ?!?!");
	}
}