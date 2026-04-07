package image;

/**
 * BC7 block decoder (all 8 modes) and Mode-6 encoder.
 * Partition tables for multi-subset modes return zeros (single-subset fallback).
 */
@lombok.extern.slf4j.Slf4j
class BC7Util {
	
	static void decompress(ImgData data, byte[] out) {
		byte[] src = data.data;
		int w = data.w, h = data.h, bw = (w + 3) / 4;
		
		for (int by = 0; by < (h + 3) / 4; by++) {
			for (int bx = 0; bx < bw; bx++) {
				int off = (by * bw + bx) * 16;
				if (off + 16 > src.length)
					break;
				
				int[] decoded = decodeBlock(src, off);
				
				for (int py = 0; py < 4; py++) {
					for (int px = 0; px < 4; px++) {
						int x = bx * 4 + px, y = by * 4 + py;
						if (x < w && y < h) {
							int oi = (y * w + x) * 4, c = decoded[py * 4 + px];
							out[oi] = (byte) ((c >> 16) & 0xFF);
							out[oi + 1] = (byte) ((c >> 8) & 0xFF);
							out[oi + 2] = (byte) (c & 0xFF);
							out[oi + 3] = (byte) ((c >> 24) & 0xFF);
						}
					}
				}
			}
		}
	}
	
	private static int[] decodeBlock(byte[] src, int off) {
		DDSUtil.BitReader br = new DDSUtil.BitReader(java.util.Arrays.copyOfRange(src, off, off + 16));
		
		int mode = - 1;
		for (int i = 0; i < 8; i++) {
			if (br.read(1) == 1) {
				mode = i;
				break;
			}
		}
		if (mode < 0) {
			int[] e = new int[16];
			java.util.Arrays.fill(e, 0xFF000000);
			return e;
		}
		
		final int[] nSubsets = { 3, 2, 3, 2, 1, 1, 1, 2 };
		final int[] partBits = { 4, 6, 6, 6, 0, 0, 0, 6 };
		final int[] cBits = { 4, 6, 5, 7, 5, 7, 7, 5 };
		final int[] aBits = { 0, 0, 0, 0, 6, 8, 7, 5 };
		final int[] idxBits = { 3, 3, 2, 2, 2, 2, 4, 2 };
		
		int ns = nSubsets[mode];
		int pi = br.read(partBits[mode]);
		int rot = (mode == 4 || mode == 5) ? br.read(2) : 0;
		int sel = (mode == 4) ? br.read(1) : 0;
		
		int cb = cBits[mode], ab = aBits[mode];
		int icb = (mode == 4) ? (sel == 0 ? 2 : 3) : idxBits[mode];
		int iab = (mode == 4) ? (sel == 0 ? 3 : 2) : idxBits[mode];
		
		int[][] r = new int[ns][2], g = new int[ns][2], b = new int[ns][2], a = new int[ns][2];
		for (int s = 0; s < ns; s++) {
			r[s][0] = br.read(cb);
			r[s][1] = br.read(cb);
		}
		for (int s = 0; s < ns; s++) {
			g[s][0] = br.read(cb);
			g[s][1] = br.read(cb);
		}
		for (int s = 0; s < ns; s++) {
			b[s][0] = br.read(cb);
			b[s][1] = br.read(cb);
		}
		if (ab > 0)
			for (int s = 0; s < ns; s++) {
				a[s][0] = br.read(ab);
				a[s][1] = br.read(ab);
			}
		
		int[][] pb = new int[ns][2];
		if (mode == 0 || mode == 3 || mode == 6 || mode == 7) {
			for (int s = 0; s < ns; s++) {
				pb[s][0] = br.read(1);
				pb[s][1] = br.read(1);
			}
		} else if (mode == 1) {
			for (int s = 0; s < ns; s++) {
				int p = br.read(1);
				pb[s][0] = pb[s][1] = p;
			}
		}
		
		for (int s = 0; s < ns; s++) {
			r[s][0] = expand(r[s][0], cb, pb[s][0]);
			r[s][1] = expand(r[s][1], cb, pb[s][1]);
			g[s][0] = expand(g[s][0], cb, pb[s][0]);
			g[s][1] = expand(g[s][1], cb, pb[s][1]);
			b[s][0] = expand(b[s][0], cb, pb[s][0]);
			b[s][1] = expand(b[s][1], cb, pb[s][1]);
			if (ab > 0) {
				a[s][0] = expand(a[s][0], ab, pb[s][0]);
				a[s][1] = expand(a[s][1], ab, pb[s][1]);
			} else {
				a[s][0] = a[s][1] = 255;
			}
		}
		
		int[] part = partition(ns, pi);
		int[] anchors = anchors(part, ns);
		int[] ci = new int[16], ai = new int[16];
		
		for (int i = 0; i < 16; i++)
			ci[i] = br.read(icb - (i == anchors[part[i]] ? 1 : 0));
		if (mode == 4 || mode == 5)
			for (int i = 0; i < 16; i++)
				ai[i] = br.read(iab - (i == anchors[0] ? 1 : 0));
		else
			ai = ci;
		
		int[] res = new int[16];
		for (int i = 0; i < 16; i++) {
			int s = part[i];
			int fr = interp(r[s][0], r[s][1], ci[i], icb);
			int fg = interp(g[s][0], g[s][1], ci[i], icb);
			int fb = interp(b[s][0], b[s][1], ci[i], icb);
			int fa = interp(a[s][0], a[s][1], ai[i], iab);
			if (rot == 1) {
				int t = fa;
				fa = fr;
				fr = t;
			}
			if (rot == 2) {
				int t = fa;
				fa = fg;
				fg = t;
			}
			if (rot == 3) {
				int t = fa;
				fa = fb;
				fb = t;
			}
			res[i] = (fa << 24) | (fr << 16) | (fg << 8) | fb;
		}
		return res;
	}
	
