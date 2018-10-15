package com.dokany.java;


import com.dokany.java.migrated.structure.ByHandleFileInformation;
import com.dokany.java.migrated.structure.DokanFileInfo;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;

/**
 * Implementation of {@link com.dokany.java.DokanyOperations} which connects to {@link DokanyFileSystem_OLD}.
 */
final class DokanyOperationsProxy extends com.dokany.java.DokanyOperations {

	private final DokanyFileSystem_OLD fileSystem;

	DokanyOperationsProxy(final DokanyFileSystem_OLD fileSystem) {
		this.fileSystem = fileSystem;
		super.ZwCreateFile = new ZwCreateFileProxy();
		super.CloseFile = fileSystem::closeFile;
		super.Cleanup = fileSystem::cleanup;
		super.ReadFile = new ReadFileProxy();
		super.WriteFile = new WriteFileProxy();
		super.FlushFileBuffers = new FlushFileBuffersProxy();
		super.GetFileInformation = new GetFileInformationProxy();
		super.GetVolumeInformation = new GetVolumeInformationProxy();
		super.GetDiskFreeSpace = new GetDiskFreeSpaceProxy();
		super.FindFiles = new FindFilesProxy();
		super.FindFilesWithPattern = new FindFilesWithPatternProxy();
		super.SetFileAttributes = new SetFileAttributesProxy();
		super.SetFileTime = new SetFileTimeProxy();
		super.DeleteFile = new DeleteFileProxy();
		super.DeleteDirectory = new DeleteDirectoryProxy();
		super.MoveFile = new MoveFileProxy();
		super.SetEndOfFile = new SetEndOfFileProxy();
		super.SetAllocationSize = new SetAllocationSizeProxy();
		super.LockFile = null;
		super.UnlockFile = null;
		super.Mounted = new MountedProxy();
		super.Unmounted = new UnmountedProxy();
		super.GetFileSecurity = null;
		super.SetFileSecurity = null;
		super.FindStreams = null;
	}

	class ZwCreateFileProxy implements ZwCreateFile {

		@Override
		public long callback(WString rawPath, WinBase.SECURITY_ATTRIBUTES securityContext, int rawDesiredAccess, int rawFileAttributes, int rawShareAccess, int rawCreateDisposition, int rawCreateOptions, DokanFileInfo dokanFileInfo) {
			IntByReference createDisposition = new IntByReference();
			IntByReference desiredAccess = new IntByReference();
			IntByReference fileAttributeFlags = new IntByReference();
			NativeMethods.DokanMapKernelToUserCreateFileFlags(rawDesiredAccess, rawFileAttributes, rawCreateOptions, rawCreateDisposition, desiredAccess, fileAttributeFlags, createDisposition);
			return NativeMethods.DokanNtStatusFromWin32(fileSystem.zwCreateFile(rawPath, securityContext, desiredAccess.getValue(), fileAttributeFlags.getValue(), rawShareAccess, createDisposition.getValue(), rawCreateOptions, dokanFileInfo));
		}
	}

	/**
	 * CloseFileProxy is not needed, because its callback return type is void.
	 */

	/**
	 * CleanupProxy is not needed, because its callback return type is void.
	 */

	class ReadFileProxy implements ReadFile {

		@Override
		public long callback(WString rawPath, Pointer rawBuffer, int rawBufferLength, IntByReference rawReadLength, long rawOffset, DokanFileInfo dokanFileInfo) {
			return NativeMethods.DokanNtStatusFromWin32(fileSystem.readFile(rawPath, rawBuffer, rawBufferLength, rawReadLength, rawOffset, dokanFileInfo));
		}
	}

	class WriteFileProxy implements WriteFile {

		@Override
		public long callback(WString rawPath, Pointer rawBuffer, int rawNumberOfBytesToWrite, IntByReference rawNumberOfBytesWritten, long rawOffset, DokanFileInfo dokanFileInfo) {
			return NativeMethods.DokanNtStatusFromWin32(fileSystem.writeFile(rawPath, rawBuffer, rawNumberOfBytesToWrite, rawNumberOfBytesWritten, rawOffset, dokanFileInfo));
		}
	}

