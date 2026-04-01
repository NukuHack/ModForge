package modforge.backend;

import modforge.backend.model.IModItem;
import modforge.backend.model.Language;

import java.util.*;

public final class ModData {
	public String id = "";
	public String name = "";
	public String description = "";
	public String author = "";
	public String modVersion = "";
	public String createdOn = "";
	public boolean modifiesLevel = false;
	public List<String> supportsGameVersions = new ArrayList<>();
	public List<IModItem> items = new ArrayList<>();
	public Map<String, String> config = new HashMap<>();

	/**
	 * lang-code -> (string-key -> localised-value)
	 */
	public Map<Language, Map<String, String>> localizations = new HashMap<>();

	@Override
	public String toString() {
		return "ModData{" +
				"id='" + id + '\'' +
				", name='" + name + '\'' +
				", description='" + description + '\'' +
				", author='" + author + '\'' +
				", modVersion='" + modVersion + '\'' +
				", createdOn='" + createdOn + '\'' +
				", modifiesLevel=" + modifiesLevel +
				", supportsGameVersions=" + supportsGameVersions +
				", item_size=" + items.size() +
				", lang_size=" + localizations.size() +
				'}';
	}

	public static final ModData BASE_GAME = new ModData();
}
