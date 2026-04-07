package image;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * DDS_HEADER_DXT10 — the optional 20-byte extended header that follows
 * the main DDS header when pixelFormat.fourCC == "DX10".
 * Mirrors the C# ExtendedHeader class.
 */
@lombok.extern.slf4j.Slf4j
public class ExtendedHeader {
	
	public DxgiFormat dxgiFormat = DxgiFormat.UNKNOWN;
	public int resourceDimension;
	public int miscFlag;
	public int arraySize;
	public int miscFlags2;
	
	public ExtendedHeader() {
	}
	
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