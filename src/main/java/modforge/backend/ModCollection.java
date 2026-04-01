package modforge.backend;

import java.util.ArrayList;

public final class ModCollection extends ArrayList<ModDescription> {
	public void addMod(ModDescription m) {
		add(m);
	}

	public void removeMod(ModDescription m) {
		remove(m);
	}

	public ModDescription getMod(String id) {
		return stream().filter(m -> id.equals(m.id)).findFirst().orElse(null);
	}
}
