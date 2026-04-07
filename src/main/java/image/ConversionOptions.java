package image;

/**
 * All parameters that control a single conversion operation.
 * Mirrors the individual boolean/string parameters threaded through
 * the C# ConvertImage() / BatchProcessFiles() call chains.
 */
public class ConversionOptions {
	
	/** Save the assembled raw .dds file alongside the output TIFF. */
	public boolean saveRawDDS = false;
	
	/** Write the gloss/alpha channel to a separate _alpha.tif instead of merging it. */
	public boolean separateGlossMap = false;
	
	/**
	 * Output path. Interpreted as a directory when {@link #isOutputFolder} is
	 * true, as an explicit file path otherwise.
	 */
	public String outputPath = "";
	
	/** Delete source (.dds + mip companion) files after successful conversion. */
	public boolean deleteSourceFiles = false;
	
	/**
	 * Whether {@link #outputPath} names a folder (true) or a specific file (false).
	 * For batch / recursive operations this is always true.
	 */
	public boolean isOutputFolder = true;
	
	// -------------------------------------------------------------------------
	// Fluent builders
	// -------------------------------------------------------------------------
	
	public ConversionOptions saveRawDDS(boolean v) {
		this.saveRawDDS = v;
		return this;
	}
	
	public ConversionOptions separateGlossMap(boolean v) {
		this.separateGlossMap = v;
		return this;
	}
	
	public ConversionOptions outputPath(String v) {
		this.outputPath = v;
		return this;
	}
	
	public ConversionOptions deleteSourceFiles(boolean v) {
		this.deleteSourceFiles = v;
		return this;
	}
	
	public ConversionOptions isOutputFolder(boolean v) {
		this.isOutputFolder = v;
		return this;
	}
}