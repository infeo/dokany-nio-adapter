package com.dokany.java;

import com.dokany.java.structure.DokanyFileInfo;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.WinBase.FILETIME;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.Date;
import java.util.Objects;

/**
 * Utilities to do various operations.
 */
public class DokanyUtils {

	private static final Logger LOG = LoggerFactory.getLogger(DokanyUtils.class);

	private DokanyUtils() {

	}

	/**
	 * Uses *nix separator
	 *
	 * @param str
	 * @return
	 */
	public static String trimTailSeparator(final String str) {
		return str.endsWith(UNIX_SEPARATOR) ? str.substring(0, str.length() - 1) : str;
	}

	/**
	 * Uses *nix separator
	 *
	 * @param str
	 * @return
	 */
	public static String trimFrontSeparator(final String str) {
		return str.startsWith(UNIX_SEPARATOR) ? str.substring(1, str.length()) : str;
	}

	// TODO: can this return null?

	/**
	 * @param path
	 * @return
	 */
	public static File toFile(final String path) {
		return Paths.get(path).toFile();
	}

	public static String UNIX_SEPARATOR = FilenameUtils.separatorsToUnix(File.separator);

	/**
	 * Will add tail UNIX_SEPARATOR if file is a directory and tail separator is not already present
	 *
	 * @param path
	 * @return
	 */
	public static String normalize(final String path) {
		String normalizedPath = FilenameUtils.normalize(path, true);

		if (new File(normalizedPath).isDirectory()) {
			final int lastSeparator = indexOfLastSeparator(normalizedPath);

			if ((lastSeparator == -1) || (lastSeparator != (normalizedPath.length() - 1))) {
				normalizedPath += UNIX_SEPARATOR;
			}
		}
		return normalizedPath;
	}

	public static int indexOfLastSeparator(final String normalizedPath) {
		return FilenameUtils.indexOfLastSeparator(normalizedPath);
	}

	// TODO: can this return null?
	public static String normalize(final WString path) {
		return normalize(path.toString());
	}

	// TODO: can this return null?
	public static String normalize(final Path path) {
		return normalize(path.toString());
	}

	// TODO: can this return null?
	static String getFileName(final String fileName) {
		return FilenameUtils.getBaseName(fileName);
	}

	// TODO: can this return null?
	static String getExtension(final String fileName) {
		return FilenameUtils.getExtension(fileName);
	}

	// TODO: can this return null?
	public static String toShortName(final Path path) {
		String pathAsStr = path.toString();

		String base = trimStrToSize(getFileName(pathAsStr), 8);
		LOG.trace("base: {}", base);

		String ext = trimStrToSize(getExtension(pathAsStr), 3);
		if (ext.length() > 0) {
			ext = "." + ext;
		}
		LOG.trace("ext: {}", ext);
		return base + ext;
	}


	public static String trimStrToSize(final String str, final int len) {
		return str.substring(0, Math.min(str.length(), len));
	}

	public static FileTime toFileTime(final FILETIME time) {
		return FileTime.from(time.toDate().toInstant());
	}

	public static FILETIME toFILETIME(final FileTime time) {
		return getTime(time.toMillis());
	}

	public static FILETIME getTime(final Date date) {
		return new FILETIME(date);
	}

	public static FILETIME getTime(final long time) {
		return getTime(new Date(time));
	}

	public static FILETIME getCurrentTime() {
		return getTime(new Date());
	}

	/**
	 * @param path
	 * @return
	 */
	public static BasicFileAttributeView getBasicAttributes(final String path) {
		return getBasicAttributes(Paths.get(path));
	}

	/**
	 * @param path
	 * @return
	 */
	public static BasicFileAttributeView getBasicAttributes(final Path path) {
		return Files.getFileAttributeView(path, BasicFileAttributeView.class);
	}


	/**
	 * Set DokanyFileInfo.DeleteOnClose based on whether file or directory can be deleted.
	 *
	 * @param fileOrDirectory
	 * @param dokanyFileInfo
	 */
	public static void setDeleteStatus(final File fileOrDirectory, final DokanyFileInfo dokanyFileInfo) {
		boolean canDelete = fileOrDirectory.renameTo(fileOrDirectory);

		if (canDelete) {
			dokanyFileInfo.DeleteOnClose = 1;
		}
	}

	/**
	 * Returns String representation of WString.
	 *
	 * @param wStr .
	 * @return if wStr is null, method will return null
	 */
	public static String wStrToStr(final WString wStr) {
		return Objects.nonNull(wStr) ? wStr.toString() : null;
	}
}
