package image;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * CLI entry point for the KCD Texture Exporter.
 *
 * Mirrors the argument-handling in {@code App.OnStartup} from the C# source:
 * When called without arguments the process prints usage and exits; your
 * frontend should wire up to {@link ImageConverter} directly.
 */
@lombok.extern.slf4j.Slf4j
public class Main {
	
	static void main(String[] args) throws Exception {
		List<String> argList = Arrays.asList(args);
		
		if (! argList.contains("--input") || ! argList.contains("--output")) {
			printUsage();
			System.exit(1);
		}
		
		String inputStr = getArg(argList, "--input");
		String outputStr = getArg(argList, "--output");
		
		boolean saveRaw = argList.contains("--saveRaw");
		boolean separateGloss = argList.contains("--separateGloss");
		boolean deleteSource = argList.contains("--deleteSource");
		boolean recursive = argList.contains("--recursive");
		
		boolean isOutputFolder = ! outputStr.toLowerCase().endsWith(".tif");
		
		Path inputPath = Path.of(inputStr);
		Path outputPath = Path.of(outputStr);
		
		ConversionOptions opts = new ConversionOptions().saveRawDDS(saveRaw).separateGlossMap(separateGloss).deleteSourceFiles(deleteSource).outputPath(outputStr).isOutputFolder(isOutputFolder);
		
		if (Files.isDirectory(inputPath)) {
			log.info("Batch converting " + inputPath + " isRecursive" + recursive);
			var futures = ImageConverter.batchProcess(inputPath, outputPath, opts, recursive);
			ImageConverter.awaitAll(futures);
			log.info("Done.");
		} else if (Files.isRegularFile(inputPath) && inputPath.toString().toLowerCase().endsWith(".dds")) {
			log.info("Converting single file: " + inputPath);
			ImageConverter.convertImage(inputPath, opts);
			log.info("Done.");
		} else {
			System.err.println("ERROR: Input path is not a .dds file or a directory: " + inputPath);
			System.exit(2);
		}
	}
	
	private static String getArg(List<String> args, String key) {
		int idx = args.indexOf(key);
		if (idx >= 0 && idx < args.size() - 1)
			return args.get(idx + 1);
		return "";
	}
	
	private static void printUsage() {
		System.out.println("""
				KCD Texture Exporter (Java)
				Usage:
				  java -jar kcd-texture-exporter.jar \\
				      --input  <file.dds | folder>  \\
				      --output <file.tif | folder>  \\
				      [--saveRaw]       Save assembled .dds alongside TIFF
				      [--separateGloss] Write gloss/alpha as _alpha.tif
				      [--deleteSource]  Delete source DDS files after conversion
				      [--recursive]     Search sub-folders (folder input only)
				""");
	}
}
