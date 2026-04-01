package modforge.backend;

// =============================================================================
// EXTENSIONS UTILITY  (mirrors C# Extensions static class)
// =============================================================================

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class Util {
	private Util() {
	}

	public static String readAllText(InputStream is) throws IOException {
		return new String(is.readAllBytes(), StandardCharsets.UTF_8);
	}

	public static String capitalizeFirstOnly(String s) {
		if (s == null || s.isEmpty()) return s;
		return Character.toUpperCase(s.charAt(0))
				+ s.substring(1).toLowerCase(Locale.ROOT);
	}

	public static String replaceWhiteSpace(String s) {
		if (s == null) return "";
		return String.join("_",
				s.trim().toLowerCase(Locale.ROOT).split("\\s+"));
	}

	/**
	 * Get the directory portion of a ZIP entry path,
	 * stripping any trailing XML filename.
	 * Mirrors C# ZipArchiveEntry.GetEntryPath().
	 */
	public static String getEntryPath(String fullName) {
		if (fullName == null) return "";
		String[] parts = fullName.replace('\\', '/').split("/");
		if (parts.length > 0 &&
				parts[parts.length - 1].toLowerCase(Locale.ROOT).contains("xml")) {
			var sb = new StringBuilder();
			for (int i = 0; i < parts.length - 1; i++) {
				if (i > 0) sb.append('/');
				sb.append(parts[i]);
			}
			return sb.toString();
		}
		return fullName;
	}


	// Helper method to normalize XML for comparison
	public static String normalizeXml(String xml) {
		// Remove whitespace between tags and normalize line endings
		return xml.replaceAll(">\\s+<", "><")
				.replaceAll("\\r\\n?", "\n")
				.trim();
	}

	/**
	 * Simple HTML escaping
	 */
	public static String escapeHtml(String s) {
		if (s == null) return "";
		return s.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&#39;");
	}
}
