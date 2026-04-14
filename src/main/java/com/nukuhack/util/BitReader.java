package com.nukuhack.util;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.Objects;

/**
 * A utility class for reading bits from various binary data sources (LSB-first).
 * Supports marking, non-consuming reads, and bit-level operations.
 * Bits are read from the least significant bit to most significant bit within each byte.
 */
@Slf4j
public class BitReader implements Closeable {
	private final InputStream input;
	private final boolean closeUnderlying;
	
	private int currentByte;
	private int bitsRemaining;
	/**
	 * -- GETTER --
	 *  Gets the current bit position (0-based)
	 */
	@Getter
	private long bitPosition;
	
	private int currentBitIndex;
	
	private long markBitPosition = - 1;
	private int markCurrentByte;
	private int markBitsRemaining;
	private int markCurrentBitIndex;
	private boolean markSet = false;
	
	private static final int MARK_BUFFER_SIZE = 8192;
	
	private byte[] markBuffer;
	private int markBufferPos;
	private int markBufferLimit;
	private final InputStream originalInput;
	
	/**
	 * Creates a BitReaderLSB from an InputStream
	 */
	public BitReader(InputStream input) {
		this(input, false);
	}
	
	/**
	 * Creates a BitReaderLSB from an InputStream with option to close underlying stream
	 */
	public BitReader(InputStream input, boolean closeUnderlying) {
		this.originalInput = Objects.requireNonNull(input, "Input stream cannot be null");
		this.closeUnderlying = closeUnderlying;
		
		if (input.markSupported()) {
			this.input = input;
		} else {
			this.input = new BufferedInputStream(input, MARK_BUFFER_SIZE);
		}
		
		this.bitsRemaining = 0;
		this.bitPosition = 0;
		this.currentBitIndex = 0;
	}
	
	/**
	 * Creates a BitReaderLSB from a byte array
	 */
	public BitReader(byte[] data) {
		this(new ByteArrayInputStream(data), true);
	}
	
	/**
	 * Creates a BitReaderLSB from a File
	 */
	public BitReader(File file) throws IOException {
		this(new FileInputStream(file), true);
	}
	
	/**
	 * Reads a single bit as a boolean (LSB first)
	 * @return true for 1, false for 0
	 * @throws EOFException if end of stream is reached
	 */
	public boolean read() throws IOException {
		ensureBitsAvailable();
		
		boolean result = ((currentByte >> currentBitIndex) & 1) == 1;
		currentBitIndex++;
		bitsRemaining--;
		bitPosition++;
		
		return result;
	}
	
	/**
	 * Reads a single bit as a byte (0 or 1)
	 * @return 0 or 1
	 * @throws EOFException if end of stream is reached
	 */
	public byte readB() throws IOException {
		return read() ? (byte) 1 : (byte) 0;
	}
	
	/**
	 * Reads multiple bits as an integer value (LSB first)
	 * @param n number of bits to read (1-32)
	 * @return integer value of the bits
	 */
	public int readBits(int n) throws IOException {
		if (n < 0 || n > 32) {
			throw new IllegalArgumentException("Number of bits must be between 0 and 32");
		}
		
		int result = 0;
		for (int i = 0; i < n; i++) {
			
			if (read()) {
				result |= (1 << i);
			}
		}
		return result;
	}
	
	/**
	 * Reads multiple bits as a long value (LSB first)
	 * @param n number of bits to read (1-64)
	 * @return long value of the bits
	 */
	public long readLongBits(int n) throws IOException {
		if (n < 0 || n > 64) {
			throw new IllegalArgumentException("Number of bits must be between 0 and 64");
		}
		
		long result = 0;
		for (int i = 0; i < n; i++) {
			
			if (read())
				result |= (1L << i);
		}
		return result;
	}
	
	/**
	 * Reads bits in reverse order (MSB first) - useful when mixing bit orders
	 * @param n number of bits to read (1-32)
	 * @return integer value of the bits (MSB first)
	 */
	public int readBitsMSB(int n) throws IOException {
		if (n < 0 || n > 32) {
			throw new IllegalArgumentException("Number of bits must be between 0 and 32");
		}
		
		int result = 0;
		for (int i = 0; i < n; i++) {
			result = (result << 1) | (read() ? 1 : 0);
		}
		return result;
	}
	
	public long readLongBitsMSB(int n) throws IOException {
		if (n < 0 || n > 64) {
			throw new IllegalArgumentException("Number of bits must be between 0 and 64");
		}
		
		long result = 0;
		for (int i = 0; i < n; i++) {
			result = (result << 1) | (read() ? 1 : 0);
		}
		return result;
	}
	
	/**
	 * Reads a full byte (8 bits, LSB first)
	 */
	public byte readByte() throws IOException {
		return (byte) readBits(8);
	}
	
	/**
	 * Reads a short (16 bits, LSB first)
	 */
	public short readShort() throws IOException {
		return (short) readBits(16);
	}
	
