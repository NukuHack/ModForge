package image;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * DDS legacy pixel format structure (DDSPF) — always 32 bytes.
 * Mirrors the C# PixelFormat class exactly.
 */
@Slf4j
@NoArgsConstructor
public class PixelFormat {
	
	// DDPF flags
	public static final int DDPF_ALPHAPIXELS = 0x1;
	public static final int DDPF_FOURCC = 0x4;
	public static final int DDPF_RGB = 0x40;
	// Common FourCC codes
	public static final int FOURCC_DXT1 = 0x31545844; // "DXT1"
	public static final int FOURCC_DXT2 = 0x32545844; // "DXT2"
	public static final int FOURCC_DXT3 = 0x33545844; // "DXT3"
	public static final int FOURCC_DXT4 = 0x34545844; // "DXT4"
	public static final int FOURCC_DXT5 = 0x35545844; // "DXT5"
	public static final int FOURCC_DX10 = 0x30315844; // "DX10" — extended header follows
	public static final int FOURCC_ATI1 = 0x31495441; // "ATI1" (BC4)
	public static final int FOURCC_ATI2 = 0x32495441; // "ATI2" (BC5)
	public static final int FOURCC_BC4U = 0x55344342; // "BC4U"
	public static final int FOURCC_BC4S = 0x53344342; // "BC4S"
	public static final int FOURCC_BC5U = 0x55354342; // "BC5U"
	public static final int FOURCC_BC5S = 0x53354342; // "BC5S"
	private static final int STRUCT_SIZE = 32;
	public int size = 32;
	public int flags;
	public int fourCC;
	public int rgbBitCount;
	public int rBitMask;
	public int gBitMask;
	public int bBitMask;
	public int aBitMask;
	
	/**
	 * Read from a little-endian DataInput (wraps a LittleEndianDataInputStream).
	 */
	public void read(DataInput in) throws IOException {
		int sz = Integer.reverseBytes(in.readInt());
		if (sz != STRUCT_SIZE) {
			throw new IOException("DDS PixelFormat size mismatch: expected 32, got " + sz);
		}
		flags = Integer.reverseBytes(in.readInt());
		fourCC = Integer.reverseBytes(in.readInt());
		rgbBitCount = Integer.reverseBytes(in.readInt());
		rBitMask = Integer.reverseBytes(in.readInt());
		gBitMask = Integer.reverseBytes(in.readInt());
		bBitMask = Integer.reverseBytes(in.readInt());
		aBitMask = Integer.reverseBytes(in.readInt());
	}
	
	public void write(DataOutput out) throws IOException {
		out.writeInt(Integer.reverseBytes(size));
		out.writeInt(Integer.reverseBytes(flags));
		out.writeInt(Integer.reverseBytes(fourCC));
		out.writeInt(Integer.reverseBytes(rgbBitCount));
		out.writeInt(Integer.reverseBytes(rBitMask));
		out.writeInt(Integer.reverseBytes(gBitMask));
		out.writeInt(Integer.reverseBytes(bBitMask));
		out.writeInt(Integer.reverseBytes(aBitMask));
	}
	
	/** Returns true when the DX10 extended header is present. */
	public boolean hasDx10Header() {
		return (flags & DDPF_FOURCC) != 0 && fourCC == FOURCC_DX10;
	}
	
	/**
	 * Derives a DxgiFormat from the legacy FourCC field.
	 * Matches C# GetPixelFormat() exactly.
	 */
	public DxgiFormat getDxgiFormat() {
		return switch (fourCC) {
			case FOURCC_DXT1 -> DxgiFormat.BC1_UNORM;
			case FOURCC_DXT2, FOURCC_DXT3 -> DxgiFormat.BC2_UNORM;
			case FOURCC_DXT4, FOURCC_DXT5 -> DxgiFormat.BC3_UNORM;
			case FOURCC_ATI1, FOURCC_BC4U -> DxgiFormat.BC4_UNORM;
			case FOURCC_BC4S -> DxgiFormat.BC4_SNORM;
			case FOURCC_ATI2, FOURCC_BC5U -> DxgiFormat.BC5_UNORM;
			case FOURCC_BC5S -> DxgiFormat.BC5_SNORM;
			default -> DxgiFormat.UNKNOWN;
		};
	}
}