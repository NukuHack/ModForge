package modforge.backend.model.storm;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * A custom operation declared at the top of a STORM file.
 *
 * <p>Custom operations can expose "mod attributes" that describe how
 * a stat can be modified (min/max mod values).</p>
 */
@Getter
@lombok.extern.slf4j.Slf4j
public final class CustomStormOperation {
	
	/** Attribute ranges exposed by this operation. */
	private final List<ModAttribute> modAttributes = new ArrayList<>();
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
	public static final class ModAttribute {
		private String stat = "";
		private double minMod = 0;
		private double maxMod = 0;
		
		public String getStat() {
			return stat;
		}
		
		public void setStat(String stat) {
			this.stat = stat == null ? "" : stat;
		}
		
		public double getMinMod() {
			return minMod;
		}
		
		public void setMinMod(double minMod) {
			this.minMod = minMod;
		}
		
		public double getMaxMod() {
			return maxMod;
		}
		
		public void setMaxMod(double maxMod) {
			this.maxMod = maxMod;
		}
		
		@Override
		public String toString() {
			return "ModAttribute{stat='" + stat + "', range=[" + minMod + "," + maxMod + "]}";
		}
	}
}
