package image;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of a {@link TextureImporter#importTexture} call.
 *
 * <p>Carries:
 * <ul>
 *   <li>The detected source format description</li>
 *   <li>The inferred KCD2 texture role (profile)</li>
 *   <li>Whether the conversion was automatic or needs user confirmation</li>
 *   <li>The converted DDS bytes (ready to write to disk / pass to the game)</li>
 *   <li>Human-readable messages and warnings for the frontend</li>
 * </ul>
 *
 * Inspired by the C# KCDTextureExporter's interactive feedback loop, but
 * designed as a pure data object so any frontend (Swing, REST, CLI) can use it.
 */
@Slf4j
@Getter
@ToString
public class TextureImportResult {
	
	// ── Source info ───────────────────────────────────────────────────────────
	
	/** Informational messages (shown in a log / status panel). */
	private final List<String> messages = new ArrayList<>();
	/** Warnings that the user should be aware of but that didn't block conversion. */
	private final List<String> warnings = new ArrayList<>();
	/** File name of the source file as supplied by the caller. */
	private String sourceFileName = "";
	/** Human-readable description of what was detected in the source file. */
	private String detectedSourceFormat = "Unknown";
	
	// ── Profile info ──────────────────────────────────────────────────────────
	/** Image dimensions. */
	private int sourceWidth;
	private int sourceHeight;
	
	// ── Conversion outcome ────────────────────────────────────────────────────
	/** The KCD2 texture role that was resolved (auto-detected or user-chosen). */
	private FormatProfile profile = FormatProfile.UNKNOWN;
	/** Whether the profile was confidently auto-detected ({@code true})
	 *  or is just a best-guess / default ({@code false}). */
	private boolean profileAutoDetected;
	/** True when the converted bytes are ready and no user action is needed. */
	private boolean conversionSucceeded = false;
	
	// ── Feedback for the UI ───────────────────────────────────────────────────
	/**
	 * Converted DDS bytes in the KCD2-expected format.
	 * {@code null} when {@link #conversionSucceeded} is {@code false}.
	 */
	private byte[] convertedDdsBytes;
	/** The DDS format that was actually produced. */
	private String outputFormatDescription = "";
	/** Actionable guidance shown to the user (e.g. "select a profile manually"). */
	private String actionRequired;
	
	// ── Builder methods (fluent API) ──────────────────────────────────────────
	
	public static TextureImportResult b() {
		return new TextureImportResult();
	}
	
	public TextureImportResult sourceFileName(String sourceFileName) {
		this.sourceFileName = sourceFileName;
		return this;
	}
	
	public TextureImportResult detectedSourceFormat(String detectedSourceFormat) {
		this.detectedSourceFormat = detectedSourceFormat;
		return this;
	}
	
	public TextureImportResult sourceWidth(int sourceWidth) {
		this.sourceWidth = sourceWidth;
		return this;
	}
	
	public TextureImportResult sourceHeight(int sourceHeight) {
		this.sourceHeight = sourceHeight;
		return this;
	}
	
	public TextureImportResult profile(FormatProfile profile) {
		this.profile = profile;
		return this;
	}
	
	public TextureImportResult profileAutoDetected(boolean profileAutoDetected) {
		this.profileAutoDetected = profileAutoDetected;
		return this;
	}
	
	public TextureImportResult conversionSucceeded(boolean conversionSucceeded) {
		this.conversionSucceeded = conversionSucceeded;
		return this;
	}
	
	public TextureImportResult convertedDdsBytes(byte[] convertedDdsBytes) {
		this.convertedDdsBytes = convertedDdsBytes;
		return this;
	}
	
	public TextureImportResult outputFormatDescription(String outputFormatDescription) {
		this.outputFormatDescription = outputFormatDescription;
		return this;
	}
	
	public TextureImportResult actionRequired(String actionRequired) {
		this.actionRequired = actionRequired;
		return this;
	}
	
	// ── Convenience methods ───────────────────────────────────────────────────
	
	public TextureImportResult file(String sourceFileName) {
		return sourceFileName(sourceFileName);
	}
	
	public TextureImportResult dimensions(int width, int height) {
		this.sourceWidth = width;
		this.sourceHeight = height;
		return this;
	}
	
	public TextureImportResult succeeded(byte[] dds, String format) {
		this.conversionSucceeded = true;
		this.convertedDdsBytes = dds;
		this.outputFormatDescription = format;
		return this;
	}
	
	public TextureImportResult message(String message) {
		this.messages.add(message);
		return this;
	}
	
	public TextureImportResult warning(String warning) {
		this.warnings.add(warning);
		return this;
	}
	
	// ── Utility methods ───────────────────────────────────────────────────────
	
	/** Returns a single human-readable summary suitable for a status label. */
	public String summary() {
		StringBuilder sb = new StringBuilder();
		sb.append("Source: ").append(detectedSourceFormat).append(" (").append(sourceWidth).append("×").append(sourceHeight).append(")\n");
		sb.append("Profile: ").append(profile.displayName).append(profileAutoDetected ? " [auto]" : " [manual]").append("\n");
		if (conversionSucceeded) {
			sb.append("Output: ").append(outputFormatDescription).append(" — ").append(convertedDdsBytes != null ? convertedDdsBytes.length + " bytes" : "n/a").append("\n");
		}
		if (! warnings.isEmpty()) {
			sb.append("Warnings:\n");
			warnings.forEach(w -> sb.append("  ⚠ ").append(w).append("\n"));
		}
		if (actionRequired != null) {
			sb.append("Action needed: ").append(actionRequired).append("\n");
		}
		return sb.toString().trim();
	}
}