	/**
	 * Reads an int (32 bits, LSB first)
	 */
	public int readInt() throws IOException {
		return readBits(32);
	}
	
	/**
	 * Reads a long (64 bits, LSB first)
	 */
	public long readLong() throws IOException {
		return readLongBits(64);
	}
	
	/**
	 * Reads a big-endian byte (8 bits, MSB first per byte)
	 */
	public byte readByteBE() throws IOException {
		return (byte) readBitsMSB(8);
	}
	
	/**
	 * Reads a big-endian short (16 bits, MSB first per byte)
	 */
	public short readShortBE() throws IOException {
		return (short) readBitsMSB(16);
	}
	
	/**
	 * Reads a big-endian int (32 bits, MSB first per byte)
	 */
	public int readIntBE() throws IOException {
		return readBitsMSB(32);
	}
	
	/**
	 * Reads a big-endian long (64 bits, MSB first per byte)
	 */
	public long readLongBE() throws IOException {
		return readLongBitsMSB(64);
	}
	
	/**
	 * Peeks at the next bit without consuming it (LSB first)
	 */
	public boolean peek() throws IOException {
		ensureBitsAvailable();
		return ((currentByte >> currentBitIndex) & 1) == 1;
	}
	
	/**
	 * Peeks at the next n bits without consuming them (LSB first)
	 */
	public int peekBits(int n) throws IOException {
		if (n < 0 || n > 32) {
			throw new IllegalArgumentException("Number of bits must be between 0 and 32");
		}
		
		long pos = getBitPosition();
		int result = readBits(n);
		seek(pos);
		return result;
	}
	
	/**
	 * Skips the specified number of bits
	 */
	public void skipBits(long n) throws IOException {
		for (long i = 0; i < n; i++) {
			if (! hasNext()) {
				throw new EOFException("Cannot skip " + (n - i) + " more bits");
			}
			read();
		}
	}
	
	/**
	 * Checks if there are more bits available
	 */
	public boolean hasNext() {
		try {
			ensureBitsAvailable();
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	
	/**
	 * Returns the number of bits available (or -1 if unknown)
	 */
	public long availableBits() {
		try {
			int bytesAvailable = input.available();
			return bitsRemaining + (bytesAvailable * 8L);
		} catch (IOException e) {
			return - 1;
		}
	}
	
	/**
	 * Seeks to a specific bit position (only supported if underlying stream supports it)
	 */
	public void seek(long bitPos) throws IOException {
		if (markSet && bitPos >= markBitPosition) {
			
			reset();
			skipBits(bitPos - markBitPosition);
		} else {
			throw new UnsupportedOperationException("Seek to arbitrary position not supported. Use mark() to create a restore point.");
		}
	}
	
	/**
	 * Marks the current position for later reset
	 */
	public void mark() {
		
		if (input.markSupported()) {
			input.mark(MARK_BUFFER_SIZE);
		}
		
		this.markBitPosition = bitPosition;
		this.markCurrentByte = currentByte;
		this.markBitsRemaining = bitsRemaining;
		this.markCurrentBitIndex = currentBitIndex;
		this.markSet = true;
	}
	
	/**
	 * Resets to the last marked position
	 */
	public void reset() throws IOException {
		if (! markSet) {
			throw new IOException("No mark set");
		}
		
		if (input.markSupported()) {
			input.reset();
		}
		
		this.bitPosition = markBitPosition;
		this.currentByte = markCurrentByte;
		this.bitsRemaining = markBitsRemaining;
		this.currentBitIndex = markCurrentBitIndex;
	}
	
	/**
	 * Clears any set mark without resetting
	 */
	public void clearMark() {
		this.markSet = false;
		this.markBitPosition = - 1;
	}
	
	/**
	 * Aligns to the next byte boundary (skips remaining bits in current byte)
	 */
	public void alignToByte() throws IOException {
		if (bitsRemaining > 0) {
			skipBits(bitsRemaining);
		}
	}
	
	/**
	 * Reads raw bytes (aligned) from the current position
	 */
	public int readRawBytes(byte[] buffer, int offset, int length) throws IOException {
		alignToByte();
		return input.read(buffer, offset, length);
	}
	
	/**
	 * Reads all remaining bytes from the current aligned position
	 */
	public byte[] readAllRemainingBytes() throws IOException {
		alignToByte();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int bytesRead;
		while ((bytesRead = input.read(buffer)) != - 1) {
			baos.write(buffer, 0, bytesRead);
		}
		return baos.toByteArray();
	}
	
	private void ensureBitsAvailable() throws IOException {
		if (bitsRemaining != 0)
			return;
		
		int nextByte = input.read();
		if (nextByte == - 1) {
			throw new EOFException("End of stream reached");
		}
		currentByte = nextByte;
		bitsRemaining = 8;
		currentBitIndex = 0;
		
	}
	
	@Override
	public void close() throws IOException {
		if (closeUnderlying) {
			input.close();
		}
	}
}