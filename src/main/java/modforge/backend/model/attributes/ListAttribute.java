package modforge.backend.model.attributes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@lombok.extern.slf4j.Slf4j
public non-sealed class ListAttribute<M> extends BaseAttribute<List<M>> {
	public ListAttribute(String name, List<M> value) {
		super(name, value);
	}
	
	@Override
	public ListAttribute<M> deepClone() {
		return new ListAttribute<>(getName(), deepCloneList(value));
	}
	
	@Override
	public ListAttribute<M> deepClone(List<M> newValue) {
		return new ListAttribute<>(name, deepCloneList(newValue));
	}
	
	/**
	 * Helper method to deep clone nested lists
	 */
	private <O> List<O> deepCloneList(List<O> list) {
		if (list == null) return null;
		
		List<O> clonedList = new ArrayList<>(list.size());
		for (O e : list) {
			if (e instanceof Attribute) {
				throw new IllegalArgumentException("No nesting inside attributes");
			} else if (e instanceof Collection<?>) {
				throw new IllegalArgumentException("No nesting inside attributes");
			} else {
				clonedList.add(e);
			}
		}
		return clonedList;
	}
}