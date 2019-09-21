package org.cryptomator.frontend.dokany;

import com.google.common.collect.Sets;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import dev.dokan.dokan_java.constants.microsoft.AccessMask;
import dev.dokan.dokan_java.constants.microsoft.CreateOption;
import dev.dokan.dokan_java.constants.microsoft.CreateOptions;
import dev.dokan.dokan_java.constants.microsoft.CreationDisposition;
import dev.dokan.dokan_java.constants.microsoft.FileAttribute;
import dev.dokan.dokan_java.structure.EnumIntegerSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static dev.dokan.dokan_java.constants.microsoft.CreationDisposition.*;

public class FileUtil {

	public static final Instant WINDOWS_EPOCH_START = Instant.parse("1601-01-01T00:00:00Z");
	// from wdm.h
	public static final int FILE_SUPERSEDE = 0x00000000;
	public static final int FILE_OPEN = 0x00000001;
	public static final int FILE_CREATE = 0x00000002;
	public static final int FILE_OPEN_IF = 0x00000003;
	public static final int FILE_OVERWRITE = 0x00000004;
	public static final int FILE_OVERWRITE_IF = 0x00000005;

	static final FileAttribute[] supportedAttributeValuesToSet = new FileAttribute[]{FileAttribute.HIDDEN, FileAttribute.READONLY, FileAttribute.SYSTEM, FileAttribute.ARCHIVE};

	private static final Logger LOG = LoggerFactory.getLogger(FileUtil.class);
	private static final Set<Integer> globOperatorsToEscapeCodePoints;


	static {
		char[] globOperatorsToEscape = new char[]{'[', ']', '{', '}'};
		globOperatorsToEscapeCodePoints = Sets.newHashSet();
		for (int i = 0; i < globOperatorsToEscape.length; i++) {
			globOperatorsToEscapeCodePoints.add(Character.codePointAt(globOperatorsToEscape, i));
		}
	}

	/**
	 * TODO: support for other attributes ?
	 *
	 * @param attr the DOS file attributes of the java standard API
	 * @return
	 */
	public static EnumIntegerSet<FileAttribute> dosAttributesToEnumIntegerSet(DosFileAttributes attr) {
		EnumIntegerSet<FileAttribute> set = new EnumIntegerSet<>(FileAttribute.class);
		if (attr.isArchive()) {
			set.add(FileAttribute.ARCHIVE);
		}
		if (attr.isHidden()) {
			set.add(FileAttribute.HIDDEN);
		}
		if (attr.isReadOnly()) {
			set.add(FileAttribute.READONLY);
		}
		if (attr.isSystem()) {
			set.add(FileAttribute.SYSTEM);
		}
		if (attr.isDirectory()) {
			set.add(FileAttribute.DIRECTORY);
		}
		if (attr.isSymbolicLink()) {
			set.add(FileAttribute.REPARSE_POINT);
		}
		if (attr.isRegularFile() && set.isEmpty()) {
			set.add(FileAttribute.NORMAL);
		}
		return set;
	}

	public static void setAttribute(DosFileAttributeView attrView, FileAttribute attr, boolean value) throws IOException {
		switch (attr) {
			case ARCHIVE:
				attrView.setArchive(value);
				break;
			case HIDDEN:
				attrView.setHidden(value);
				break;
			case READONLY:
				attrView.setReadOnly(value);
				break;
			case SYSTEM:
				attrView.setSystem(value);
				break;
			default:
				LOG.debug("Windows file attribute {} is currently not supported and thus will be ignored", attr.name());
		}
	}

