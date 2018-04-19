package org.cryptomator.frontend.dokan;

import com.dokany.java.constants.ErrorCode;
import com.dokany.java.constants.NtStatus;
import com.dokany.java.structure.DokanyFileInfo;
import com.dokany.java.structure.FreeSpace;
import com.dokany.java.structure.VolumeInformation;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashSet;

public class ReadWriteAdapter extends ReadOnlyAdapter {

	private static final Logger LOG = LoggerFactory.getLogger(ReadOnlyAdapter.class);

	public ReadWriteAdapter(Path root, VolumeInformation volumeInformation, FreeSpace freeSpace) {
		super(root, volumeInformation, freeSpace);
	}

	@Override
	public long setAllocationSize(WString rawPath, long rawLength, DokanyFileInfo dokanyFileInfo) {
		try {
			if (dokanyFileInfo.Context == 0) {
				Path path = root.resolve(rawPath.toString());
				dokanyFileInfo.Context = fac.open(path, new HashSet<OpenOption>(Collections.singleton(StandardOpenOption.WRITE)));
			}
			fac.get(dokanyFileInfo.Context).truncate(rawLength);
		} catch (IOException e) {
			return ErrorCode.ERROR_WRITE_FAULT.getMask();
		}
		return ErrorCode.SUCCESS.getMask();
	}

	/**
	 * The fileHandle is already closed here, due to the requirements of the dokany implementation to delete a file in the cleanUp method
	 *
	 * @param rawPath
	 * @param dokanyFileInfo {@link DokanyFileInfo} with information about the file or directory.
	 */
	@Override
	public void cleanup(WString rawPath, DokanyFileInfo dokanyFileInfo) {
		LOG.trace("cleanup() is called for: " + getRootedPath(rawPath).toString());
		try {
			if (dokanyFileInfo.Context != 0) {
				fac.close(dokanyFileInfo.Context);
			}
			if (dokanyFileInfo.deleteOnClose()) {
				try {
					Files.delete(getRootedPath(rawPath));
				} catch (IOException e) {
					LOG.warn("Unable to delete File: ", e);
				}
			}
		} catch (IOException e) {
			LOG.warn("Unable to close FileHandle: ", e);
		}

	}

	@Override
	public long writeFile(WString rawPath, Pointer rawBuffer, int rawNumberOfBytesToWrite, IntByReference rawNumberOfBytesWritten, long rawOffset, DokanyFileInfo dokanyFileInfo) {
		LOG.trace("writeFile() is called for " + getRootedPath(rawPath).toString());
		if (dokanyFileInfo.Context == 0) {
			LOG.warn("Attempt to read file " + getRootedPath(rawPath).toString() + " with invalid handle");
			return NtStatus.UNSUCCESSFUL.getMask();
		} else {
			try {
				rawNumberOfBytesWritten.setValue(fac.get(dokanyFileInfo.Context).write(rawBuffer, rawNumberOfBytesToWrite, rawOffset));
			} catch (IOException e) {
				LOG.error("Error while reading file: ", e);
				return ErrorCode.ERROR_WRITE_FAULT.getMask();
			}
			return ErrorCode.SUCCESS.getMask();
		}
	}
}
