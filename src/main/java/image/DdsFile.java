package image;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Represents a complete DDS file in memory (magic + header + pixel data).
 * Mirrors the C# DDSFile class.
 *
 * <p>KCD splits textures into a base .dds file (highest mip) plus numbered
 * companion files (.dds.1, .dds.2, …) for lower mips, and an optional
 * .dds.a alpha file with matching .dds.a.1, .dds.a.2, … companions.
 * This class handles only the per-file binary layout; the companion-merging
 * logic lives in {@link com.kcdtexture.converter.ImageConverter}.
 */
public class DdsFile {
	
	public static final int MAGIC = 0x20534444; // "DDS "
	
	/** When true the file starts without the 4-byte magic (KCD alpha sidecar). */
	public final boolean trimmedMagic;
	
	public DdsHeader header = new DdsHeader();
	public byte[] data;
	
	// -------------------------------------------------------------------------
	
	public DdsFile(boolean trimmedMagic) {
		this.trimmedMagic = trimmedMagic;
	}
	
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