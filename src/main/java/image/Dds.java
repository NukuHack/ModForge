package image;

import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * DDS_HEADER — the main 124-byte DDS header (after the 4-byte magic).
 */
@Slf4j
@NoArgsConstructor
public class Dds {
	
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
	
	/**
	 * DDS_HEADER_DXT10 — the optional 20-byte extended header that follows
	 * the main DDS header when pixelFormat.fourCC == "DX10".
	 * Mirrors the C# ExtendedHeader class.
	 */
	@NoArgsConstructor
	public static class ExtendedHeader {
		
		public DxgiFormat dxgiFormat = DxgiFormat.UNKNOWN;
		public int resourceDimension;
		public int miscFlag;
		public int arraySize;
		public int miscFlags2;
		
		public void read(DataInput in) throws IOException {
			dxgiFormat = DxgiFormat.fromValue(Integer.reverseBytes(in.readInt()));
			resourceDimension = Integer.reverseBytes(in.readInt());
			miscFlag = Integer.reverseBytes(in.readInt());
			arraySize = Integer.reverseBytes(in.readInt());
			miscFlags2 = Integer.reverseBytes(in.readInt());
		}
		
		public void write(DataOutput out) throws IOException {
			out.writeInt(Integer.reverseBytes(dxgiFormat.getValue()));
			out.writeInt(Integer.reverseBytes(resourceDimension));
			out.writeInt(Integer.reverseBytes(miscFlag));
			out.writeInt(Integer.reverseBytes(arraySize));
			out.writeInt(Integer.reverseBytes(miscFlags2));
		}
	}
	
	/**
	 * Represents a complete DDS file in memory (magic + header + pixel data).
	 * Mirrors the C# DDSFile class.
	 *
	 * <p>KCD splits textures into a base .dds file (highest mip) plus numbered
	 * companion files (.dds.1, .dds.2, …) for lower MIPS, and an optional
	 * .dds.a alpha file with matching .dds.a.1, .dds.a.2, … companions.
	 * This class handles only the per-file binary layout; the companion-merging
	 * logic lives in {@link image.ImageConverter}.
	 */
	@Slf4j
	@RequiredArgsConstructor
	public static class DdsFile {
		
		public static final int MAGIC = 0x20534444; // "DDS "
		
		/** When true the file starts without the 4-byte magic (KCD alpha sidecar). */
		public final boolean trimmedMagic;
		
		public Dds header = new Dds();
		public byte[] data;
		
		// -------------------------------------------------------------------------
		
		public DdsFile(Path path, boolean trimmedMagic) throws IOException {
			this.trimmedMagic = trimmedMagic;
			read(path);
		}
		
		public DdsFile(byte[] bytes, boolean trimmedMagic) throws IOException {
			this.trimmedMagic = trimmedMagic;
			read(bytes);
		}
		
		// -------------------------------------------------------------------------
		// Reading
		// -------------------------------------------------------------------------
		
		public void read(Path path) throws IOException {
			read(Files.readAllBytes(path));
		}
		
		public void read(byte[] bytes) throws IOException {
			try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
				read(in);
			}
		}
		
		public void read(DataInputStream in) throws IOException {
			if (! trimmedMagic) {
				int magic = Integer.reverseBytes(in.readInt());
				if (magic != MAGIC) {
					throw new IOException("Not a DDS file: invalid magic 0x" + Integer.toHexString(magic));
				}
			}
			header.read(in);
			data = in.readAllBytes();
		}
		
		// -------------------------------------------------------------------------
		// Writing
		// -------------------------------------------------------------------------
		
		public byte[] toBytes() throws IOException {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			write(bos);
			return bos.toByteArray();
		}
		
		public void write(Path path) throws IOException {
			Files.write(path, toBytes());
		}
		
		public void write(OutputStream out) throws IOException {
			DataOutputStream dos = new DataOutputStream(out);
			// Always write magic on output regardless of trimmedMagic
			dos.writeInt(Integer.reverseBytes(MAGIC));
			header.write(dos);
			dos.write(data);
			dos.flush();
		}
	}
	
	static class ImgData {
		final int h, w, code, length;
		final ExtendedHeader header;
		byte[] data;
		
		ImgData(int h, int w, int code, int length, byte[] data) {
			this.h = h;
			this.w = w;
			this.code = code;
			this.length = length;
			this.header = null;
			this.data = data;
		}
	}
}