	/** Expand a value of {@code bits} (+ optional P-bit) to 8 bits. */
	private static int expand(int v, int bits, int pBit) {
		if (bits == 0)
			return 0;
		int tv = v, tb = bits;
		if (pBit >= 0 && bits < 8) {
			tv = (tv << 1) | pBit;
			tb++;
		}
		return switch (tb) {
			case 4 -> (tv << 4) | tv;
			case 5 -> (tv << 3) | (tv >> 2);
			case 6 -> (tv << 2) | (tv >> 4);
			case 7 -> (tv << 1) | (tv >> 6);
			default -> tv;
		};
	}
	
	private static int interp(int e0, int e1, int idx, int bits) {
		final int[] W2 = { 0, 21, 43, 64 };
		final int[] W3 = { 0, 9, 18, 27, 37, 46, 55, 64 };
		final int[] W4 = { 0, 4, 9, 13, 17, 21, 26, 30, 34, 38, 43, 47, 51, 55, 60, 64 };
		int w = (bits == 2) ? W2[idx] : (bits == 3) ? W3[idx] : W4[idx];
		return ((64 - w) * e0 + w * e1 + 32) >> 6;
	}
	
	private static int[] anchors(int[] part, int ns) {
		int[] anc = new int[ns]; // anc[0] = 0 always
		if (ns > 1)
			for (int i = 0; i < 16; i++)
				if (part[i] == 1) {
					anc[1] = i;
					break;
				}
		if (ns > 2)
			for (int i = 0; i < 16; i++)
				if (part[i] == 2) {
					anc[2] = i;
					break;
				}
		return anc;
	}
	
	// Multi-subset partition tables are not implemented; single-subset (all 0) is returned.
	private static int[] partition(int ns, int idx) {
		return new int[16];
	}
	
	// ── BC7 Mode-6 encoder ──────────────────────────────────────────────────
	
	static byte[] compress(byte[] argb, int w, int h) {
		int bw = (w + 3) / 4, bh = (h + 3) / 4;
		byte[] out = new byte[bw * bh * 16];
		int[] block = new int[16];
		
		for (int by = 0; by < bh; by++) {
			for (int bx = 0; bx < bw; bx++) {
				for (int py = 0, i = 0; py < 4; py++) {
					for (int px = 0; px < 4; px++, i++) {
						int x = bx * 4 + px, y = by * 4 + py;
						if (x < w && y < h) {
							int p = (y * w + x) * 4;
							block[i] = ((argb[p + 3] & 0xFF) << 24) | ((argb[p] & 0xFF) << 16) | ((argb[p + 1] & 0xFF) << 8) | (argb[p + 2] & 0xFF);
						} else
							block[i] = 0;
					}
				}
				encodeMode6(block, out, (by * bw + bx) * 16);
			}
		}
		return out;
	}
	