	class FlushFileBuffersProxy implements FlushFileBuffers {

		@Override
		public long callback(WString rawPath, DokanFileInfo dokanFileInfo) {
			return NativeMethods.DokanNtStatusFromWin32(fileSystem.flushFileBuffers(rawPath, dokanFileInfo));
		}
	}

	class GetFileInformationProxy implements GetFileInformation {

		@Override
		public long callback(WString fileName, ByHandleFileInformation handleFileInfo, DokanFileInfo dokanFileInfo) {
			return NativeMethods.DokanNtStatusFromWin32(fileSystem.getFileInformation(fileName, handleFileInfo, dokanFileInfo));
		}
	}

	class FindFilesProxy implements FindFiles {

		@Override
		public long callback(WString rawPath, FillWin32FindData rawFillFindData, DokanFileInfo dokanFileInfo) {
			return NativeMethods.DokanNtStatusFromWin32(fileSystem.findFiles(rawPath, rawFillFindData, dokanFileInfo));
		}
	}

	class FindFilesWithPatternProxy implements FindFilesWithPattern {

		@Override
		public long callback(WString fileName, WString searchPattern, FillWin32FindData rawFillFindData, DokanFileInfo dokanFileInfo) {
			return NativeMethods.DokanNtStatusFromWin32(fileSystem.findFilesWithPattern(fileName, searchPattern, rawFillFindData, dokanFileInfo));
		}
	}

	class SetFileAttributesProxy implements SetFileAttributes {

		@Override
		public long callback(WString rawPath, int rawAttributes, DokanFileInfo dokanFileInfo) {
			return NativeMethods.DokanNtStatusFromWin32(fileSystem.setFileAttributes(rawPath, rawAttributes, dokanFileInfo));
		}
	}

	class SetFileTimeProxy implements SetFileTime {

		@Override
		public long callback(WString rawPath, WinBase.FILETIME rawCreationTime, WinBase.FILETIME rawLastAccessTime, WinBase.FILETIME rawLastWriteTime, DokanFileInfo dokanFileInfo) {
			return NativeMethods.DokanNtStatusFromWin32(fileSystem.setFileTime(rawPath, rawCreationTime, rawLastAccessTime, rawLastWriteTime, dokanFileInfo));
		}
	}

	class DeleteFileProxy implements DeleteFile {

		@Override
		public long callback(WString rawPath, DokanFileInfo dokanFileInfo) {
			return NativeMethods.DokanNtStatusFromWin32(fileSystem.deleteFile(rawPath, dokanFileInfo));
		}
	}

	class DeleteDirectoryProxy implements DeleteDirectory {

		@Override
		public long callback(WString rawPath, DokanFileInfo dokanFileInfo) {
			return NativeMethods.DokanNtStatusFromWin32(fileSystem.deleteDirectory(rawPath, dokanFileInfo));
		}
	}

	class MoveFileProxy implements MoveFile {

		@Override
		public long callback(WString rawPath, WString rawNewFileName, boolean rawReplaceIfExisting, DokanFileInfo dokanFileInfo) {
			return NativeMethods.DokanNtStatusFromWin32(fileSystem.moveFile(rawPath, rawNewFileName, rawReplaceIfExisting, dokanFileInfo));
		}
	}

	class SetEndOfFileProxy implements SetEndOfFile {

		@Override
		public long callback(WString rawPath, long rawByteOffset, DokanFileInfo dokanFileInfo) {
			return NativeMethods.DokanNtStatusFromWin32(fileSystem.setEndOfFile(rawPath, rawByteOffset, dokanFileInfo));
		}
	}

	class SetAllocationSizeProxy implements SetAllocationSize {

		@Override
		public long callback(WString rawPath, long rawLength, DokanFileInfo dokanFileInfo) {
			return NativeMethods.DokanNtStatusFromWin32(fileSystem.setAllocationSize(rawPath, rawLength, dokanFileInfo));
		}
	}

