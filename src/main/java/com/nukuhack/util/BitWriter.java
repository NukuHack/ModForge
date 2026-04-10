package com.nukuhack.util;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.Objects;

/**
 * A utility class for writing bits to various binary data sinks (LSB-first).
 * Supports marking, buffering, and bit-level operations.
 * Bits are written from the least significant bit to most significant bit within each byte.
 */
@Slf4j
public class BitWriter implements Closeable, Flushable {
	private final OutputStream output;
	private final boolean closeUnderlying;
	
	private int currentByte;
	private int bitsFilled;
	/**
	 * -- GETTER --
	 *  Gets the current bit position (0-based)
	 */
	@Getter
	private long bitPosition;
	
	// Track which bit we're on within the current byte (0 = LSB, 7 = MSB)
	private int currentBitIndex;
	
	// Marking support
	private long markBitPosition = - 1;
	private int markCurrentByte;
	private int markBitsFilled;
	private int markCurrentBitIndex;
	private boolean markSet = false;
	
	// Buffering for mark/reset functionality
	private static final int MARK_BUFFER_SIZE = 8192; // 8KB buffer
	private ByteArrayOutputStream markBuffer;
	
	/**
	 * Creates a BitWriter to an OutputStream
	 */
	public BitWriter(OutputStream output) {
		this(output, false);
	}
	
	/**
	 * Creates a BitWriter to an OutputStream with option to close underlying stream
	 */
	public BitWriter(OutputStream output, boolean closeUnderlying) {
		this.output = Objects.requireNonNull(output, "Output stream cannot be null");
		this.closeUnderlying = closeUnderlying;
		this.bitsFilled = 0;
		this.bitPosition = 0;
		this.currentBitIndex = 0;
		this.currentByte = 0;
	}
	
	/**
	 * Creates a BitWriter to a byte array (writes to memory)
	 */
	public BitWriter(byte[] data) {
		this(new ByteArrayOutputStream() {
			@Override
			public synchronized void write(byte[] b, int off, int len) {
				System.arraycopy(b, off, data, off, len);
			}
		}, true);
	}
	
	/**
	 * Creates a BitWriter to a byte array starting at offset
	 */
	public BitWriter(byte[] data, int offset) {
		this(new ByteArrayOutputStream() {
			private int pos = offset;
			
			@Override
			public synchronized void write(int b) {
				if (pos < data.length) {
					data[pos++] = (byte) b;
				}
			}
			
			@Override
			public synchronized void write(byte[] b, int off, int len) {
				int available = data.length - pos;
				int toWrite = Math.min(len, available);
				System.arraycopy(b, off, data, pos, toWrite);
				pos += toWrite;
			}
		}, true);
	}
	
	/**
	 * Creates a BitWriter to a File
	 */
	public BitWriter(File file) throws IOException {
		this(new FileOutputStream(file), true);
	}
	
	/**
	 * Creates a BitWriter to a File with append option
	 */
	public BitWriter(File file, boolean append) throws IOException {
		this(new FileOutputStream(file, append), true);
	}
	
	/**
	 * Writes a single bit as a boolean (LSB first)
	 * @param bit true for 1, false for 0
	 */
	public void write(boolean bit) throws IOException {
		if (bit) {
			currentByte |= (1 << currentBitIndex);
		}
		
		currentBitIndex++;
		bitsFilled++;
		bitPosition++;
		
		if (bitsFilled == 8) {
			flushCurrentByte();
		}
	}
	
	/**
	 * Writes a single bit as a byte (0 or 1)
	 */
	public void writeB(byte bit) throws IOException {
		write(bit != 0);
	}
	
	/**
	 * Writes multiple bits from an integer value (LSB first)
	 * @param value value to write
	 * @param n number of bits to write (1-32)
	 */
	public void writeBits(int value, int n) throws IOException {
		if (n < 0 || n > 32) {
			throw new IllegalArgumentException("Number of bits must be between 0 and 32");
		}
		
		for (int i = 0; i < n; i++) {
			write(((value >> i) & 1) == 1);
		}
	}
	
	/**
	 * Writes multiple bits from a long value (LSB first)
	 * @param value value to write
	 * @param n number of bits to write (1-64)
	 */
	public void writeLongBits(long value, int n) throws IOException {
		if (n < 0 || n > 64) {
			throw new IllegalArgumentException("Number of bits must be between 0 and 64");
		}
		
		for (int i = 0; i < n; i++) {
			write(((value >> i) & 1) == 1);
		}
	}
	
	/**
	 * Writes bits in reverse order (MSB first) - useful when mixing bit orders
	 * @param value value to write
	 * @param n number of bits to write (1-32)
	 */
	public void writeBitsMSB(int value, int n) throws IOException {
		if (n < 0 || n > 32) {
			throw new IllegalArgumentException("Number of bits must be between 0 and 32");
		}
		
		for (int i = n - 1; i >= 0; i--) {
			write(((value >> i) & 1) == 1);
		}
	}
	
