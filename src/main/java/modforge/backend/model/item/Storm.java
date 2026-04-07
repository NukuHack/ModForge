package modforge.backend.model.item;

import lombok.Getter;
import lombok.Setter;
import modforge.backend.model.BaseModItem;
import modforge.backend.model.storm.StormData;

/**
 * {@code ModItem} representation of a Storm script entry.
 *
 * <p>Previously this was a flat item with a {@code HashMap}, which broke as soon
 * as selectors/operations needed to be nested more than one level deep. Now it
 * carries a fully-parsed {@link StormData} object that supports arbitrary tree
 * depth for both selectors and operations.</p>
 *
 * <p>The base-class {@code id} / {@code attributes} fields are still populated
 * by {@link modforge.backend.service.ModItemBuilder} for the normal item-list
 * display pipeline (search, filter, etc.).  The rich {@link StormData} payload is
 * populated separately by {@link modforge.backend.service.StormService} after the
 * PAK scan.</p>
 */
@lombok.extern.slf4j.Slf4j
public class Storm extends BaseModItem {
	
	/** Fully-parsed Storm file contents. May be {@code null} if not yet loaded. */
	@Getter
	@Setter
	private StormData stormData;
	
	public Storm() {
	}
	
	@Override
	public String toString() {
		return "Storm{id='" + getId() + "', parsed=" + (stormData != null) + "}";
	}
}
