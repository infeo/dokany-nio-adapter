package org.cryptomator.frontend.dokany;

import com.google.common.base.CharMatcher;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import dev.dokan.dokan_java.DokanyFileSystemStub;
import dev.dokan.dokan_java.DokanyOperations;
import dev.dokan.dokan_java.DokanyUtils;
import dev.dokan.dokan_java.FileSystemInformation;
import dev.dokan.dokan_java.constants.microsoft.CreateOption;
import dev.dokan.dokan_java.constants.microsoft.CreateOptions;
import dev.dokan.dokan_java.constants.microsoft.CreationDisposition;
import dev.dokan.dokan_java.constants.microsoft.FileAccessMask;
import dev.dokan.dokan_java.constants.microsoft.FileAttribute;
import dev.dokan.dokan_java.constants.microsoft.Win32ErrorCodes;
import dev.dokan.dokan_java.structure.ByHandleFileInformation;
import dev.dokan.dokan_java.structure.DokanFileInfo;
import dev.dokan.dokan_java.structure.EnumIntegerSet;
import org.cryptomator.frontend.dokany.locks.DataLock;
import org.cryptomator.frontend.dokany.locks.LockManager;
import org.cryptomator.frontend.dokany.locks.PathLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static dev.dokan.dokan_java.constants.microsoft.Win32ErrorCodes.ERROR_ACCESS_DENIED;
import static dev.dokan.dokan_java.constants.microsoft.Win32ErrorCodes.ERROR_ALREADY_EXISTS;
import static dev.dokan.dokan_java.constants.microsoft.Win32ErrorCodes.ERROR_BUSY;
import static dev.dokan.dokan_java.constants.microsoft.Win32ErrorCodes.ERROR_CANNOT_MAKE;
import static dev.dokan.dokan_java.constants.microsoft.Win32ErrorCodes.ERROR_CANT_ACCESS_FILE;
import static dev.dokan.dokan_java.constants.microsoft.Win32ErrorCodes.ERROR_CURRENT_DIRECTORY;
import static dev.dokan.dokan_java.constants.microsoft.Win32ErrorCodes.ERROR_DIR_NOT_EMPTY;
import static dev.dokan.dokan_java.constants.microsoft.Win32ErrorCodes.ERROR_FILE_CORRUPT;
import static dev.dokan.dokan_java.constants.microsoft.Win32ErrorCodes.ERROR_FILE_EXISTS;
import static dev.dokan.dokan_java.constants.microsoft.Win32ErrorCodes.ERROR_FILE_NOT_FOUND;
import static dev.dokan.dokan_java.constants.microsoft.Win32ErrorCodes.ERROR_FILE_READ_ONLY;
import static dev.dokan.dokan_java.constants.microsoft.Win32ErrorCodes.ERROR_GEN_FAILURE;
import static dev.dokan.dokan_java.constants.microsoft.Win32ErrorCodes.ERROR_INVALID_DATA;
import static dev.dokan.dokan_java.constants.microsoft.Win32ErrorCodes.ERROR_INVALID_HANDLE;
import static dev.dokan.dokan_java.constants.microsoft.Win32ErrorCodes.ERROR_IO_DEVICE;
import static dev.dokan.dokan_java.constants.microsoft.Win32ErrorCodes.ERROR_OPEN_FAILED;
import static dev.dokan.dokan_java.constants.microsoft.Win32ErrorCodes.ERROR_READ_FAULT;
import static dev.dokan.dokan_java.constants.microsoft.Win32ErrorCodes.ERROR_SUCCESS;
import static dev.dokan.dokan_java.constants.microsoft.Win32ErrorCodes.ERROR_WRITE_FAULT;

/**
 * TODO: Beware of DokanyUtils.enumSetFromInt()!!!
 */
public class ReadWriteAdapter extends DokanyFileSystemStub {

	private static final Logger LOG = LoggerFactory.getLogger(ReadWriteAdapter.class);

	private final Path root;
	private final LockManager lockManager;
	private final CompletableFuture didMount;
	private final OpenHandleFactory fac;
	private final FileStore fileStore;

