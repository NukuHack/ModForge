package modforge.backend;

// =============================================================================
// EXTENSIONS UTILITY  (mirrors C# Extensions static class)
// =============================================================================

import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.util.function.Predicate;
import java.util.logging.*;
import java.util.zip.*;

public final class Util {
	private static final Logger log = Logger.getLogger(Util.class.getName());
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


	/**
	 * Pack a source folder into a destination PAK file.
	 * The PAK will contain the contents of the source folder as the root,
	 * maintaining the relative directory structure.
	 *
	 * @param sourceFolder   The folder to pack (its contents become the PAK root)
	 * @param destPakFile    The output PAK file path
	 * @param fileFilter     Optional filter to exclude certain files (can be null to include all)
	 * @param stripMetadata  If true, removes timestamps and other metadata from ZIP entries
	 * @return true if packing succeeded, false otherwise
	 */
	public static boolean packFolder(Path sourceFolder, Path destPakFile, Predicate<Path> fileFilter, boolean stripMetadata) {
		if (!Files.exists(sourceFolder) || !Files.isDirectory(sourceFolder)) {
			log.warning("Source folder does not exist: " + sourceFolder);
			return false;
		}
		final FileTime ft = FileTime.fromMillis(0);
		try {
			Files.createDirectories(destPakFile.getParent());
			Files.deleteIfExists(destPakFile);

			try (var fos = new FileOutputStream(destPakFile.toFile());
				 var zout = new ZipOutputStream(fos)) {

				// Optionally set the ZipOutputStream comment to empty or fixed
				if (stripMetadata) {
					zout.setComment(""); // Remove any comments
				}

				Files.walk(sourceFolder)
						.filter(Files::isRegularFile)
						.filter(path -> fileFilter == null || fileFilter.test(path))
						.forEach(file -> {
							try {
								// Get the path relative to the source folder
								String relPath = sourceFolder.relativize(file)
										.toString()
										.replace('\\', '/');

								// Create ZipEntry with a fixed timestamp to strip metadata
								ZipEntry entry = new ZipEntry(relPath);

								if (stripMetadata) {
									// Set a fixed timestamp (Unix epoch, or any constant value)
									// This prevents storing the actual file modification time
									entry.setTime(0L); // Jan 1 1970 00:00:00 UTC

									// Set other metadata to fixed/default values
									entry.setCreationTime(ft);
									entry.setLastAccessTime(ft);
									entry.setLastModifiedTime(ft);

									// Optionally set compression method and level
									entry.setMethod(ZipEntry.DEFLATED);
								}

								zout.putNextEntry(entry);
								Files.copy(file, zout);
								zout.closeEntry();

								log.fine("Added to PAK: " + relPath);
							} catch (IOException e) {
								log.warning("Cannot add to pak: " + file + " - " + e.getMessage());
							}
						});
			}

			log.info("PAK created: " + destPakFile);
			return true;

		} catch (IOException e) {
			log.severe("PAK creation failed: " + e.getMessage());
			return false;
		}
	}

	/**
	 * Pack a source folder into a destination PAK file.
	 * Convenience method with no file filter.
	 */
	public static boolean packFolder(Path sourceFolder, Path destPakFile) {
		return packFolder(sourceFolder, destPakFile, null, true);
	}

	/**
	 * Pack a source folder into a destination PAK file, excluding the PAK file itself.
	 * Useful when packing from a folder that might contain the target PAK.
	 */
	public static boolean packFolderExcludingSelf(Path sourceFolder, Path destPakFile) {
		return packFolder(sourceFolder, destPakFile, path -> !path.equals(destPakFile), true);
	}

	public static void deleteRecursively(Path path) {
		if (Files.exists(path)) {
			try {
				Files.walk(path)
						.sorted(Comparator.reverseOrder())
						.forEach(p -> {
							try { Files.deleteIfExists(p); }
							catch (IOException e) { throw new RuntimeException(e); }
						});
			} catch (IOException ex) {
				log.info("Could not delete file/folder " + path + " : " + ex);
			}
		}
	}
}