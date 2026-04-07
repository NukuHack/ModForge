package image;

import java.util.ArrayList;
import java.util.Collections;
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
@lombok.extern.slf4j.Slf4j
public class TextureImportResult {
	
	// ── Source info ───────────────────────────────────────────────────────────
	
	/** File name of the source file as supplied by the caller. */
	public final String sourceFileName;
	
	/** Human-readable description of what was detected in the source file. */
	public final String detectedSourceFormat;
	
	/** Image dimensions. */
	public final int sourceWidth;
	public final int sourceHeight;
	
	// ── Profile info ──────────────────────────────────────────────────────────
	
	/** The KCD2 texture role that was resolved (auto-detected or user-chosen). */
	public final FormatProfile profile;
	
	/** Whether the profile was confidently auto-detected ({@code true})
	 *  or is just a best-guess / default ({@code false}). */
	public final boolean profileAutoDetected;
	
	// ── Conversion outcome ────────────────────────────────────────────────────
	
	/** True when the converted bytes are ready and no user action is needed. */
	public final boolean conversionSucceeded;
	
	/**
	 * Converted DDS bytes in the KCD2-expected format.
	 * {@code null} when {@link #conversionSucceeded} is {@code false}.
	 */
	public final byte[] convertedDdsBytes;
	
	/** The DDS format that was actually produced. */
	public final String outputFormatDescription;
	
	// ── Feedback for the UI ───────────────────────────────────────────────────
	
	/** Informational messages (shown in a log / status panel). */
	public final List<String> messages;
	
	/** Warnings that the user should be aware of but that didn't block conversion. */
	public final List<String> warnings;
	
	/** Actionable guidance shown to the user (e.g. "select a profile manually"). */
	public final String actionRequired;
	
	// ── Constructor (use Builder) ─────────────────────────────────────────────
	
	private TextureImportResult(Builder b) {
		this.sourceFileName = b.sourceFileName;
		this.detectedSourceFormat = b.detectedSourceFormat;
		this.sourceWidth = b.sourceWidth;
		this.sourceHeight = b.sourceHeight;
		this.profile = b.profile;
		this.profileAutoDetected = b.profileAutoDetected;
		this.conversionSucceeded = b.conversionSucceeded;
		this.convertedDdsBytes = b.convertedDdsBytes;
		this.outputFormatDescription = b.outputFormatDescription;
		this.messages = Collections.unmodifiableList(b.messages);
		this.warnings = Collections.unmodifiableList(b.warnings);
		this.actionRequired = b.actionRequired;
	}
	
	// ── Convenience ──────────────────────────────────────────────────────────
	
	public static Builder builder(String sourceFileName) {
		return new Builder(sourceFileName);
	}
	
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
	
	// ── Builder ───────────────────────────────────────────────────────────────
	
	@Override
	public String toString() {
		return summary();
	}
	
	public static class Builder {
		String sourceFileName;
		String detectedSourceFormat = "Unknown";
		int sourceWidth, sourceHeight;
		FormatProfile profile = FormatProfile.UNKNOWN;
		boolean profileAutoDetected = false;
		boolean conversionSucceeded = false;
		byte[] convertedDdsBytes;
		String outputFormatDescription = "";
		List<String> messages = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		String actionRequired;
		
		private Builder(String sourceFileName) {
			this.sourceFileName = sourceFileName;
		}
		
		public Builder detectedSourceFormat(String v) {
			detectedSourceFormat = v;
			return this;
		}
		
		public Builder dimensions(int w, int h) {
			sourceWidth = w;
			sourceHeight = h;
			return this;
		}
		
		public Builder profile(FormatProfile v) {
			profile = v;
			return this;
		}
		
		public Builder profileAutoDetected(boolean v) {
			profileAutoDetected = v;
			return this;
		}
		
		public Builder succeeded(byte[] dds, String fmt) {
			conversionSucceeded = true;
			convertedDdsBytes = dds;
			outputFormatDescription = fmt;
			return this;
		}
		
		public Builder message(String m) {
			messages.add(m);
			return this;
		}
		
		public Builder warning(String w) {
			warnings.add(w);
			return this;
		}
		
		public Builder actionRequired(String v) {
			actionRequired = v;
			return this;
		}
		
		public TextureImportResult build() {
			return new TextureImportResult(this);
		}
	}
}
