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
import java.util.Arrays;
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
	public static final String TABLES = "Tables.pak";
	public static final String STORM = "Storm.pak";
	public static final String COMP_FORMAT = ".pak";
	public static final String DATA_FORMAT = ".xml";
	public static final String ICONS = "IPL_GameData.pak";
	
	public static final String LOCALIZATION_DIR = "Localization";
	public static final String LOCALIZATION_EXTRA = "_xml";
	public static final String DATA_DIR = "Data";
	public static final String LIBS_DIR = "Libs";
	public static final String TABLES_DIR = "Tables";
	public static final String MODS_DIR = "Mods";
	
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
		return joinP(root, DATA_DIR);
	}
	
	public static String gameData(String root) {
		return join(root, DATA_DIR);
	}
	
	public static Path gameLocalDir(String root) {
		return joinP(root, LOCALIZATION_DIR);
	}
	
	public static Path modLocalDir(String root, String modId) {
		return joinP(modFolder(root, modId), LOCALIZATION_DIR);
	}
	
	public static Path modStaging(String gameDir, String modId) {
		return Path.of(Util.modData(gameDir, modId), "_stage");
	}
	
	public static Path modStormStaging(String gameDir, String modId) {
		return Path.of(Util.modStaging(gameDir, modId).toString(), DATA_DIR);
	}
	
	public static String locImport(String root, Language lang) {
		return join(root, LOCALIZATION_DIR, lang.getName() + LOCALIZATION_EXTRA + COMP_FORMAT);
	}
	
	public static Path locExport(String root, Language lang, String modId) {
		return joinP(modLocalDir(root, modId), lang.getName() + LOCALIZATION_EXTRA, modXmlForce("text", modId));
	}
	
	public static String modXmlFile(String fileName, String modId) {
		final int delimit = fileName.indexOf("__");
		final String nameFinal;
		if (delimit != - 1)
			nameFinal = fileName.substring(0, delimit);
		else if (fileName.endsWith(DATA_FORMAT))
			nameFinal = fileName.substring(0, fileName.length() - 4);
		else
			nameFinal = fileName;
		return modXmlForce(nameFinal, modId);
	}
	
	private static String modXmlForce(String fileName, String modId) {
		return fileName + "__" + modId + DATA_FORMAT;
	}
	
	public static String modFolder(String root, String modId) {
		return join(root, MODS_DIR, modId);
	}
	
	public static Path modFolder(String root) {
		return joinP(root, MODS_DIR);
	}
	
	public static String modData(String root, String modId) {
		return join(root, MODS_DIR, modId, DATA_DIR);
	}
	
	public static Set<String> allLocPaths(String root) {
		return Arrays.stream(Language.values()).map(l -> locImport(root, l)).collect(Collectors.toSet());
	}
	
	public static String icons(String gameDir) {
		return Util.join(gameData(gameDir), ICONS);
	}
	
	/**
	 * Join path segments using forward slashes (cross-platform safe).
	 */
	public static String join(String... parts) {
		if (parts.length < 1) return "";
		return String.join(File.separator, parts);
	}
	// Windows: "C:\Users\name\file.txt"
	// Linux: "/home/name/file.txt"
	
	public static Path joinP(String... parts) {
		if (parts.length < 1)
			return Path.of("");
		else if (parts.length == 1)
			return Path.of(parts[0]);
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
			transformer.transform(new StreamSource(new StringReader(input)), new StreamResult(result));
			// Literally glued together
			return result.toString().replaceFirst("\\?>", "?>\n").replaceAll("\\n\\s*\\n", "\n");
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
	public static String escHtml(String s) {
		if (s == null)
			return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
	}
	
	// Need this to escape XML special chars
	public static String escapeXml(String s) {
		// theoretically escapeHtml could work, but I made this just in case
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
	}
	
	public static String unescapeXml(String s) {
		if (s == null) {
			return null;
		}
		return s.replace("&nbsp;", " ").replace("&apos;", "'").replace("&quot;", "\"").replace("&gt;", ">").replace("&lt;", "<").replace("&amp;", "&");
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
	
	public static void openDirectory(JPanel w, String dirPath) {
		if (dirPath == null || dirPath.isBlank()) {
			JOptionPane.showMessageDialog(w, "Game directory not set. Please configure it in Settings.", "Directory Not Set", JOptionPane.WARNING_MESSAGE);
			return;
		}
		
		final File gameDir = new File(dirPath);
		if (! gameDir.exists() || ! gameDir.isDirectory()) {
			JOptionPane.showMessageDialog(w, "Game directory does not exist or is invalid:\n" + dirPath, "Invalid Directory", JOptionPane.ERROR_MESSAGE);
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
				runtime.exec(new String[] { "explorer", dirPath });
			} else if (os.contains("mac")) {
				runtime.exec(new String[] { "open", dirPath });
			} else if (os.contains("nix") || os.contains("nux")) {
				runtime.exec(new String[] { "xdg-open", dirPath });
			} else {
				JOptionPane.showMessageDialog(w, "Cannot open directory on this operating system.", "Unsupported OS", JOptionPane.ERROR_MESSAGE);
			}
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(w, "Failed to open game directory:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
			log.warning("IOException: " + ex);
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
	public static boolean packFolder(final Path sourceFolder, final Path destPakFile, final Predicate<Path> fileFilter, final boolean stripMetadata) {
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
		try (var fos = new FileOutputStream(destPakFile.toFile()); var out = new ZipOutputStream(fos); var walk = Files.walk(sourceFolder)) {
			
			// Optionally set the ZipOutputStream comment to empty or fixed
			if (stripMetadata) {
				out.setComment(""); // Remove any comments
			}
			
			walk.filter(Files::isRegularFile).filter(path -> fileFilter == null || fileFilter.test(path)).forEach(file -> {
				try {
					// Get the path relative to the source folder
					final String relPath = sourceFolder.relativize(file).toString().replace('\\', '/');
					
					// Create ZipEntry with a fixed timestamp to strip metadata
					final var entry = getZipEntry(relPath, ft, stripMetadata);
					
					out.putNextEntry(entry);
					Files.copy(file, out);
					out.closeEntry();
					
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
	
	private static ZipEntry getZipEntry(final String relPath, final FileTime ft, final boolean stripMetadata) {
		final var entry = new ZipEntry(relPath);
		
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
		try (var walk = Files.walk(path)) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> {
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