	/**
	 * Writes bits in reverse order from a long (MSB first)
	 */
	public void writeLongBitsMSB(long value, int n) throws IOException {
		if (n < 0 || n > 64) {
			throw new IllegalArgumentException("Number of bits must be between 0 and 64");
		}
		
		for (int i = n - 1; i >= 0; i--) {
			write(((value >> i) & 1) == 1);
		}
	}
	
	/**
	 * Writes a full byte (8 bits, LSB first)
	 */
	public void writeByte(byte value) throws IOException {
		writeBits(value & 0xFF, 8);
	}
	
	/**
	 * Writes a short (16 bits, LSB first)
	 */
	public void writeShort(short value) throws IOException {
		writeBits(value & 0xFFFF, 16);
	}
	
	/**
	 * Writes an int (32 bits, LSB first)
	 */
	public void writeInt(int value) throws IOException {
		writeBits(value, 32);
	}
	
	/**
	 * Writes a long (64 bits, LSB first)
	 */
	public void writeLong(long value) throws IOException {
		writeLongBits(value, 64);
	}
	
	/**
	 * Writes a big-endian byte (8 bits, MSB first per byte)
	 */
	public void writeByteBE(byte value) throws IOException {
		writeBitsMSB(value & 0xFF, 8);
	}
	
	/**
	 * Writes a big-endian short (16 bits, MSB first per byte)
	 */
	public void writeShortBE(short value) throws IOException {
		writeBitsMSB(value & 0xFFFF, 16);
	}
	
	/**
	 * Writes a big-endian int (32 bits, MSB first per byte)
	 */
	public void writeIntBE(int value) throws IOException {
		writeBitsMSB(value, 32);
	}
	
	/**
	 * Writes a big-endian long (64 bits, MSB first per byte)
	 */
	public void writeLongBE(long value) throws IOException {
		writeLongBitsMSB(value, 64);
	}
	
	/**
	 * Writes a string as UTF-8 bytes (aligned to byte boundary)
	 */
	public void writeString(String value) throws IOException {
		alignToByte();
		byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		output.write(bytes);
		bitPosition += bytes.length * 8L;
	}
	
	/**
	 * Writes raw bytes (aligned) to the current position
	 */
	public void writeRawBytes(byte[] buffer, int offset, int length) throws IOException {
		alignToByte();
		output.write(buffer, offset, length);
		bitPosition += length * 8L;
	}
	
	/**
	 * Writes raw bytes (aligned)
	 */
	public void writeRawBytes(byte[] buffer) throws IOException {
		writeRawBytes(buffer, 0, buffer.length);
	}
	
	/**
	 * Skips the specified number of bits (writes zeros)
	 */
	public void skipBits(long n) throws IOException {
		for (long i = 0; i < n; i++) {
			write(false);
		}
	}
	
	/**
	 * Marks the current position for later reset
	 */
	public void mark() {
		this.markBitPosition = bitPosition;
		this.markCurrentByte = currentByte;
		this.markBitsFilled = bitsFilled;
		this.markCurrentBitIndex = currentBitIndex;
		this.markSet = true;
		
		// Start buffering future writes
		this.markBuffer = new ByteArrayOutputStream();
	}
	
	/**
	 * Resets to the last marked position
	 */
	public void reset() throws IOException {
		if (! markSet) {
			throw new IOException("No mark set");
		}
		
		this.bitPosition = markBitPosition;
		this.currentByte = markCurrentByte;
		this.bitsFilled = markBitsFilled;
		this.currentBitIndex = markCurrentBitIndex;
		this.markBuffer = null;
	}
	
	/**
	 * Clears any set mark without resetting
	 */
	public void clearMark() {
		this.markSet = false;
		this.markBitPosition = - 1;
		this.markBuffer = null;
	}
	
	/**
	 * Aligns to the next byte boundary (fills remaining bits with zeros)
	 */
	public void alignToByte() throws IOException {
		if (bitsFilled > 0) {
			flushCurrentByte();
		}
	}
	
	/**
	 * Flushes the current byte to the output stream
	 */
	private void flushCurrentByte() throws IOException {
		if (bitsFilled > 0) {
			output.write(currentByte);
			
			// Also write to mark buffer if set
			if (markSet && markBuffer != null) {
				markBuffer.write(currentByte);
			}
			
			currentByte = 0;
			bitsFilled = 0;
			currentBitIndex = 0;
		}
	}
	
	/**
	 * Returns the number of bits written so far
	 */
	public long getBitsWritten() {
		return bitPosition;
	}
	
	/**
	 * Returns the number of bytes written so far (rounded up)
	 */
	public long getBytesWritten() {
		return (bitPosition + 7) / 8;
	}
	
	@Override
	public void flush() throws IOException {
		flushCurrentByte();
		output.flush();
	}
	
	@Override
	public void close() throws IOException {
		flush();
		if (closeUnderlying) {
			output.close();
		}
	}
}