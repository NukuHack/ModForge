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
		return new ListAttribute<M>(getName(), (List<M>) deepCloneList(value));
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public ListAttribute<M> deepClone(List newValue) {
		return new ListAttribute<M>(name, (List<M>) deepCloneList(newValue));
	}
	
	/**
	 * Helper method to deep clone nested lists
	 */
	@SuppressWarnings("unchecked")
	private <O> List<O> deepCloneList(List<?> list) {
		if (list == null) return null;
		
		List<O> clonedList = new ArrayList<>(list.size());
		for (Object element : list) {
			if (element instanceof Attribute) {
				clonedList.add((O) ((Attribute<?>) element).deepClone());
			} else if (element instanceof List) {
				clonedList.add((O) deepCloneList((List<?>) element));
			} else {
				clonedList.add((O) element);
			}
		}
		return clonedList;
	}
}