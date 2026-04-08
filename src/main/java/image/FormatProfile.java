package image;

import lombok.extern.slf4j.Slf4j;

/**
 * KCD2 texture format profiles.
 *
 * Each enum value represents a logical texture "slot" in Kingdom Come: Deliverance 2
 * and carries:
 *  - the DDS target format the game expects,
 *  - whether alpha is meaningful in that slot,
 *  - a human-readable description used in UI/log output.
 *
 * Detection rules (applied by {@link TextureImporter}) are based on the
 * filename suffix conventions used by Warhorse Studios:
 *
 *  _dif / _d      → DIFFUSE        (color map, sRGB, BC1 no-alpha or BC3 with alpha)
 *  _nrm / _n      → NORMAL         (tangent-space normal, BC5)
 *  _spec / _s     → SPECULAR       (specular / roughness, BC5 or BC1)
 *  _gloss / _g    → GLOSS          (gloss stored in alpha; usually companion to _nrm)
 *  _ao            → AMBIENT_OCC    (grayscale AO, BC4)
 *  _emissive / _e → EMISSIVE       (additive color, BC1)
 *  _mask / _msk   → MASK           (binary / multichannel mask, BC3)
 *  _id            → ID_MAP         (uncompressed RGBA for exact color IDs)
 *  anything else  → UNKNOWN        (user picks manually)
 */
@Slf4j
public enum FormatProfile {
	
	DIFFUSE("Diffuse / Albedo (colour map)", TargetFormat.BC3, true, "sRGB colour texture. Use BC3 (DXT5) for transparency, BC1 (DXT1) for opaque."),         // BC1 when opaque, BC3 when alpha present
	NORMAL("Normal Map", TargetFormat.BC5, false, "Tangent-space XY normal map. KCD2 expects BC5_UNORM (two-channel RG)."), SPECULAR("Specular / Roughness", TargetFormat.BC5, false, "Specular / roughness data packed into RG. BC5_UNORM gives best precision."), GLOSS("Gloss Map", TargetFormat.BC3, true, "Gloss channel – often stored in the alpha of a normal map. BC3 or separate BC4."), AMBIENT_OCC("Ambient Occlusion", TargetFormat.BC1, false, "Grayscale AO map. BC1 is sufficient; no alpha needed."), EMISSIVE("Emissive", TargetFormat.BC1, false, "Additive emissive colour. BC1 is standard."), MASK("Mask", TargetFormat.BC3, true, "Multi-channel mask (e.g. damage, wetness). BC3 preserves the alpha channel."), ID_MAP("ID Map", TargetFormat.UNCOMPRESSED_RGBA, true, "Colour-ID map. Must stay uncompressed (RGBA8) so IDs are exact."), UNKNOWN("Unknown / General", TargetFormat.BC7, true, "Format unknown. Defaulting to BC7 (best quality, supports alpha).");
	
	// -------------------------------------------------------------------------
	
	public final String displayName;
	
	// -------------------------------------------------------------------------
	public final TargetFormat targetFormat;
	public final boolean usesAlpha;
	public final String guidance;
	
	FormatProfile(String displayName, TargetFormat targetFormat, boolean usesAlpha, String guidance) {
		this.displayName = displayName;
		this.targetFormat = targetFormat;
		this.usesAlpha = usesAlpha;
		this.guidance = guidance;
	}
	
	/**
	 * Infer the KCD2 texture role from a filename.
	 * Returns {@link #UNKNOWN} when no suffix matches.
	 *
	 * @param filename the base filename (with or without extension)
	 */
	public static FormatProfile detect(String filename) {
		if (filename == null)
			return UNKNOWN;
		String lower = filename.toLowerCase();
		
		// Strip extension if present
		int dot = lower.lastIndexOf('.');
		if (dot > 0)
			lower = lower.substring(0, dot);
		
		if (lower.endsWith("_dif") || lower.endsWith("_d") || lower.endsWith("_diffuse") || lower.endsWith("_albedo") || lower.endsWith("_color") || lower.endsWith("_colour"))
			return DIFFUSE;
		
		if (lower.endsWith("_nrm") || lower.endsWith("_n") || lower.endsWith("_normal") || lower.endsWith("_nor"))
			return NORMAL;
		
		if (lower.endsWith("_spec") || lower.endsWith("_s") || lower.endsWith("_specular") || lower.endsWith("_rough") || lower.endsWith("_roughness"))
			return SPECULAR;
		
		if (lower.endsWith("_gloss") || lower.endsWith("_g"))
			return GLOSS;
		
		if (lower.endsWith("_ao") || lower.endsWith("_ambientocclusion") || lower.endsWith("_occlusion"))
			return AMBIENT_OCC;
		
		if (lower.endsWith("_emissive") || lower.endsWith("_e") || lower.endsWith("_emit"))
			return EMISSIVE;
		
		if (lower.endsWith("_mask") || lower.endsWith("_msk") || lower.endsWith("_m"))
			return MASK;
		
		if (lower.endsWith("_id"))
			return ID_MAP;
		
		return UNKNOWN;
	}
	
	// -------------------------------------------------------------------------
	// Filename-based auto-detection
	// -------------------------------------------------------------------------
	
	/** Which DDS compression format to produce. */
	public enum TargetFormat {
		BC1("BC1 / DXT1 – 4 bpp, no alpha"), BC3("BC3 / DXT5 – 8 bpp, full alpha"), BC5("BC5 – 8 bpp, two-channel RG (normal/spec)"), BC7("BC7 – 8 bpp, high-quality RGBA"), UNCOMPRESSED_RGBA("Uncompressed RGBA8 – 32 bpp, lossless");
		
		public final String description;
		
		TargetFormat(String description) {
			this.description = description;
		}
	}
}
