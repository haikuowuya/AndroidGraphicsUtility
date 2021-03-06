package agu.bitmap;

import java.io.IOException;
import java.io.InputStream;

public class TwoPhaseBufferedInputStream extends InputStream {
	private static final int INITIAL_BUFFER_CAPACITY = 1024;
	
	private InputStream mIn;
	private byte[] mBuffer = new byte [INITIAL_BUFFER_CAPACITY];
	private int mBufferLength = 0;
	private int mBufferOffset;
	private int mMarkOffset = 0;
	private boolean mBufferExpandable = true;
	private boolean mSecondPhase = false;
	
	public TwoPhaseBufferedInputStream(InputStream in) {
		mIn = in;
	}
	
	@Override
	public void mark(int readlimit) {
		mMarkOffset = mBufferOffset;
		
		if (mSecondPhase && mBufferOffset + readlimit > mBufferLength) {
			mBufferExpandable = true;
			if (mBuffer == null) {
				mBuffer = new byte [INITIAL_BUFFER_CAPACITY];
				mBufferLength = mBufferOffset = mMarkOffset = 0;
			}
		}
	}
	
	@Override
	public void reset() throws IOException {
		mBufferOffset = mMarkOffset;
	}
	
	private void ensureCapacity(int extraLength) {
		int requiredLength = mBufferLength + extraLength;
		if (requiredLength > mBuffer.length) {
			byte[] newBuffer = new byte [requiredLength * 2];
			System.arraycopy(mBuffer, 0, newBuffer, 0, mBufferLength);
			mBuffer = newBuffer;
		}
	}
	
	@Override
	public int read() throws IOException {
		if (mBuffer != null) {
			if (mBufferOffset < mBufferLength) {
				return mBuffer[mBufferOffset++];
			} else if (mBufferExpandable) {
				int oneByte = mIn.read();
				if (oneByte >= 0) {
					ensureCapacity(1);
					mBuffer[mBufferLength++] = (byte) oneByte;
					mBufferOffset = mBufferLength;
				}
				
				return oneByte;
			}
		}

		return mIn.read();
	}
	
	@Override
	public boolean markSupported() {
		return true;
	}

	@Override
	public int read(byte[] buffer, int byteOffset, int byteCount)
			throws IOException {
		
		if (mBuffer != null) {
			int totalBytesRead = 0;
			
			if (mBufferOffset < mBufferLength) {
				int bytesToRead = Math.min(mBufferLength - mBufferOffset, byteCount);
				System.arraycopy(mBuffer, mBufferOffset, buffer, byteOffset, bytesToRead);

				mBufferOffset += bytesToRead;
				byteOffset += bytesToRead;
				byteCount -= bytesToRead;
				totalBytesRead += bytesToRead;
			}
			
			if (byteCount > 0) {
				int bytesRead = mIn.read(buffer, byteOffset, byteCount);

				if (mBufferExpandable) {
					ensureCapacity(bytesRead);
					System.arraycopy(buffer, byteOffset, mBuffer, mBufferLength, bytesRead);

					mBufferLength += bytesRead;
					mBufferOffset = mBufferLength;
				} else {
					mBuffer = null;
					if (mSecondPhase) {
						mBufferExpandable = false;
					}
				}
				
				return totalBytesRead + bytesRead;
			} else {
				return totalBytesRead;
			}
		} else {
			return mIn.read(buffer, byteOffset, byteCount);
		}
	}
	
	@Override
	public void close() throws IOException {
		mIn.close();
	}
	
	public void startSecondPhase() {
		mBufferExpandable = false;
		mSecondPhase = true;
	}
	
	public boolean isSecondPhase() {
		return mSecondPhase;
	}
	
	public void seekToBeginning() {
		mBufferOffset = mMarkOffset = 0;
	}
}
