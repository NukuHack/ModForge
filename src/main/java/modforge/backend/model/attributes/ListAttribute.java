package modforge.backend.model.attributes;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
public class ListAttribute<M> extends BaseAttribute<List<M>> {
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
	private List<M> deepCloneList(List<M> list) {
		if (list == null)
			return null;
		
		List<M> clonedList = new ArrayList<>(list.size());
		for (M e : list) {
			if (e instanceof Attribute<?> a) {
				clonedList.add((M) a.deepClone());
			} else if (e instanceof Collection<?>) {
				throw new IllegalArgumentException("No nesting inside attributes, Use ListAttribute for that");
			} else {
				clonedList.add(e);
			}
		}
		return clonedList;
	}
}