package modforge.backend.model.storm;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A custom operation declared at the top of a STORM file.
 *
 * <p>Custom operations can expose "mod attributes" that describe how
 * a stat can be modified (min/max mod values).</p>
 */
@lombok.extern.slf4j.Slf4j
public final class CustomStormOperation {
	
	/** Attribute ranges exposed by this operation. */
	@Getter
	private final List<ModAttribute> modAttributes = new ArrayList<>();
	@Getter
	private String name = "";
	
	// -------------------------------------------------------------------------
	
	public void setName(String name) {
		this.name = name == null ? "" : name;
	}
	
	@Override
	public String toString() {
		return "CustomStormOperation{name='" + name + "', modAttrs=" + modAttributes.size() + "}";
	}
	
	// -------------------------------------------------------------------------
	// Nested type
	// -------------------------------------------------------------------------
	
	/**
	 * Describes the numeric range of a modifiable attribute within a custom operation.
	 */
	@Getter
	public static final class ModAttribute {
		private String stat = "";
		@Setter
		private double minMod = 0;
		@Setter
		private double maxMod = 0;
		
		public void setStat(String stat) {
			this.stat = stat == null ? "" : stat;
		}
		
		@Override
		public String toString() {
			return "ModAttribute{stat='" + stat + "', range=[" + minMod + "," + maxMod + "]}";
		}
	}
}
