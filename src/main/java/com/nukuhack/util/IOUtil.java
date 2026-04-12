package com.nukuhack.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Slf4j
@UtilityClass
public final class IOUtil {
	
	/** Operating system name (lowercase) for cross-platform detection */
	public final String os = System.getProperty("os.name").toLowerCase();
	
	public void openDirectory(String dirPath) throws IllegalArgumentException, IOException {
		if (dirPath == null || dirPath.isBlank())
			throw new IllegalArgumentException();
		
		final File gameDir = new File(dirPath);
		if (! gameDir.exists() || ! gameDir.isDirectory())
			throw new FileNotFoundException();
		
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
		}
	}
	
	/**
	 * Determines if a file is ZIP-like (ZIP or PAK) by checking extension or magic bytes.
	 *
	 * @param path Path to the file
	 * @return true if file has .zip/.pak extension or starts with ZIP magic bytes (PK\x03\x04)
	 */
	public boolean isZipLike(Path path) {
		final String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
		if (lower.endsWith(".zip") || lower.endsWith(".pak"))
			return true;
		try (var in = Files.newInputStream(path)) {
			final byte[] magic = in.readNBytes(4);
			// ZIP local-file header signature: 0x50 0x4B 0x03 0x04
			return magic.length >= 4 && magic[0] == 0x50 && magic[1] == 0x4B && magic[2] == 0x03 && magic[3] == 0x04;
		} catch (IOException ex) {
			return false;
		}
	}
	
	
	public boolean unpackArchive(final Path sourcePakFile, final Path destFolder, final Predicate<Path> fileFilter, final boolean overwrite) {
		if (! Files.exists(sourcePakFile) || ! Files.isRegularFile(sourcePakFile)) {
			log.warn("Source archive does not exist: {}", sourcePakFile);
			return false;
		}
		if (isZipLike(sourcePakFile)) {
			log.warn("Source is not an archive: {}", sourcePakFile);
			return false;
		}
		final var absoluteSource = sourcePakFile.toAbsolutePath().normalize();
		final var absoluteDest = destFolder.toAbsolutePath().normalize();
		
		try {
			Files.createDirectories(absoluteDest);
		} catch (final IOException e) {
			log.error("Archive extraction failed – cannot create destination folder", e);
			return false;
		}
		
		try (var zis = new ZipInputStream(new FileInputStream(absoluteSource.toFile()))) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				if (entry.isDirectory()) {
					continue;
				}
				
				final var entryPath = absoluteDest.resolve(entry.getName()).normalize();
				
				// Security check - prevent zip slip vulnerability
				if (! entryPath.startsWith(absoluteDest)) {
					log.warn("Skipping entry outside target directory: {}", entry.getName());
					continue;
				}
				
				// Apply filter if provided
				if (fileFilter != null && ! fileFilter.test(entryPath)) {
					continue;
				}
				
				// Skip if exists and overwrite is false
				if (! overwrite && Files.exists(entryPath)) {
					continue;
				}
				
				try {
					Files.createDirectories(entryPath.getParent());
					Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
					
					// Restore last modified time if available
					if (entry.getTime() > 0) {
						Files.setLastModifiedTime(entryPath, FileTime.fromMillis(entry.getTime()));
					}
					
					zis.closeEntry();
				} catch (final IOException e) {
					log.warn("Cannot extract entry: {} – {}", entry.getName(), e.getMessage());
				}
			}
			
			return true;
			
		} catch (final IOException e) {
			log.error("Archive extraction failed", e);
			return false;
		}
	}
	
	
	public boolean packFolder(final Path sourceFolder, final Path destPakFile, final Predicate<Path> fileFilter, final boolean stripMetadata) {
		if (! Files.exists(sourceFolder) || ! Files.isDirectory(sourceFolder)) {
			log.warn("Source folder does not exist: {}", sourceFolder);
			return false;
		}
		
		final var absoluteSource = sourceFolder.toAbsolutePath().normalize();
		final var absoluteDest = destPakFile.toAbsolutePath().normalize();
		
		try {
			Files.createDirectories(absoluteDest.getParent());
			Files.deleteIfExists(absoluteDest);
		} catch (final IOException e) {
			log.error("PAK creation failed – cannot prepare output", e);
			return false;
		}
		
		try (var fos = new FileOutputStream(absoluteDest.toFile()); var zos = new ZipOutputStream(fos); var walk = Files.walk(absoluteSource)) {
			
			zos.setLevel(9);
			if (stripMetadata)
				zos.setComment("");
			
			walk.filter(Files::isRegularFile).filter(p -> ! p.toAbsolutePath().normalize().equals(absoluteDest))   // never include self
					.filter(p -> fileFilter == null || fileFilter.test(p)).forEach(file -> {
						try {
							var entryName = absoluteSource.relativize(file).toString().replace('\\', '/');
							var entry = new ZipEntry(entryName);
							
							if (stripMetadata) {
								entry.setTime(0L);
								// *** KEY FIX: wipe the extra-field that Java adds automatically ***
								entry.setExtra(new byte[0]);
							} else {
								entry.setTime(Files.getLastModifiedTime(file).toMillis());
							}
							
							zos.putNextEntry(entry);
							Files.copy(file, zos);
							zos.closeEntry();
							
						} catch (final IOException e) {
							log.warn("Cannot add to pak: {} – {}", file, e.getMessage());
						}
					});
			
			return true;
			
		} catch (final IOException e) {
			log.error("PAK creation failed", e);
			return false;
		}
	}
	
	/**
	 * Recursively deletes a file or directory and all its contents.
	 *
	 * @param path Path to delete (file or directory)
	 */
	public void deleteRecursively(Path path) {
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
			log.info("Could not delete file/folder {}", path, ex);
		}
	}
}
