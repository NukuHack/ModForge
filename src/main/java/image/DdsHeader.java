package image;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * DDS_HEADER — the main 124-byte DDS header (after the 4-byte magic).
 */
public class DdsHeader {
	
	// dwFlags bits
	public static final int DDSD_CAPS = 0x1;
	public static final int DDSD_HEIGHT = 0x2;
	public static final int DDSD_WIDTH = 0x4;
	public static final int DDSD_PITCH = 0x8;
	public static final int DDSD_PIXELFORMAT = 0x1000;
	public static final int DDSD_MIPMAPCOUNT = 0x20000;
	public static final int DDSD_LINEARSIZE = 0x80000;
	public static final int DDSD_DEPTH = 0x800000;
	
	// dwCaps bits
	public static final int DDSCAPS_COMPLEX = 0x8;
	public static final int DDSCAPS_MIPMAP = 0x400000;
	public static final int DDSCAPS_TEXTURE = 0x1000;
	
	// struct size is always 124
	private static final int STRUCT_SIZE = 124;
	
	public int size = STRUCT_SIZE;
	public int flags;
	public int height;
	public int width;
	public int pitchOrLinearSize;
	public int depth;
	public int mipMapCount;
	public int[] reserved1 = new int[11];
	public PixelFormat pixelFormat = new PixelFormat();
	public int caps;
	public int caps2;
	public int caps3;
	public int caps4;
	public int reserved2;
	
	/** Optional DX10 extended header — only non-null when pixelFormat.hasDx10Header() */
	public ExtendedHeader extendedHeader;
	
	public DdsHeader() {
	}
	
	public void read(DataInput in) throws IOException {
		int sz = Integer.reverseBytes(in.readInt());
		if (sz != STRUCT_SIZE) {
			throw new IOException("DDS header size mismatch: expected 124, got " + sz);
		}
		flags = Integer.reverseBytes(in.readInt());
		height = Integer.reverseBytes(in.readInt());
		width = Integer.reverseBytes(in.readInt());
		pitchOrLinearSize = Integer.reverseBytes(in.readInt());
		depth = Integer.reverseBytes(in.readInt());
		mipMapCount = Integer.reverseBytes(in.readInt());
		for (int i = 0; i < 11; i++)
			reserved1[i] = Integer.reverseBytes(in.readInt());
		pixelFormat.read(in);
		caps = Integer.reverseBytes(in.readInt());
		caps2 = Integer.reverseBytes(in.readInt());
		caps3 = Integer.reverseBytes(in.readInt());
		caps4 = Integer.reverseBytes(in.readInt());
		reserved2 = Integer.reverseBytes(in.readInt());
		
		if (pixelFormat.hasDx10Header()) {
			extendedHeader = new ExtendedHeader();
			extendedHeader.read(in);
		}
	}
	
	public void write(DataOutput out) throws IOException {
		out.writeInt(Integer.reverseBytes(size));
		out.writeInt(Integer.reverseBytes(flags));
		out.writeInt(Integer.reverseBytes(height));
		out.writeInt(Integer.reverseBytes(width));
		out.writeInt(Integer.reverseBytes(pitchOrLinearSize));
		out.writeInt(Integer.reverseBytes(depth));
		out.writeInt(Integer.reverseBytes(mipMapCount));
		for (int v : reserved1)
			out.writeInt(Integer.reverseBytes(v));
		pixelFormat.write(out);
		out.writeInt(Integer.reverseBytes(caps));
		out.writeInt(Integer.reverseBytes(caps2));
		out.writeInt(Integer.reverseBytes(caps3));
		out.writeInt(Integer.reverseBytes(caps4));
		out.writeInt(Integer.reverseBytes(reserved2));
		
		if (extendedHeader != null) {
			extendedHeader.write(out);
		}
	}
	
	/**
	 * Resolves the true DXGI format, preferring the DX10 extended header
	 * when present, otherwise falling back to legacy FourCC.
	 */
	public DxgiFormat getPixelFormat() {
		if (extendedHeader != null && extendedHeader.dxgiFormat != DxgiFormat.UNKNOWN) {
			return extendedHeader.dxgiFormat;
		}
		return pixelFormat.getDxgiFormat();
	}
	
	/** Effective mip count (treat 0 as 1). */
	public int effectiveMipCount() {
		return Math.max(1, mipMapCount);
	}
}