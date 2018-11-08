package com.fnt.filecopy;

import java.io.IOException;
import java.io.InputStream;


public final class Read {
	private static final int SLEEP_TIME = 100;

	public static int readBytesBlocking(InputStream in, byte b[], int off,
			int len, int timeoutInMillis) throws IOException {
		int totalBytesRead = 0;
		int bytesRead;
		long whenToGiveUp = System.currentTimeMillis() + timeoutInMillis;
		while (totalBytesRead < len
				&& (bytesRead = in.read(b, off + totalBytesRead, len
						- totalBytesRead)) >= 0) {
			if (bytesRead == 0) {
				try {
					if (System.currentTimeMillis() >= whenToGiveUp) {
						throw new IOException("timeout");
					}
					Thread.sleep(SLEEP_TIME);
				} catch (InterruptedException e) {
				}
			} else {
				totalBytesRead += bytesRead;
				whenToGiveUp = System.currentTimeMillis() + timeoutInMillis;
			}
		}
		return totalBytesRead;
	}

	/**
	 * Prevent instantiation.
	 */
	private Read() {
	}
}
