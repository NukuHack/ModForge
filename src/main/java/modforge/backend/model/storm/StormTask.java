package modforge.backend.model.storm;

import lombok.Getter;

/**
 * A task declaration inside a STORM file.
 */
@Getter
@lombok.extern.slf4j.Slf4j
public final class StormTask {
	
	private String name = "";
	private String comment = "";
	private String sources = "";
	/** The scripting class name wired to this task. */
	private String taskClass = "";
	
	// -------------------------------------------------------------------------
	// Accessors
	// -------------------------------------------------------------------------
	
	public void setName(String name) {
		this.name = name == null ? "" : name;
	}
	
	public void setComment(String comment) {
		this.comment = comment == null ? "" : comment;
	}
	
	public void setSources(String sources) {
		this.sources = sources == null ? "" : sources;
	}
	
	public void setTaskClass(String taskClass) {
		this.taskClass = taskClass == null ? "" : taskClass;
	}
	
	@Override
	public String toString() {
		return "StormTask{name='" + name + "', class='" + taskClass + "'}";
	}
}
