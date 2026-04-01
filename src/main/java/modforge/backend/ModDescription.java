package modforge.backend;

import modforge.backend.model.IModItem;

import java.util.ArrayList;
import java.util.List;

public final class ModDescription {
	public String id = "";
	public String name = "";
	public String description = "";
	public String author = "";
	public String modVersion = "";
	public String createdOn = "";
	public boolean modifiesLevel = false;
	public List<String> supportsGameVersions = new ArrayList<>();
	public List<IModItem> modItems = new ArrayList<>();
	public String imagePath;
	/**
	 * Storm rules – extend as needed when Storm logic is ported.
	 */
	public List<Object> stormRules = new ArrayList<>();

	@Override
	public String toString() {
		return "Mod[" + id + "]";
	}
}