	public ReadWriteAdapter(Path root, LockManager lockManager, CompletableFuture<?> didMount, FileSystemInformation fsInfo) {
		super(fsInfo, false);
		this.root = root;
		this.lockManager = lockManager;
		this.didMount = didMount;
		this.fac = new OpenHandleFactory();
		try {
			this.fileStore = Files.getFileStore(root);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public int zwCreateFile(WString rawPath, WinBase.SECURITY_ATTRIBUTES securityContext, int rawDesiredAccess, int rawFileAttributes, int rawShareAccess, int rawCreateDisposition, int rawCreateOptions, DokanFileInfo dokanFileInfo) {
		Path path;
		try {
			path = getRootedPath(rawPath);
		} catch (InvalidPathException e) {
			return Win32ErrorCodes.ERROR_BAD_PATHNAME;
		}
		CreationDisposition creationDisposition = CreationDisposition.fromInt(rawCreateDisposition);
		LOG.trace("zwCreateFile() is called for {} with CreationDisposition {}.", path, creationDisposition.name());

		Optional<DosFileAttributes> attr;
		try {
			attr = Optional.of(Files.readAttributes(path, DosFileAttributes.class, LinkOption.NOFOLLOW_LINKS));
		} catch (NoSuchFileException e) {
			attr = Optional.empty();
		} catch (IOException e) {
			return ERROR_IO_DEVICE;
		}

		//is the file a directory and if yes, indicated as one?
		if (attr.isPresent() && attr.get().isDirectory()) {
			if ((rawCreateOptions & CreateOptions.FILE_NON_DIRECTORY_FILE) == 0) {
				dokanFileInfo.IsDirectory = 0x01;
				//TODO: set the share access like in the dokany mirror example
			} else {
				LOG.debug("Ressource {} is a Directory and cannot be opened as a file.", path);
				//TODO: which win32 error code should be returned? NTSTATUS is FILE_IS_NOT_DIRECTORY, here we use a cheat in the dokan-java project!
				return ERROR_INVALID_DATA;
			}
		} else if (attr.isPresent() && !attr.get().isRegularFile()) {
			return ERROR_CANT_ACCESS_FILE; // or ERROR_OPEN_FAILED or ERROR_CALL_NOT_IMPLEMENTED?
		}

		try (PathLock pathLock = lockManager.createPathLock(path.toString()).forWriting();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			if (dokanFileInfo.isDirectory()) {
				return createDirectory(path, creationDisposition, rawFileAttributes, dokanFileInfo);
			} else {
				EnumIntegerSet<CreateOption> createOptions = EnumIntegerSet.enumSetFromInt(rawCreateOptions, CreateOption.values());
				EnumIntegerSet<FileAccessMask> fileAccessMasks = EnumIntegerSet.enumSetFromInt(rawDesiredAccess, FileAccessMask.values());
				EnumIntegerSet<FileAttribute> fileAttributes = EnumIntegerSet.enumSetFromInt(rawFileAttributes, FileAttribute.values());
				Set<OpenOption> openOptions = FileUtil.buildOpenOptions(rawDesiredAccess, fileAttributes, createOptions, creationDisposition, dokanFileInfo.writeToEndOfFile(), attr.isPresent());
				return createFile(path, attr, creationDisposition, openOptions, rawFileAttributes, dokanFileInfo);
			}
		}
	}

	/**
	 * @return
	 */
	private int createDirectory(Path path, CreationDisposition creationDisposition, int rawFileAttributes, DokanFileInfo dokanyFileInfo) {
		LOG.trace("Try to open {} as Directory.", path);
		final int mask = creationDisposition.getMask();
		//createDirectory request
		if (mask == CreationDisposition.CREATE_NEW.getMask() || mask == CreationDisposition.OPEN_ALWAYS.getMask()) {
			// TODO: rename current method "createDirectory" to "openDirectory" and extract following part to new private method "createDirectory"
			try {
				Files.createDirectory(path);
				LOG.trace("Directory {} successful created ", path);
			} catch (FileAlreadyExistsException e) {
				if (mask == CreationDisposition.CREATE_NEW.getMask()) {
					//we got create_new flag -> there should be nuthing, but there is somthin!
					LOG.trace("Directory {} already exists.", path);
					return ERROR_ALREADY_EXISTS;
				}
			} catch (IOException e) {
				//we dont know what the hell happened
				LOG.debug("zwCreateFile(): IO error occured during the creation of {}.", path);
				LOG.debug("zwCreateFile(): ", e);
				return ERROR_CANNOT_MAKE;
			}
		}

		//some otha flags were used
		//here we check if we try to open a file as a directory
		if (Files.isRegularFile(path)) {
			//sh*t
			LOG.trace("Attempt to open file {} as a directory.", path);
			return ERROR_ACCESS_DENIED;
		} else {
			// we open the directory in some kinda way
			try {
				setFileAttributes(path, rawFileAttributes);
				dokanyFileInfo.Context = fac.openDir(path);
				LOG.trace("({}) {} opened successful with handle {}.", dokanyFileInfo.Context, path, dokanyFileInfo.Context);
			} catch (NoSuchFileException e) {
				LOG.trace("{} not found.", path);
				return Win32ErrorCodes.ERROR_PATH_NOT_FOUND;
			} catch (IOException e) {
				LOG.debug("zwCreateFile(): IO error occurred during opening handle to {}.", path);
				LOG.debug("zwCreateFile(): ", e);
				return Win32ErrorCodes.ERROR_OPEN_FAILED;
			}
			return Win32ErrorCodes.ERROR_SUCCESS;
		}
	}

	private int createFile(Path path, Optional<DosFileAttributes> attr, CreationDisposition creationDisposition, Set<OpenOption> openOptions, int rawFileAttributes, DokanFileInfo dokanyFileInfo) {
		LOG.trace("Try to open {} as File.", path);
		final int mask = creationDisposition.getMask();
		//we want to create a file
		//system or hidden file?
		if (attr.isPresent()
				&&
				(mask == CreationDisposition.TRUNCATE_EXISTING.getMask() || mask == CreationDisposition.CREATE_ALWAYS.getMask())
				&&
				(
						((rawFileAttributes & FileAttribute.HIDDEN.getMask()) == 0 && attr.get().isHidden())
								||
								((rawFileAttributes & FileAttribute.SYSTEM.getMask()) == 0 && attr.get().isSystem())
				)
		) {
			//cannot overwrite hidden or system file
			LOG.trace("{} is hidden or system file. Unable to overwrite.", path);
			return ERROR_ACCESS_DENIED;
		}
		//read-only?
		else if ((attr.isPresent()  && attr.get().isReadOnly() || ((rawFileAttributes & FileAttribute.READONLY.getMask()) != 0))
				&& dokanyFileInfo.DeleteOnClose != 0
		) {
			//cannot overwrite file
			LOG.trace("{} is readonly. Unable to overwrite.", path);
			return ERROR_FILE_READ_ONLY;
		} else {
			try {
				dokanyFileInfo.Context = fac.openFile(path, openOptions);
				if (!attr.isPresent()  || mask == CreationDisposition.TRUNCATE_EXISTING.getMask() || mask == CreationDisposition.CREATE_ALWAYS.getMask()) {
					//according to zwCreateFile() documentation FileAttributes are ignored if no file is created or overwritten
					setFileAttributes(path, rawFileAttributes);
				}
				LOG.trace("({}) {} opened successful with handle {}.", dokanyFileInfo.Context, path, dokanyFileInfo.Context);
				//required by contract
				if (attr.isPresent()  && (mask == CreationDisposition.OPEN_ALWAYS.getMask() || mask == CreationDisposition.CREATE_ALWAYS.getMask())) {
					return ERROR_ALREADY_EXISTS;
				} else {
					return ERROR_SUCCESS;
				}
			} catch (FileAlreadyExistsException e) {
				LOG.trace("Unable to open {}.", path);
				return ERROR_FILE_EXISTS;
			} catch (NoSuchFileException e) {
				LOG.trace("{} not found.", path);
				return ERROR_FILE_NOT_FOUND;
			} catch (AccessDeniedException e) {
				LOG.trace("zwCreateFile(): Access to file {} was denied.", path);
				LOG.trace("Cause:", e);
				return ERROR_ACCESS_DENIED;
			} catch (IOException e) {
				if (attr.isPresent()) {
					LOG.debug("zwCreateFile(): IO error occurred during opening handle to {}.", path);
					LOG.debug("zwCreateFile(): ", e);
					return ERROR_OPEN_FAILED;
				} else {
					LOG.debug("zwCreateFile(): IO error occurred during creation of {}.", path);
					LOG.debug("zwCreateFile(): ", e);
					return ERROR_CANNOT_MAKE;
				}
			} catch (IllegalArgumentException e) {
				//special handling for cryptofs
				LOG.warn("createFile(): Exception occurred:", e);
				LOG.warn("{} seems to be modified on disk.", path);
				dokanyFileInfo.Context = fac.openRestrictedFile(path);
				LOG.warn("({}) {} opened in restricted mode with handle {}.", dokanyFileInfo.Context, path, dokanyFileInfo.Context);
				return ERROR_FILE_CORRUPT;
			}
		}
	}

	/**
	 * The handle is closed in this method, due to the requirements of the dokany implementation to delete a file in the cleanUp method
	 *
	 * @param rawPath
	 * @param dokanyFileInfo {@link DokanFileInfo} with information about the file or directory.
	 */
	@Override
	public void cleanup(WString rawPath, DokanFileInfo dokanyFileInfo) {
		Path path = getRootedPath(rawPath);
		LOG.trace("({}) cleanup() is called for {}.", dokanyFileInfo.Context, path);
		if (dokanyFileInfo.Context == 0) {
			LOG.debug("cleanup(): Invalid handle to {}.", path);
		} else {
			try {
				fac.close(dokanyFileInfo.Context);
				if (dokanyFileInfo.deleteOnClose()) {
					try (PathLock pathLock = lockManager.createPathLock(path.toString()).forWriting();
						 DataLock dataLock = pathLock.lockDataForWriting()) {
						Files.delete(path);
						LOG.trace("({}) {} successful deleted.", dokanyFileInfo.Context, path);
					} catch (DirectoryNotEmptyException e) {
						LOG.debug("({}) Directory {} not empty.", dokanyFileInfo.Context, path);
					} catch (IOException e) {
						LOG.debug("({}) cleanup(): IO error during deletion of {} ", dokanyFileInfo.Context, path, e);
						LOG.debug("cleanup(): ", e);
					}
				}
			} catch (IOException e) {
				LOG.debug("({}) cleanup(): Unable to close handle to {}", dokanyFileInfo.Context, path, e);
				LOG.debug("cleanup(): ", e);
			}
		}
	}

	@Override
	public void closeFile(WString rawPath, DokanFileInfo dokanyFileInfo) {
		Path path = getRootedPath(rawPath);
		LOG.trace("({}) closeFile() is called for {}.", dokanyFileInfo.Context, path);
		if (fac.exists(dokanyFileInfo.Context)) {
			LOG.debug("({}) Resource {} was not cleanuped. Closing handle now.", dokanyFileInfo.Context, path);
			try {
				fac.close(dokanyFileInfo.Context);
			} catch (IOException e) {
				LOG.warn("({}) closeFile(): Unable to close handle to resource {}. To close it please restart the adapter.", dokanyFileInfo.Context, path);
				LOG.warn("closeFile():", e);
			}
		}
		dokanyFileInfo.Context = 0;
	}

	@Override
	public int readFile(WString rawPath, Pointer rawBuffer, int rawBufferLength, IntByReference rawReadLength, long rawOffset, DokanFileInfo dokanyFileInfo) {
		Path path = getRootedPath(rawPath);
		LOG.trace("({}) readFile() is called for {}.", dokanyFileInfo.Context, path);
		if (dokanyFileInfo.Context == 0) {
			LOG.debug("readFile(): Invalid handle to {} ", path);
			return ERROR_INVALID_HANDLE;
		} else if (dokanyFileInfo.isDirectory()) {
			LOG.debug("({}) {} is a directory. Unable to read Data from it.", dokanyFileInfo.Context, path);
			return ERROR_ACCESS_DENIED;
		}

		long handleID = dokanyFileInfo.Context;
		boolean reopened = false;
		OpenFile handle = (OpenFile) fac.get(handleID);
		if (handle == null) {
			LOG.debug("({}) readFile(): Unable to find handle for {}. Try to reopen it.", handleID, path);
			try {
				handleID = fac.openFile(path, Collections.singleton(StandardOpenOption.READ));
				handle = (OpenFile) fac.get(handleID);
				LOG.trace("readFile(): Successful reopened {} with handle {}.", path, handleID);
				reopened = true;
			} catch (IOException e1) {
				LOG.debug("readFile(): Reopen of {} failed. Aborting.", path);
				return ERROR_OPEN_FAILED;
			}
		}

		try (PathLock pathLock = lockManager.createPathLock(path.toString()).forReading();
			 DataLock dataLock = pathLock.lockDataForReading()) {
			rawReadLength.setValue(handle.read(rawBuffer, rawBufferLength, rawOffset));
			LOG.trace("({}) Data successful read from {}.", handleID, path);
			return ERROR_SUCCESS;
		} catch (IOException e) {
			LOG.debug("({}) readFile(): IO error while reading file {}.", handleID, path);
			LOG.debug("Error is:", e);
			return ERROR_READ_FAULT;
		} finally {
			if (reopened) {
				try {
					handle.close();
					LOG.trace("({}) readFile(): Successful closed REOPENED file {}.", handleID, path);
				} catch (IOException e) {
					LOG.debug("({}) readFile(): IO error while closing REOPENED file {}. File will be closed on exit.", handleID, path);
				}
			}
		}
	}

	@Override
	public int writeFile(WString rawPath, Pointer rawBuffer, int rawNumberOfBytesToWrite, IntByReference rawNumberOfBytesWritten, long rawOffset, DokanFileInfo dokanyFileInfo) {
		Path path = getRootedPath(rawPath);
		LOG.trace("({}) writeFile() is called for {}.", dokanyFileInfo.Context, path);
		if (dokanyFileInfo.Context == 0) {
			LOG.debug("writeFile(): Invalid handle to {}", path);
			return ERROR_INVALID_HANDLE;
		} else if (dokanyFileInfo.isDirectory()) {
			LOG.debug("({}) {} is a directory. Unable to write Data to it.", dokanyFileInfo.Context, path);
			return ERROR_ACCESS_DENIED;
		}

		long handleID = dokanyFileInfo.Context;
		boolean reopened = false;
		OpenFile handle = (OpenFile) fac.get(handleID);
		if (handle == null) {
			LOG.debug("({}) writeFile(): Unable to find handle for {}. Try to reopen it.", handleID, path);
			try {
				handleID = fac.openFile(path, Collections.singleton(StandardOpenOption.WRITE));
				handle = (OpenFile) fac.get(handleID);
				LOG.trace("writeFile(): Successful reopened {} with handle {}.", path, handleID);
				reopened = true;
			} catch (IOException e1) {
				LOG.debug("writeFile(): Reopen of {} failed. Aborting.", path);
				return ERROR_OPEN_FAILED;
			}
		}

		try (PathLock pathLock = lockManager.createPathLock(path.toString()).forReading();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			rawNumberOfBytesWritten.setValue(handle.write(rawBuffer, rawNumberOfBytesToWrite, rawOffset));
			LOG.trace("({}) Data successful written to {}.", handleID, path);
			return ERROR_SUCCESS;
		} catch (IOException e) {
			LOG.debug("({}) writeFile(): IO Error while writing to {} ", handleID, path, e);
			return ERROR_WRITE_FAULT;
		} finally {
			if (reopened) {
				try {
					handle.close();
					LOG.trace("({}) writeFile(): Successful closed REOPENED file {}.", handleID, path);
				} catch (IOException e) {
					LOG.debug("({}) writeFile(): IO error while closing REOPENED file {}. File will be closed on exit.", handleID, path);
				}
			}
		}
	}

	@Override
	public int flushFileBuffers(WString rawPath, DokanFileInfo dokanyFileInfo) {
		Path path = getRootedPath(rawPath);
		LOG.trace("({}) flushFileBuffers() is called for {}.", dokanyFileInfo.Context, path);
		if (dokanyFileInfo.Context == 0) {
			LOG.debug("flushFileBuffers(): Invalid handle to {}.", path);
			return ERROR_INVALID_HANDLE;
		} else if (dokanyFileInfo.isDirectory()) {
			LOG.debug("({}) {} is a directory. Unable to write data to it.", dokanyFileInfo.Context, path);
			return ERROR_ACCESS_DENIED;
		} else {
			OpenHandle handle = fac.get(dokanyFileInfo.Context);
			try {
				((OpenFile) handle).flush();
				LOG.trace("Flushed successful to {} with handle {}.", path, dokanyFileInfo.Context);
				return ERROR_SUCCESS;
			} catch (IOException e) {
				LOG.debug("({}) flushFileBuffers(): IO Error while flushing to {}.", dokanyFileInfo.Context, path, e);
				LOG.debug("flushFileBuffers(): ", e);
				return ERROR_WRITE_FAULT;
			}
		}
	}

	/**
	 * @param fileName
	 * @param handleFileInfo
	 * @param dokanyFileInfo {@link DokanFileInfo} with information about the file or directory.
	 * @return
	 */
	@Override
	public int getFileInformation(WString fileName, ByHandleFileInformation handleFileInfo, DokanFileInfo dokanyFileInfo) {
		Path path = getRootedPath(fileName);
		LOG.trace("({}) getFileInformation() is called for {}.", dokanyFileInfo.Context, path);
		if (dokanyFileInfo.Context == 0) {
			LOG.debug("getFileInformation(): Invalid handle to {}.", path);
			return ERROR_INVALID_HANDLE;
		} else {
			try (PathLock pathLock = lockManager.createPathLock(path.toString()).forReading();
				 DataLock dataLock = pathLock.lockDataForReading()) {
				DosFileAttributes attr = Files.readAttributes(path, DosFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
				getFileInformation(path).copyTo(handleFileInfo);
				LOG.trace("({}) File Information successful read from {}.", dokanyFileInfo.Context, path);
				return ERROR_SUCCESS;
			} catch (NoSuchFileException e) {
				LOG.debug("({}) Resource {} not found.", dokanyFileInfo.Context, path);
				return ERROR_FILE_NOT_FOUND;
			} catch (IOException e) {
				LOG.debug("({}) getFileInformation(): IO error occurred reading meta data from {}.", dokanyFileInfo.Context, path);
				LOG.debug("getFileInformation(): ", e);
				return ERROR_READ_FAULT;
			}
		}
	}

	/**
	 * TODO: move this into FileUtil class
	 *
	 * @param p
	 * @return
	 * @throws IOException
	 */
	private ByHandleFileInformation getFileInformation(Path p) throws IOException {
		DosFileAttributes attr = Files.readAttributes(p, DosFileAttributes.class);
		long index = 0;
		if (attr.fileKey() != null) {
			index = (long) attr.fileKey();
		}
		int fileAttr = 0;
		fileAttr |= attr.isArchive() ? WinNT.FILE_ATTRIBUTE_ARCHIVE : 0;
		fileAttr |= attr.isSystem() ? WinNT.FILE_ATTRIBUTE_SYSTEM : 0;
		fileAttr |= attr.isHidden() ? WinNT.FILE_ATTRIBUTE_HIDDEN : 0;
		fileAttr |= attr.isReadOnly() ? WinNT.FILE_ATTRIBUTE_READONLY : 0;
		fileAttr |= attr.isDirectory() ? WinNT.FILE_ATTRIBUTE_DIRECTORY : 0;
		fileAttr |= attr.isSymbolicLink() ? WinNT.FILE_ATTRIBUTE_REPARSE_POINT : 0;

		if (fileAttr == 0) {
			fileAttr |= WinNT.FILE_ATTRIBUTE_NORMAL;
		}

		return new ByHandleFileInformation(p.getFileName(), fileAttr, attr.creationTime(), attr.lastAccessTime(), attr.lastModifiedTime(), this.volumeSerialnumber, attr.size(), index);
	}

	@Override
	public int findFiles(WString rawPath, DokanyOperations.FillWin32FindData rawFillFindData, DokanFileInfo dokanyFileInfo) {
		Path path = getRootedPath(rawPath);
		assert path.isAbsolute();
		LOG.trace("({}) findFiles() is called for {}.", dokanyFileInfo.Context, path);
		if (dokanyFileInfo.Context == 0) {
			LOG.debug("findFiles(): Invalid handle to {}.", path);
			return Win32ErrorCodes.ERROR_INVALID_HANDLE;
		} else {
			try (PathLock pathLock = lockManager.createPathLock(path.toString()).forReading();
				 DataLock dataLock = pathLock.lockDataForReading();
				 DirectoryStream<Path> ds = Files.newDirectoryStream(path)) {
				Spliterator<Path> spliterator = Spliterators.spliteratorUnknownSize(ds.iterator(), Spliterator.DISTINCT);
				Stream<Path> stream = StreamSupport.stream(spliterator, false);
				stream.map(p -> {
					assert p.isAbsolute();
					try {
						//DosFileAttributes attr = Files.readAttributes(p, DosFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
						//if (attr.isDirectory() || attr.isRegularFile()) {
						return getFileInformation(p).toWin32FindData();
						//} else {
						//LOG.warn("({}) findFilesWithPattern(): Found node that is neither directory nor file: {}. Will be ignored in file listing.", dokanyFileInfo.Context, p);
						//return null;
						//}
					} catch (IOException e) {
						LOG.debug("({}) findFiles(): IO error accessing {}. Will be ignored in file listing.", dokanyFileInfo.Context, p);
						return null;
					}
				})
						.filter(Objects::nonNull)
						.forEach(file -> {
							assert file != null;
							LOG.trace("({}) findFiles(): found file {}", dokanyFileInfo.Context, file.getFileName());
							rawFillFindData.fillWin32FindData(file, dokanyFileInfo);
						});
				LOG.trace("({}) Successful searched content in {}.", dokanyFileInfo.Context, path);
				return Win32ErrorCodes.ERROR_SUCCESS;
			} catch (IOException e) {
				LOG.error("({}) findFiles(): Unable to list content of directory {}.", dokanyFileInfo.Context, path);
				LOG.error("(" + dokanyFileInfo.Context + ") findFiles(): Message and Stacktrace.", e);
				return Win32ErrorCodes.ERROR_READ_FAULT;
			}
		}
	}


	@Override
	public int setFileAttributes(WString rawPath, int rawAttributes, DokanFileInfo dokanyFileInfo) {
		Path path = getRootedPath(rawPath);
		LOG.trace("({}) setFileAttributes() is called for {}.", dokanyFileInfo.Context, path);
		if (dokanyFileInfo.Context == 0) {
			LOG.debug("setFileAttribute(): Invalid handle to {}.", path);
			return ERROR_INVALID_HANDLE;
		} else {
			try (PathLock pathLock = lockManager.createPathLock(path.toString()).forReading();
				 DataLock dataLock = pathLock.lockDataForWriting()) {
				try {
					setFileAttributes(path, rawAttributes);
					return ERROR_SUCCESS;
				} catch (NoSuchFileException e) {
					return ERROR_FILE_NOT_FOUND;
				} catch (IOException e) {
					LOG.trace("setFileAttributes(): Failed for file {} due to IOException.", path);
					LOG.trace("setFileAttributes():Cause", e);
					return ERROR_WRITE_FAULT;
				}
			}
		}
	}

	//TODO: refactor the method to gain speed by avoiding the iteration with some set
	private void setFileAttributes(Path path, int rawAttributes) throws IOException {
		DosFileAttributeView attrView = Files.getFileAttributeView(path, DosFileAttributeView.class);
		EnumIntegerSet<FileAttribute> attrsToUnset = EnumIntegerSet.enumSetFromInt(Integer.MAX_VALUE, FileUtil.supportedAttributeValuesToSet);
		EnumIntegerSet<FileAttribute> attrsToSet = EnumIntegerSet.enumSetFromInt(rawAttributes, FileAttribute.values());
		// if (rawAttributes == 0) {
		// MS-FSCC 2.6 File Attributes : There is no file attribute with the value 0x00000000
		// because a value of 0x00000000 in the FileAttributes field means that the file attributes for this file MUST NOT be changed when setting basic information for the file
		// do nuthin'
		if ((rawAttributes & FileAttribute.NORMAL.getMask()) != 0 && (rawAttributes - FileAttribute.NORMAL.getMask() == 0)) {
			//contains only the NORMAL attribute
			//removes all removable fields
			for (FileAttribute attr : attrsToUnset) {
				FileUtil.setAttribute(attrView, attr, false);
			}
		} else {
			attrsToSet.remove(FileAttribute.NORMAL);
			for (FileAttribute attr : attrsToSet) {
				FileUtil.setAttribute(attrView, attr, true);
				attrsToUnset.remove(attr);
			}

			for (FileAttribute attr : attrsToUnset) {
				FileUtil.setAttribute(attrView, attr, false);
			}

		}
	}

	@Override
	public int setFileTime(WString rawPath, WinBase.FILETIME rawCreationTime, WinBase.FILETIME rawLastAccessTime, WinBase.FILETIME rawLastWriteTime, DokanFileInfo dokanyFileInfo) {
		Path path = getRootedPath(rawPath);
		LOG.trace("({}) setFileTime() is called for {}.", dokanyFileInfo.Context, path);
		if (dokanyFileInfo.Context == 0) {
			LOG.debug("setFileTime(): Invalid handle to {}.", path);
			return ERROR_INVALID_HANDLE;
		} else {
			try (PathLock pathLock = lockManager.createPathLock(path.toString()).forReading();
				 DataLock dataLock = pathLock.lockDataForWriting()) {
				FileTime lastModifiedTime = FileTime.fromMillis(rawLastWriteTime.toDate().getTime());
				FileTime lastAccessTime = FileTime.fromMillis(rawLastAccessTime.toDate().getTime());
				FileTime createdTime = FileTime.fromMillis(rawCreationTime.toDate().getTime());
				Files.getFileAttributeView(path, BasicFileAttributeView.class).setTimes(lastModifiedTime, lastAccessTime, createdTime);
				LOG.trace("({}) Successful updated Filetime for {}.", dokanyFileInfo.Context, path);
				return ERROR_SUCCESS;
			} catch (NoSuchFileException e) {
				LOG.debug("({}) File {} not found.", dokanyFileInfo.Context, path);
				return ERROR_FILE_NOT_FOUND;
			} catch (IOException e) {
				LOG.debug("({}) setFileTime(): IO error occurred accessing {}.", dokanyFileInfo.Context, path);
				LOG.debug("setFileTime(): ", e);
				return ERROR_WRITE_FAULT;
			}
		}
	}

	@Override
	public int deleteFile(WString rawPath, DokanFileInfo dokanyFileInfo) {
		Path path = getRootedPath(rawPath);
		LOG.trace("({}) deleteFile() is called for {}.", dokanyFileInfo.Context, path);
		if (dokanyFileInfo.Context == 0) {
			LOG.debug("deleteFile(): Invalid handle to {}.", path);
			return ERROR_INVALID_HANDLE;
		} else if (dokanyFileInfo.isDirectory()) {
			LOG.debug("({}) {} is a directory. Unable to delete via deleteFile()", dokanyFileInfo.Context, path);
			return ERROR_ACCESS_DENIED;
		} else {
			try (PathLock pathLock = lockManager.createPathLock(path.toString()).forWriting();
				 DataLock dataLock = pathLock.lockDataForWriting()) {
				//TODO: race condition with handle == null possible?
				OpenHandle handle = fac.get(dokanyFileInfo.Context);
				if (Files.exists(path)) {
					//TODO: what is the best condition for the deletion? and is this case analysis correct?
					if (((OpenFile) handle).canBeDeleted()) {
						LOG.trace("({}) Deletion of {} possible.", dokanyFileInfo.Context, path);
						return ERROR_SUCCESS;
					} else {
						LOG.trace("({}) Deletion of {} not possible.", dokanyFileInfo.Context, path);
						return ERROR_BUSY;
					}
				} else {
					LOG.debug("({}) deleteFile(): {} not found.", dokanyFileInfo.Context, path);
					return ERROR_FILE_NOT_FOUND;
				}
			}
		}
	}

	@Override
	public int deleteDirectory(WString rawPath, DokanFileInfo dokanyFileInfo) {
		Path path = getRootedPath(rawPath);
		LOG.trace("({}) deleteDirectory() is called for {}.", dokanyFileInfo.Context, path);
		if (dokanyFileInfo.Context == 0) {
			LOG.debug("deleteDirectory(): Invalid handle to {}.", path);
			return ERROR_INVALID_HANDLE;
		} else if (!dokanyFileInfo.isDirectory()) {
			LOG.debug("({}) {} is a file. Unable to delete via deleteDirectory()", dokanyFileInfo.Context, path);
			return ERROR_ACCESS_DENIED;
		} else {
			try (PathLock pathLock = lockManager.createPathLock(path.toString()).forWriting();
				 DataLock dataLock = pathLock.lockDataForWriting()) {
				//TODO: check for directory existence
				//TODO: race condition with handle == null possible?
				try (DirectoryStream emptyCheck = Files.newDirectoryStream(path)) {
					if (emptyCheck.iterator().hasNext()) {
						LOG.trace("({}) Deletion of {} not possible.", dokanyFileInfo.Context, path);
						return ERROR_DIR_NOT_EMPTY;
					} else {
						LOG.trace("({}) Deletion of {} possible.", dokanyFileInfo.Context, path);
						return ERROR_SUCCESS;
					}
				} catch (IOException e) {
					LOG.debug("({}) deleteDirectory(): IO error occurred reading {}.", dokanyFileInfo.Context, path);
					LOG.debug("deleteDirectory(): ", e);
					return ERROR_CURRENT_DIRECTORY;
				}
			}
		}
	}

	@Override
	public int moveFile(WString rawPath, WString rawNewFileName, boolean rawReplaceIfExisting, DokanFileInfo dokanyFileInfo) {
		Path path = getRootedPath(rawPath);
		Path newPath = getRootedPath(rawNewFileName);
		LOG.trace("({}) moveFile() is called for {}.", dokanyFileInfo.Context, path);
		if (dokanyFileInfo.Context == 0) {
			LOG.debug("moveFile(): Invalid handle to {}.", path);
			return ERROR_INVALID_HANDLE;
		} else {
			try (PathLock oldPathLock = lockManager.createPathLock(path.toString()).forWriting();
				 DataLock oldDataLock = oldPathLock.lockDataForWriting();
				 PathLock newPathLock = lockManager.createPathLock(newPath.toString()).forWriting();
				 DataLock newDataLock = newPathLock.lockDataForWriting()) {
				if (rawReplaceIfExisting) {
					Files.move(path, newPath, StandardCopyOption.REPLACE_EXISTING);
				} else {
					Files.move(path, newPath);
				}
				LOG.trace("({}) Successful moved resource {} to {}.", dokanyFileInfo.Context, path, newPath);
				return ERROR_SUCCESS;
			} catch (FileAlreadyExistsException e) {
				LOG.trace("({}) Ressource {} already exists at {}.", dokanyFileInfo.Context, path, newPath);
				return ERROR_FILE_EXISTS;
			} catch (DirectoryNotEmptyException e) {
				LOG.trace("({}) Target directoy {} is not emtpy.", dokanyFileInfo.Context, path);
				return ERROR_DIR_NOT_EMPTY;
			} catch (IOException e) {
				LOG.debug("({}) moveFile(): IO error occured while moving ressource {}.", dokanyFileInfo.Context, path);
				LOG.debug("moveFile(): ", e);
				return ERROR_GEN_FAILURE;
			}
		}
	}

	@Override
	public int setEndOfFile(WString rawPath, long rawByteOffset, DokanFileInfo dokanyFileInfo) {
		Path path = getRootedPath(rawPath);
		LOG.trace("({}) setEndOfFile() is called for {}.", dokanyFileInfo.Context, path);
		if (dokanyFileInfo.Context == 0) {
			LOG.debug("setEndOfFile(): Invalid handle to {}.", path);
			return ERROR_INVALID_HANDLE;
		} else if (dokanyFileInfo.isDirectory()) {
			LOG.debug("({}) setEndOfFile(): {} is a directory. Unable to truncate.", dokanyFileInfo.Context, path);
			return ERROR_ACCESS_DENIED;
		} else {
			try (PathLock pathLock = lockManager.createPathLock(path.toString()).forReading();
				 DataLock dataLock = pathLock.lockDataForWriting()) {
				OpenHandle handle = fac.get(dokanyFileInfo.Context);
				((OpenFile) handle).truncate(rawByteOffset);
				LOG.trace("({}) Successful truncated {} to size {}.", dokanyFileInfo.Context, path, rawByteOffset);
				return ERROR_SUCCESS;
			} catch (IOException e) {
				LOG.debug("({}) setEndOfFile(): IO error while truncating {}.", dokanyFileInfo.Context, path);
				LOG.debug("setEndOfFile(): ", e);
				return ERROR_WRITE_FAULT;
			}
		}
	}

	@Override
	public int setAllocationSize(WString rawPath, long rawLength, DokanFileInfo dokanyFileInfo) {
		Path path = getRootedPath(rawPath);
		LOG.trace("({}) setAllocationSize() is called for {}.", dokanyFileInfo.Context, path);
		return setEndOfFile(rawPath, rawLength, dokanyFileInfo);
	}

	@Override
	public int getDiskFreeSpace(LongByReference freeBytesAvailable, LongByReference totalNumberOfBytes, LongByReference totalNumberOfFreeBytes, DokanFileInfo dokanyFileInfo) {
		LOG.trace("getFreeDiskSpace() is called.");
		try {
			totalNumberOfBytes.setValue(fileStore.getTotalSpace());
			freeBytesAvailable.setValue(fileStore.getUsableSpace());
			totalNumberOfFreeBytes.setValue(fileStore.getUnallocatedSpace());
			return ERROR_SUCCESS;
		} catch (IOException e) {
			LOG.debug("({}) getFreeDiskSpace(): Unable to detect disk space status.", dokanyFileInfo.Context, e);
			return ERROR_READ_FAULT;
		}
	}

	/**
	 * TODO: this method is copy pasta. Check it!
	 *
	 * @param rawVolumeNameBuffer
	 * @param rawVolumeNameSize
	 * @param rawVolumeSerialNumber
	 * @param rawMaximumComponentLength
	 * @param rawFileSystemFlags
	 * @param rawFileSystemNameBuffer
	 * @param rawFileSystemNameSize
	 * @param dokanyFileInfo {@link DokanFileInfo} with information about the file or directory.
	 * @return
	 */
	@Override
	public int getVolumeInformation(Pointer rawVolumeNameBuffer, int rawVolumeNameSize, IntByReference rawVolumeSerialNumber, IntByReference rawMaximumComponentLength, IntByReference rawFileSystemFlags, Pointer rawFileSystemNameBuffer, int rawFileSystemNameSize, DokanFileInfo dokanyFileInfo) {
		rawVolumeNameBuffer.setWideString(0L, DokanyUtils.trimStrToSize(this.volumeName, rawVolumeNameSize));
		rawVolumeSerialNumber.setValue(this.volumeSerialnumber);
		rawMaximumComponentLength.setValue(this.fileSystemInformation.getMaxComponentLength());
		rawFileSystemFlags.setValue(this.fileSystemInformation.getFileSystemFeatures().toInt());
		rawFileSystemNameBuffer.setWideString(0L, DokanyUtils.trimStrToSize(this.fileSystemInformation.getFileSystemName(), rawFileSystemNameSize));
		return Win32ErrorCodes.ERROR_SUCCESS;
	}

	@Override
	public int mounted(DokanFileInfo dokanyFileInfo) {
		LOG.trace("mounted() is called.");
		didMount.complete(null);
		return 0;
	}

	@Override
	public int unmounted(DokanFileInfo dokanyFileInfo) {
		LOG.trace("unmounted() is called.");
		return 0;
	}

	private Path getRootedPath(WString rawPath) {
		String unixPath = rawPath.toString().replace('\\', '/');
		String relativeUnixPath = CharMatcher.is('/').trimLeadingFrom(unixPath);
		assert root.isAbsolute();
		return root.resolve(relativeUnixPath);
	}

}
