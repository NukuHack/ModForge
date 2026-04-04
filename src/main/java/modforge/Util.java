package modforge;

// =============================================================================
// EXTENSIONS UTILITY  (mirrors C# Extensions static class)
// =============================================================================

import modforge.backend.model.Language;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class Util {
	public static final String APP_NAME = "ModForge";
	public static final String os = System.getProperty("os.name").toLowerCase();
	private static final Logger log = Logger.getLogger(Util.class.getName());
	
	private Util() {
	}
	
	public static String readAllText(InputStream is) throws IOException {
		return new String(is.readAllBytes(), StandardCharsets.UTF_8);
	}
	
	public static String capitalStart(String s) {
		if (s == null || s.isEmpty())
			return s;
		return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
	}
	
	public static Path gameDataDir(String root) {
		return joinP(root, "Data");
	}
	
	public static Path gameLocalDir(String root) {
		return joinP(root, "Localization");
	}
	public static Path modLocalDir(String root, String modId) {
		return joinP(modFolder(root, modId), "Localization");
	}
	
	public static Path modStaging(String gameDir, String modId) {
		return Path.of(Util.modData(gameDir, modId), "_stage");
	}
	
	public static String locImport(String root, String lang) {
		return join(root, "Localization", lang + "_xml.pak");
	}
	
	public static Path locExport(String root, String lang, String modId) {
		return joinP(modLocalDir(root, modId), lang + "_xml", "text__" + modId + ".xml");
	}
	
	public static String modFolder(String root, String modId) {
		return join(root, "Mods", modId);
	}
	
	public static Path modFolder(String root) {
		return joinP(root, "Mods");
	}
	
	public static String modData(String root, String modId) {
		return join(root, "Mods", modId, "Data");
	}
	
	public static String stormDir(String root, String modId) {
		return join(modData(root, modId), "Libs", "Storm");
	}
	
	public static Set<String> allLocPaths(String root) {
		return Language.getAllDisplayNames().stream().map(l -> locImport(root, l)).collect(Collectors.toSet());
	}
	
	/**
	 * Join path segments using forward slashes (cross-platform safe).
	 */
	public static String join(String... parts) {
		return String.join("/", parts);
	}
	public static Path joinP(String... parts) {
		return Path.of(join(parts));
	}
	public static Path joinP(Path base, String... parts) {
		return Path.of(base.toString(), join(parts));
	}
	
	// Helper method to normalize XML for comparison
	public static String normalizeXml(String xml) {
		// Remove whitespace between tags and normalize line endings
		final var nicer = xml.replaceAll(">\\s+<", ">\n<");
		final var sb = new StringBuilder();
		for (String line : nicer.split("\n")) {
			line = line.trim(); // this also removes any bad line breaks like \r ...
			if (line.isEmpty())
				continue;
			sb.append(line).append("\n");
		}
		return sb.toString();
	}
	
	public static String indentXml(String input) {
		try {
			final var transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
			
			final var result = new StringWriter();
			transformer.transform(
					new StreamSource(new StringReader(input)),
					new StreamResult(result)
			);
			// Literally glued together
			return result.toString()
				 .replaceFirst("\\?>", "?>\n")
				 .replaceAll("\\n\\s*\\n", "\n");
		} catch (TransformerException e) {
			throw new RuntimeException("Failed to indent XML", e);
		}
	}
	
	public static void writeXml(String inp, Path outFile) throws IOException {
		final var result = Util.normalizeXml(inp);
		Files.createDirectories(outFile.getParent());
		
		// Write the cleaned output
		try (var fileWriter = new FileWriter(outFile.toFile(), StandardCharsets.UTF_8)) {
			fileWriter.write(Util.indentXml(result));
		}
	}
	
	
	public static String escapeJson(String s) {
		final var sb = new StringBuilder();
		for (char c : s.toCharArray()) {
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\b' -> sb.append("\\b");
				case '\f' -> sb.append("\\f");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (c < 0x20) {
						sb.append(String.format("\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
				}
			}
		}
		return sb.toString();
	}
	
	/**
	 * Simple HTML escaping
	 */
	public static String escapeHtml(String s) {
		if (s == null)
			return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
	}
	// Need this to escape XML special chars
	public static String escapeXml(String s) {
		// theoretically escapeHtml could work, but I made this just in case
		return s.replace("&", "&amp;")
					   .replace("<", "&lt;")
					   .replace(">", "&gt;")
					   .replace("\"", "&quot;")
					   .replace("'", "&apos;");
	}
	
	public static void copyText(String text) {
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
	}
	
	/**
	 * @return selected path, or null if canceled
	 */
	public static CompletableFuture<String> pickFolderAsync() {
		CompletableFuture<String> future = new CompletableFuture<>();
		SwingUtilities.invokeLater(() -> {
			JFileChooser chooser = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			chooser.setDialogTitle("Select target folder");
			chooser.setAcceptAllFileFilterUsed(false);
			int result = chooser.showOpenDialog(null);
			String selectedPath = result == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile().getAbsolutePath() : null;
			
			future.complete(selectedPath);
		});
		return future;
	}
	
	public static void openGameDirectory(JPanel w, String gameDirPath) {
		if (gameDirPath == null || gameDirPath.isBlank()) {
			JOptionPane.showMessageDialog(w, "Game directory not set. Please configure it in Settings.", "Directory Not Set", JOptionPane.WARNING_MESSAGE);
			return;
		}
		
		final File gameDir = new File(gameDirPath);
		if (! gameDir.exists() || ! gameDir.isDirectory()) {
			JOptionPane.showMessageDialog(w, "Game directory does not exist or is invalid:\n" + gameDirPath, "Invalid Directory", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		try {
			// Cross-platform directory opening
			if (Desktop.isDesktopSupported()) {
				Desktop.getDesktop().open(gameDir);
				return;
			}
			// Fallback for systems without Desktop support
			final Runtime runtime = Runtime.getRuntime();
			
			if (os.contains("win")) {
				runtime.exec(new String[] { "explorer", gameDirPath });
			} else if (os.contains("mac")) {
				runtime.exec(new String[] { "open", gameDirPath });
			} else if (os.contains("nix") || os.contains("nux")) {
				runtime.exec(new String[] { "xdg-open", gameDirPath });
			} else {
				JOptionPane.showMessageDialog(w, "Cannot open directory on this operating system.", "Unsupported OS", JOptionPane.ERROR_MESSAGE);
			}
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(w, "Failed to open game directory:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
			ex.printStackTrace();
		}
	}
	
	
	public static Path getConfigDir() {
		if (os.contains("win")) {
			// Windows: %APPDATA%\ {APP_NAME}
			String appData = System.getenv("APPDATA");
			if (appData == null || appData.isBlank()) {
				appData = System.getProperty("user.home") + "\\AppData\\Roaming";
			}
			return Paths.get(appData, APP_NAME);
		} else if (os.contains("mac")) {
			// macOS: ~/Library/Application Support/ {APP_NAME}
			return Paths.get(System.getProperty("user.home"), "Library", "Application Support", APP_NAME);
		} else {
			// Linux/Unix: ~/.config/ {APP_NAME} (XDG Base Directory Specification)
			String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
			if (xdgConfigHome != null && ! xdgConfigHome.isBlank()) {
				return Paths.get(xdgConfigHome, APP_NAME.toLowerCase());
			}
			// Fallback to ~/.config/ {APP_NAME}
			return Paths.get(System.getProperty("user.home"), ".config", APP_NAME.toLowerCase());
		}
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
		if (! Files.exists(sourceFolder) || ! Files.isDirectory(sourceFolder)) {
			log.warning("Source folder does not exist: " + sourceFolder);
			return false;
		}
		final FileTime ft = FileTime.fromMillis(0);
		try {
			Files.createDirectories(destPakFile.getParent());
			Files.deleteIfExists(destPakFile);
		} catch (final IOException e) {
			log.severe("PAK creation failed: " + e.getMessage());
			return false;
		}
		try (var fos = new FileOutputStream(destPakFile.toFile()); var zout = new ZipOutputStream(fos)) {
			
			// Optionally set the ZipOutputStream comment to empty or fixed
			if (stripMetadata) {
				zout.setComment(""); // Remove any comments
			}
			
			Files.walk(sourceFolder).filter(Files::isRegularFile).filter(path -> fileFilter == null || fileFilter.test(path)).forEach(file -> {
				try {
					// Get the path relative to the source folder
					String relPath = sourceFolder.relativize(file).toString().replace('\\', '/');
					
					// Create ZipEntry with a fixed timestamp to strip metadata
					final var entry = getZipEntry(stripMetadata, relPath, ft);
					
					zout.putNextEntry(entry);
					Files.copy(file, zout);
					zout.closeEntry();
					
					log.fine("Added to PAK: " + relPath);
				} catch (IOException e) {
					log.warning("Cannot add to pak: " + file + " - " + e.getMessage());
				}
			});
		} catch (IOException e) {
			log.severe("PAK creation failed: " + e.getMessage());
			return false;
		}
		
		log.info("PAK created: " + destPakFile);
		return true;
	}
	
	private static ZipEntry getZipEntry(boolean stripMetadata, String relPath, FileTime ft) {
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
		return entry;
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
		return packFolder(sourceFolder, destPakFile, path -> ! path.equals(destPakFile), true);
	}
	
	public static void deleteRecursively(Path path) {
		if (! Files.exists(path))
			return;
		try {
			Files.walk(path).sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		} catch (IOException ex) {
			log.info("Could not delete file/folder " + path + " : " + ex);
		}
	}
}