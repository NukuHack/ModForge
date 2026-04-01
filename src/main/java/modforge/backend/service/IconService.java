package modforge.backend.service;

import modforge.backend.DdsConverter;
import modforge.backend.model.IModItem;

import java.io.File;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

public final class IconService {
	private static final Logger log = Logger.getLogger(IconService.class.getName());

	private final UserService configService;

	public IconService(UserService configService) {
		this.configService = configService;
	}

	/**
	 * Return a base64 data-URI for the icon of the given mod item.
	 */
	public String getIcon(IModItem item) {
		if (item == null || item.getAttributes() == null) return null;

		var iconAttr = item.getAttributes().stream()
				.filter(a -> a.getName().equalsIgnoreCase("icon_id") ||
						a.getName().equalsIgnoreCase("IconId"))
				.findFirst().orElse(null);

		String rawValue = iconAttr == null ? null
				: String.valueOf(iconAttr.getValue());

		boolean useFallback = (rawValue == null
				|| rawValue.equals("0")
				|| rawValue.equalsIgnoreCase("replaceme"));

		String iconId = useFallback ? "crime_investigation_icon" : rawValue;
		String folder = useFallback ? null : "Icons";

		return getBase64Icon(iconId, folder);
	}

	/**
	 * Load a DDS icon from IPL_GameData.pak and return it as a base64 PNG data-URI.
	 *
	 * @param iconId         Icon filename (without extension) to search for.
	 * @param matchingFolder Sub-folder inside Libs/UI/Textures, or null.
	 * @return base64 data-URI string, or null on failure.
	 */
	public String getBase64Icon(String iconId, String matchingFolder) {
		String dir = configService.getCurrent().gameDirectory;
		if (dir == null || dir.isBlank()) {
			log.warning("Game directory not set.");
			return null;
		}

		String pakPath = PathFactory.join(dir, "Data", "IPL_GameData.pak");
		File pakFile = new File(pakPath);
		if (!pakFile.exists()) {
			log.warning("IPL_GameData.pak not found: " + pakPath);
			return null;
		}

		String targetDir = (matchingFolder == null || matchingFolder.isBlank())
				? "Libs/UI/Textures"
				: "Libs/UI/Textures/" + matchingFolder;

		try (var zf = new ZipFile(pakFile)) {
			var entry = zf.stream()
					.filter(e ->
							e.getName().toLowerCase(Locale.ROOT)
									.contains(iconId.toLowerCase(Locale.ROOT)) &&
									e.getName().contains(targetDir))
					.findFirst().orElse(null);

			if (entry == null) {
				log.warning("Icon not found in pak: " + iconId);
				return null;
			}

			try (var is = zf.getInputStream(entry)) {
				return DdsConverter.toBase64DataUri(is);
			}
		} catch (UnsupportedOperationException uoe) {
			log.warning("DDS conversion not available (add DDSReader library): " + uoe.getMessage());
			return null;
		} catch (Exception ex) {
			log.severe("Icon load error (" + iconId + "): " + ex.getMessage());
			return null;
		}
	}
}
