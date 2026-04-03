package modforge.backend.model.storm;

/**
 * A task declaration inside a STORM file.
 */
public final class StormTask {

    private String name    = "";
    private String comment = "";
    private String sources = "";
    /** The scripting class name wired to this task. */
    private String taskClass = "";

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String getName()      { return name; }
    public void   setName(String name)      { this.name      = name == null ? "" : name; }

    public String getComment()   { return comment; }
    public void   setComment(String comment)   { this.comment   = comment == null ? "" : comment; }

    public String getSources()   { return sources; }
    public void   setSources(String sources)   { this.sources   = sources == null ? "" : sources; }

    public String getTaskClass() { return taskClass; }
    public void   setTaskClass(String taskClass) { this.taskClass = taskClass == null ? "" : taskClass; }

    @Override
    public String toString() {
        return "StormTask{name='" + name + "', class='" + taskClass + "'}";
    }
}
