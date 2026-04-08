package image;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * BC5_UNORM encoder (two-channel, RG only).
 *
 * <p>BC5 stores two independent BC4 blocks per 4×4 tile — one for R, one for G.
 * The B and A channels are discarded; the engine reconstructs Z at runtime.
 *
 * <p>This class is package-private. All access goes through
 * {@link DDSUtil#encodeBC5(byte[], int, int)}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
class Bc5Util {
	
	/**
	 * Compress RGBA8 pixels to raw BC5_UNORM block data (no DDS header).
	 *
	 * @param rgba source pixels, 4 bytes per pixel (R, G, B, A)
	 * @param w    image width
	 * @param h    image height
	 * @return raw BC5 block bytes, length = {@code blocksWide(w) * blocksHigh(h) * 16}
	 */
	static byte[] compress(byte[] rgba, int w, int h) {
		int bw = DDSUtil.blocksWide(w), bh = DDSUtil.blocksHigh(h);
		byte[] out = new byte[bw * bh * 16]; // 2 × 8-byte BC4 blocks per tile
		
		for (int by = 0; by < bh; by++) {
			for (int bx = 0; bx < bw; bx++) {
				int[] rVals = new int[16], gVals = new int[16];
				
				// Extract the R and G channels from the 4×4 block
				for (int py = 0; py < 4; py++) {
					for (int px = 0; px < 4; px++) {
						int x = Math.min(bx * 4 + px, w - 1);
						int y = Math.min(by * 4 + py, h - 1);
						int p = (y * w + x) * 4;
						rVals[py * 4 + px] = rgba[p] & 0xFF;
						gVals[py * 4 + px] = rgba[p + 1] & 0xFF;
					}
				}
				
				int blockOff = (by * bw + bx) * 16;
				encodeBC4Block(rVals, out, blockOff);      // R → first 8 bytes
				encodeBC4Block(gVals, out, blockOff + 8);  // G → second 8 bytes
			}
		}
		return out;
	}
	
	/**
	 * Encode 16 [0..255] values into one 8-byte BC4_UNORM block.
	 * Uses the 8-interpolant mode (e0 &gt; e1) for maximum precision.
	 */
	private static void encodeBC4Block(int[] vals, byte[] out, int off) {
		// Find the min/max to use as endpoints
		int mn = vals[0], mx = vals[0];
		for (int v : vals) {
			if (v < mn)
				mn = v;
			if (v > mx)
				mx = v;
		}
		
		// e0 > e1 activates the 8-interpolant mode
		out[off] = (byte) mx; // e0
		out[off + 1] = (byte) mn; // e1
		
		// Build the 3-bit lookup table for 8-interpolant mode
		int[] table = { mx, mn, (6 * mx + mn) / 7, (5 * mx + 2 * mn) / 7, (4 * mx + 3 * mn) / 7, (3 * mx + 4 * mn) / 7, (2 * mx + 5 * mn) / 7, (mx + 6 * mn) / 7 };
		
		// Assign the closest index to each of the 16 pixels
		long bits = 0;
		for (int i = 0; i < 16; i++)
			bits |= (long) DDSUtil.closestIndex(vals[i], table) << (i * 3);
		
		// Write the 6 index bytes
		for (int i = 0; i < 6; i++)
			out[off + 2 + i] = (byte) ((bits >> (8 * i)) & 0xFF);
	}
}