	public static Set<OpenOption> buildOpenOptions(int accessMasks, EnumIntegerSet<FileAttribute> fileAttributes, EnumIntegerSet<CreateOption> createOptions, CreationDisposition creationDisposition, boolean append, boolean fileExists) {
		Set<OpenOption> openOptions = Sets.newHashSet();
		if (((accessMasks & WinNT.GENERIC_WRITE) | (accessMasks & WinNT.FILE_READ_DATA)) != 0) {
			openOptions.add(StandardOpenOption.WRITE);
		}
		if (((accessMasks & WinNT.GENERIC_READ) | (accessMasks & WinNT.FILE_READ_DATA)) != 0) {
			openOptions.add(StandardOpenOption.READ);
			//openOptions.add(StandardOpenOption.SYNC); TODO: research to what flags GENERIC_READ, GENERIC_WRITE and GENERIC_ALL translate!
		}
		if (((accessMasks & AccessMask.MAXIMUM_ALLOWED.getMask()) | (accessMasks & WinNT.GENERIC_ALL)) != 0) {
			openOptions.add(StandardOpenOption.READ);
			openOptions.add(StandardOpenOption.WRITE);
		}
		if (createOptions.contains(CreateOptions.FILE_WRITE_THROUGH) || createOptions.contains(CreateOptions.FILE_NO_INTERMEDIATE_BUFFERING)) {
			openOptions.add(StandardOpenOption.SYNC);
		}
		if (append) {
			openOptions.add(StandardOpenOption.APPEND);
		}
		// From the Java Documentation of DELETE_ON_CLOSE:This option is not recommended for use when opening files that are open concurrently by other entities.
//		if (accessMasks.contains(AccessMask.DELETE) && createOptions.contains(CreateOptions.FILE_DELETE_ON_CLOSE)) {
//			//openOptions.add(StandardOpenOption.DELETE_ON_CLOSE);
//		}
		if (fileAttributes.contains(FileAttribute.SPARSE_FILE)) {
			openOptions.add(StandardOpenOption.SPARSE);
		}
		switch (creationDisposition) {
			case CREATE_NEW:
				openOptions.add(StandardOpenOption.CREATE_NEW);
				openOptions.add(StandardOpenOption.WRITE); //Necessary, otherwise an Exceptions is thrown during filechannel creation
				break;
			case CREATE_ALWAYS:
				if (fileExists) {
					openOptions.add(StandardOpenOption.TRUNCATE_EXISTING);
				} else {
					openOptions.add(StandardOpenOption.CREATE);
					openOptions.add(StandardOpenOption.WRITE);
				}
				break;
			case OPEN_EXISTING:
				openOptions.add(StandardOpenOption.READ);
				break;
			case OPEN_ALWAYS:
				openOptions.add(StandardOpenOption.CREATE);
				if (!fileExists) openOptions.add(StandardOpenOption.WRITE);
				break;
			case TRUNCATE_EXISTING:
				openOptions.add(StandardOpenOption.TRUNCATE_EXISTING);
				break;
			default:
				throw new IllegalStateException("Unknown createDispostion attribute: " + creationDisposition.name());

		}
		return openOptions;
	}

	public static Optional<FileTime> toFileTime(WinBase.FILETIME windowsTime) {
		Instant instant = windowsTime.toDate().toInstant();
		if (instant.equals(WINDOWS_EPOCH_START)) {
			return Optional.empty();
		} else {
			return Optional.of(FileTime.from(instant));
		}
	}

	/**
	 * TODO
	 *
	 * @param fileAccess
	 * @return
	 */
	public static int mapFileGenericAccessToGenericAccess(int fileAccess) {

		boolean genericRead = false, genericWrite = false, genericExecute = false, genericAll = false;
		int outDesiredAccess = fileAccess;

		if ((outDesiredAccess & WinNT.FILE_GENERIC_READ) == WinNT.FILE_GENERIC_READ) {
			outDesiredAccess |= WinNT.GENERIC_READ;
			genericRead = true;
		}
		if ((outDesiredAccess & WinNT.FILE_GENERIC_WRITE) == WinNT.FILE_GENERIC_WRITE) {
			outDesiredAccess |= WinNT.GENERIC_WRITE;
			genericWrite = true;
		}
		if ((outDesiredAccess & WinNT.FILE_GENERIC_EXECUTE) == WinNT.FILE_GENERIC_EXECUTE) {
			outDesiredAccess |= WinNT.GENERIC_EXECUTE;
			genericExecute = true;
		}
		if ((outDesiredAccess & WinNT.FILE_ALL_ACCESS) == WinNT.FILE_ALL_ACCESS) {
			outDesiredAccess |= WinNT.GENERIC_ALL;
			genericAll = true;
		}

		if (genericRead)
			outDesiredAccess &= ~WinNT.FILE_GENERIC_READ;
		if (genericWrite)
			outDesiredAccess &= ~WinNT.FILE_GENERIC_WRITE;
		if (genericExecute)
			outDesiredAccess &= ~WinNT.FILE_GENERIC_EXECUTE;
		if (genericAll)
			outDesiredAccess &= ~WinNT.FILE_ALL_ACCESS;

		return outDesiredAccess;
	}

	/**
	 * Converts the kernel file creation flags to the win32 flags. Copied from dokan.c
	 *
	 * @param createDisposition
	 * @return integer corresponding to an enum in {@link CreationDisposition}
	 */
	public static int convertCreateDispositionToCreationDispostion(int createDisposition) {
		switch (createDisposition) {
			case FILE_CREATE:
				return CREATE_NEW.getMask();
			case FILE_OPEN:
				return OPEN_EXISTING.getMask();
			case FILE_OPEN_IF:
				return OPEN_ALWAYS.getMask();
			case FILE_OVERWRITE:
				return TRUNCATE_EXISTING.getMask();
			case FILE_SUPERSEDE:
				// The documentation isn't clear on the difference between replacing a file
				// and truncating it.
				// For now we just map it to create/truncate
			case FILE_OVERWRITE_IF:
				return CREATE_ALWAYS.getMask();
			default:
				//TODO: maybe throw an exception
				return 0;
		}
	}
}
