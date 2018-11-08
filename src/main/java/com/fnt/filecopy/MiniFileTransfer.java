package com.fnt.filecopy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


public class MiniFileTransfer {

	static final boolean DEBUGGING = false;
	private static final int DEFAULT_BUFFSIZE = 63 * 1024;
	final int buffSize;
	int connectTimeout = 50 * 1000;
	//int readTimeout = 40 * 1000;
	int readTimeout = 120 * 1000;

	public MiniFileTransfer() {
		this.buffSize = DEFAULT_BUFFSIZE;
	}

	public MiniFileTransfer(int buffSize) {
		if (buffSize < 512) {
			buffSize = 512;
		}
		this.buffSize = buffSize;
	}

	public boolean copy(InputStream source, File target) {
		if (source == null) {
			return false;
		}
		if (target == null) {
			return false;
		}
		final FileOutputStream os;
		try {
			// O P E N _ T A R G E T
			os = new FileOutputStream(target);

			// C O P Y _ S O U R C E _ T O _ T A R G E T
			return copy(source, os, true);

			// C L O S E
			// handled by copy.
		} catch (IOException e) {
			return false;
		}
	}// end download

	public boolean copy(InputStream source, OutputStream target,
			boolean closeTarget) {
		if (source == null) {
			return false;
		}
		if (target == null) {
			return false;
		}
		try {
			int chunkSize = buffSize;
			byte[] ba = new byte[chunkSize];

			int bytesRead;
			while ((bytesRead = Read.readBytesBlocking(source, ba, 0,
					chunkSize, readTimeout)) > 0) {
				target.write(ba, 0/* offset */, bytesRead/* len */);
			}

			source.close();
			if (closeTarget) {
				target.close();
			}
		} catch (IOException e) {
			return false;
		}

		// all was ok
		return true;
	}// end copy

	public boolean copy(ZipFile sourceJar, String zipEntryString, File target) {
		if (sourceJar == null) {
			return false;
		}
		if (zipEntryString == null) {
			return false;
		}
		if (target == null) {
			return false;
		}

		try {
			ZipEntry zipEntry = sourceJar.getEntry(zipEntryString);
			if (zipEntry == null) {
				return false;
			}

			InputStream is = sourceJar.getInputStream(zipEntry);
			return is != null && copy(is, target);

			// C L O S E
			// download closes is and target
			// don't close entire zip with sourceJar.close();
		} catch (IOException e) {
			return false;
		}
	}// end copy

	public void setConnectTimeout(int connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public void setReadTimeout(int readTimeout) {
		this.readTimeout = readTimeout;
	}

}
