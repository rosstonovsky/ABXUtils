/*
 * Copyright (C) 2023 rosstonovsky
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.rosstonovsky.abxutils;

import java.io.Closeable;
import java.io.DataOutput;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Objects;

public class FastDataOutput implements DataOutput, Flushable, Closeable {
	private static final int MAX_UNSIGNED_SHORT = 65_535;

	private final OutputStream mOut;

	private final byte[] mBuffer;
	private final int mBufferCap;
	private int mBufferPos;

	/**
	 * Values that have been "interned" by {@link #writeInternedUTF(String)}.
	 */
	private final HashMap<String, Short> mStringRefs = new HashMap<>();

	public FastDataOutput(OutputStream out, int bufferSize) {
		mOut = Objects.requireNonNull(out);
		if (bufferSize < 8) {
			throw new IllegalArgumentException();
		}
		mBuffer = new byte[bufferSize];
		mBufferCap = mBuffer.length;
	}

	private void drain() throws IOException {
		if (mBufferPos > 0) {
			mOut.write(mBuffer, 0, mBufferPos);
			mBufferPos = 0;
		}
	}

	@Override
	public void flush() throws IOException {
		drain();
		mOut.flush();
	}

	@Override
	public void close() throws IOException {
		mOut.close();
	}

	@Override
	public void write(int b) throws IOException {
		writeByte(b);
	}

	@Override
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		if (mBufferCap < len) {
			drain();
			mOut.write(b, off, len);
		} else {
			if (mBufferCap - mBufferPos < len) drain();
			System.arraycopy(b, off, mBuffer, mBufferPos, len);
			mBufferPos += len;
		}
	}

	@Override
	public void writeUTF(String s) throws IOException {
		// Attempt to write directly to buffer space if there's enough room,
		// otherwise fall back to chunking into place
		if (mBufferCap - mBufferPos < 2 + s.length()) drain();
		byte[] b = s.getBytes(StandardCharsets.UTF_8);
		writeShort(b.length);
		write(b, 0, b.length);
	}

	/**
	 * Write a {@link String} value with the additional signal that the given
	 * value is a candidate for being canonicalized, similar to
	 * {@link String#intern()}.
	 * <p>
	 * Canonicalization is implemented by writing each unique string value once
	 * the first time it appears, and then writing a lightweight {@code short}
	 * reference when that string is written again in the future.
	 */
	public void writeInternedUTF(String s) throws IOException {
		Short ref = mStringRefs.get(s);
		if (ref != null) {
			writeShort(ref);
		} else {
			writeShort(MAX_UNSIGNED_SHORT);
			writeUTF(s);
			// We can only safely intern when we have remaining values; if we're
			// full we at least sent the string value above
			ref = (short) mStringRefs.size();
			if (mStringRefs.size() < MAX_UNSIGNED_SHORT) {
				mStringRefs.put(s, ref);
			}
		}
	}

	@Override
	public void writeBoolean(boolean v) throws IOException {
		writeByte(v ? 1 : 0);
	}

	@Override
	public void writeByte(int v) throws IOException {
		if (mBufferCap - mBufferPos < 1) drain();
		mBuffer[mBufferPos++] = (byte) ((v) & 0xff);
	}

	@Override
	public void writeShort(int v) throws IOException {
		if (mBufferCap - mBufferPos < 2) drain();
		mBuffer[mBufferPos++] = (byte) ((v >> 8) & 0xff);
		mBuffer[mBufferPos++] = (byte) ((v) & 0xff);
	}

	@Override
	public void writeChar(int v) throws IOException {
		writeShort((short) v);
	}

	@Override
	public void writeInt(int v) throws IOException {
		if (mBufferCap - mBufferPos < 4) drain();
		mBuffer[mBufferPos++] = (byte) ((v >> 24) & 0xff);
		mBuffer[mBufferPos++] = (byte) ((v >> 16) & 0xff);
		mBuffer[mBufferPos++] = (byte) ((v >> 8) & 0xff);
		mBuffer[mBufferPos++] = (byte) ((v) & 0xff);
	}

	@Override
	public void writeLong(long v) throws IOException {
		if (mBufferCap - mBufferPos < 8) drain();
		int i = (int) (v >> 32);
		mBuffer[mBufferPos++] = (byte) ((i >> 24) & 0xff);
		mBuffer[mBufferPos++] = (byte) ((i >> 16) & 0xff);
		mBuffer[mBufferPos++] = (byte) ((i >> 8) & 0xff);
		mBuffer[mBufferPos++] = (byte) ((i) & 0xff);
		i = (int) v;
		mBuffer[mBufferPos++] = (byte) ((i >> 24) & 0xff);
		mBuffer[mBufferPos++] = (byte) ((i >> 16) & 0xff);
		mBuffer[mBufferPos++] = (byte) ((i >> 8) & 0xff);
		mBuffer[mBufferPos++] = (byte) ((i) & 0xff);
	}

	@Override
	public void writeFloat(float v) throws IOException {
		writeInt(Float.floatToIntBits(v));
	}

	@Override
	public void writeDouble(double v) throws IOException {
		writeLong(Double.doubleToLongBits(v));
	}

	@Override
	public void writeBytes(String s) {
		// Callers should use writeUTF()
		throw new UnsupportedOperationException();
	}

	@Override
	public void writeChars(String s) {
		// Callers should use writeUTF()
		throw new UnsupportedOperationException();
	}
}