	class LockFileProxy implements LockFile {

		@Override
		public long callback(WString rawPath, long rawByteOffset, long rawLength, DokanFileInfo dokanFileInfo) {
			return NativeMethods.DokanNtStatusFromWin32(fileSystem.lockFile(rawPath, rawByteOffset, rawLength, dokanFileInfo));
		}
	}

	class UnlockFileProxy implements UnlockFile {

		@Override
		public long callback(WString rawPath, long rawByteOffset, long rawLength, DokanFileInfo dokanFileInfo) {
			return NativeMethods.DokanNtStatusFromWin32(fileSystem.unlockFile(rawPath, rawByteOffset, rawLength, dokanFileInfo));
		}
	}

	class GetDiskFreeSpaceProxy implements GetDiskFreeSpace {

		@Override
		public long callback(LongByReference freeBytesAvailable, LongByReference totalNumberOfBytes, LongByReference totalNumberOfFreeBytes, DokanFileInfo dokanFileInfo) {
			return NativeMethods.DokanNtStatusFromWin32(fileSystem.getDiskFreeSpace(freeBytesAvailable, totalNumberOfBytes, totalNumberOfFreeBytes, dokanFileInfo));
		}
	}

	class GetVolumeInformationProxy implements GetVolumeInformation {

		@Override
		public long callback(Pointer rawVolumeNameBuffer, int rawVolumeNameSize, IntByReference rawVolumeSerialNumber, IntByReference rawMaximumComponentLength, IntByReference rawFileSystemFlags, Pointer rawFileSystemNameBuffer, int rawFileSystemNameSize, DokanFileInfo dokanFileInfo) {
			return NativeMethods.DokanNtStatusFromWin32(fileSystem.getVolumeInformation(rawVolumeNameBuffer, rawVolumeNameSize, rawVolumeSerialNumber, rawMaximumComponentLength, rawFileSystemFlags, rawFileSystemNameBuffer, rawFileSystemNameSize, dokanFileInfo));
		}
	}

	class MountedProxy implements Mounted {

		@Override
		public long mounted(DokanFileInfo dokanFileInfo) {
			return NativeMethods.DokanNtStatusFromWin32(fileSystem.mounted(dokanFileInfo));
		}
	}

	class UnmountedProxy implements Unmounted {

		@Override
		public long unmounted(DokanFileInfo dokanFileInfo) {
			return NativeMethods.DokanNtStatusFromWin32(fileSystem.unmounted(dokanFileInfo));
		}
	}

	class GetFileSecurityProxy implements GetFileSecurity {

		@Override
		public long callback(WString rawPath, int rawSecurityInformation, Pointer rawSecurityDescriptor, int rawSecurityDescriptorLength, IntByReference rawSecurityDescriptorLengthNeeded, DokanFileInfo dokanFileInfo) {
			return NativeMethods.DokanNtStatusFromWin32(fileSystem.getFileSecurity(rawPath, rawSecurityInformation, rawSecurityDescriptor, rawSecurityDescriptorLength, rawSecurityDescriptorLengthNeeded, dokanFileInfo));
		}
	}

	class SetFileSecurityProxy implements SetFileSecurity {

		@Override
		public long callback(WString rawPath, int rawSecurityInformation, Pointer rawSecurityDescriptor, int rawSecurityDescriptorLength, DokanFileInfo dokanFileInfo) {
			return NativeMethods.DokanNtStatusFromWin32(fileSystem.setFileSecurity(rawPath, rawSecurityInformation, rawSecurityDescriptor, rawSecurityDescriptorLength, dokanFileInfo));
		}
	}

	class FindStreamsProxy implements FindStreams {

		@Override
		public long callback(WString rawPath, FillWin32FindStreamData rawFillFindData, DokanFileInfo dokanFileInfo) {
			return NativeMethods.DokanNtStatusFromWin32(fileSystem.findStreams(rawPath, rawFillFindData, dokanFileInfo));
		}
	}

}