	private static void encodeMode6(int[] block, byte[] out, int off) {
		// Find the two most-distant colors as endpoints
		int minC = block[0], maxC = block[0], maxDist = - 1;
		for (int i = 0; i < 16; i++) {
			for (int j = i + 1; j < 16; j++) {
				int d = colorDist4(block[i], block[j]);
				if (d > maxDist) {
					maxDist = d;
					minC = block[i];
					maxC = block[j];
				}
			}
		}
		
		// Mode 6: 7-bit endpoints (drop LSB), P-bit = 0
		int e0r = ((minC >> 16) & 0xFF) >> 1, e0g = ((minC >> 8) & 0xFF) >> 1, e0b = (minC & 0xFF) >> 1, e0a = ((minC >> 24) & 0xFF) >> 1;
		int e1r = ((maxC >> 16) & 0xFF) >> 1, e1g = ((maxC >> 8) & 0xFF) >> 1, e1b = (maxC & 0xFF) >> 1, e1a = ((maxC >> 24) & 0xFF) >> 1;
		
		int e0r8 = e0r << 1, e0g8 = e0g << 1, e0b8 = e0b << 1, e0a8 = e0a << 1;
		int e1r8 = e1r << 1, e1g8 = e1g << 1, e1b8 = e1b << 1, e1a8 = e1a << 1;
		
		final int[] W4 = { 0, 4, 9, 13, 17, 21, 26, 30, 34, 38, 43, 47, 51, 55, 60, 64 };
		
		// Anchor pixel must have index < 8; swap if needed
		if (closestIdx4(block[0], e0r8, e0g8, e0b8, e0a8, e1r8, e1g8, e1b8, e1a8, W4) >= 8) {
			int t;
			for (int i = 0; i++ < 2; ) {
				t = e0r;
				e0r = e1r;
				e1r = t;
				t = e0g;
				e0g = e1g;
				e1g = t;
				t = e0b;
				e0b = e1b;
				e1b = t;
				t = e0a;
				e0a = e1a;
				e1a = t;
			}
		}
		
		int[] idx = new int[16];
		for (int i = 0; i < 16; i++)
			idx[i] = closestIdx4(block[i], e0r8, e0g8, e0b8, e0a8, e1r8, e1g8, e1b8, e1a8, W4);
		
		DDSUtil.BitWriter bw = new DDSUtil.BitWriter(out, off);
		bw.write(0x40, 7); // mode 6 marker (bit 6 set)
		bw.write(e0r, 7);
		bw.write(e1r, 7);
		bw.write(e0g, 7);
		bw.write(e1g, 7);
		bw.write(e0b, 7);
		bw.write(e1b, 7);
		bw.write(e0a, 7);
		bw.write(e1a, 7);
		bw.write(0, 1);
		bw.write(0, 1); // P-bits
		bw.write(idx[0], 3); // anchor index (MSB implicit)
		for (int i = 1; i < 16; i++)
			bw.write(idx[i], 4);
	}
	
	private static int closestIdx4(int color, int r0, int g0, int b0, int a0, int r1, int g1, int b1, int a1, int[] W) {
		int ca = (color >> 24) & 0xFF, cr = (color >> 16) & 0xFF, cg = (color >> 8) & 0xFF, cb = color & 0xFF;
		int best = 0, bestD = Integer.MAX_VALUE;
		for (int i = 0; i < 16; i++) {
			int w = W[i], iw = 64 - w;
			int da = ca - ((iw * a0 + w * a1 + 32) >> 6);
			int dr = cr - ((iw * r0 + w * r1 + 32) >> 6);
			int dg = cg - ((iw * g0 + w * g1 + 32) >> 6);
			int db2 = cb - ((iw * b0 + w * b1 + 32) >> 6);
			int d = dr * dr + dg * dg + db2 * db2 + da * da;
			if (d < bestD) {
				bestD = d;
				best = i;
			}
		}
		return best;
	}
	
	private static int colorDist4(int a, int b) {
		int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF), dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
		int db = (a & 0xFF) - (b & 0xFF), da = ((a >> 24) & 0xFF) - ((b >> 24) & 0xFF);
		return dr * dr + dg * dg + db * db + da * da;
	}
}
