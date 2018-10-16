package com.dokany.java;

import com.dokany.java.migrated.DokanyFileSystem;
import com.dokany.java.migrated.NotImplemented;
import com.dokany.java.migrated.structure.DokanOptions;
import com.dokany.java.structure.VolumeInformation;
import com.sun.jna.ptr.IntByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractDokanyFileSystem implements DokanyFileSystem {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractDokanyFileSystem.class);

    private Set<String> notImplementedMethods;
    protected final Path mountPoint;
    protected final DokanOptions dokanOptions;
    protected final VolumeInformation volumeInformation;
    protected final DokanyOperations dokanyOperations;
    protected final boolean usesKernelFlagsAndCodes;

    public AbstractDokanyFileSystem(Path mountPoint, DokanOptions dokanOptions, VolumeInformation volumeInformation, boolean usesKernelFlagsAndCodes) {
        this.mountPoint = mountPoint;
        this.dokanOptions = dokanOptions;
        this.volumeInformation = volumeInformation;
        this.usesKernelFlagsAndCodes = usesKernelFlagsAndCodes;
        this.dokanyOperations = new DokanyOperations();
    }


    private void init(DokanyOperations dokanyOperations) {
        notImplementedMethods = Arrays.stream(getClass().getMethods())
                .filter(method -> method.getAnnotation(NotImplemented.class) != null)
                .map(Method::getName)
                .collect(Collectors.toSet());

        AbstractDokanyFileSystem dokanyFS = this;
        if (usesKernelFlagsAndCodes) {
            if (isImplemented("zwCreateFile")) {
                dokanyOperations.setZwCreateFile(this::zwCreateFile);
            }
            if (isImplemented("cleanup")) {
                dokanyOperations.setCleanup(this::cleanup);
            }
            if (isImplemented("closeFile")) {
                dokanyOperations.setCloseFile(this::closeFile);
            }
            if (isImplemented("readFile")) {
                dokanyOperations.setReadFile(this::readFile);
            }
            if (isImplemented("writeFile")) {
                dokanyOperations.setWriteFile(this::writeFile);
            }
            if (isImplemented("flushFileBuffer")) {
                dokanyOperations.setFlushFileBuffers(this::flushFileBuffers);
            }
            if (isImplemented("getFileInformation")) {
                dokanyOperations.setGetFileInformation(this::getFileInformation);
            }
            if (isImplemented("findFiles")) {
                dokanyOperations.setFindFiles(this::findFiles);
            }
            if (isImplemented("findFilesWithPattern")) {
                dokanyOperations.setFindFilesWithPattern(this::findFilesWithPattern);
            }
            if (isImplemented("setFileAttributes")) {
                dokanyOperations.setSetFileAttributes(this::setFileAttributes);
            }
            if (isImplemented("setFileTime")) {
                dokanyOperations.setSetFileTime(this::setFileTime);
            }
            if (isImplemented("deleteFile")) {
                dokanyOperations.setDeleteFile(this::deleteFile);
            }
            if (isImplemented("deleteDirectory")) {
                dokanyOperations.setDeleteDirectory(this::deleteDirectory);
            }
            if (isImplemented("moveFile")) {
                dokanyOperations.setMoveFile(this::moveFile);
            }
            if (isImplemented("setEndOfFile")) {
                dokanyOperations.setSetEndOfFile(this::setEndOfFile);
            }
            if (isImplemented("setAllocationSize")) {
                dokanyOperations.setSetAllocationSize(this::setAllocationSize);
            }
            if (isImplemented("lockFile")) {
                dokanyOperations.setLockFile(this::lockFile);
            }
            if (isImplemented("unlockFile")) {
                dokanyOperations.setUnlockFile(this::unlockFile);
            }
            if (isImplemented("getDiskFreeSpace")) {
                dokanyOperations.setGetDiskFreeSpace(this::getDiskFreeSpace);
            }
            if (isImplemented("getVolumeInformation")) {
                dokanyOperations.setGetVolumeInformation(this::getVolumeInformation);
            }
            if (isImplemented("mounted")) {
                dokanyOperations.setMounted(this::mounted);
            }
            if (isImplemented("unmounted")) {
                dokanyOperations.setUnmounted(this::unmounted);
            }
            if (isImplemented("getFileSecurity")) {
                dokanyOperations.setGetFileSecurity(this::getFileSecurity);
            }
            if (isImplemented("setFileSecurity")) {
                dokanyOperations.setSetFileSecurity(this::setFileSecurity);
            }
            if (isImplemented("fillWin32FindData")) {
                //TODO
            }
            if (isImplemented("findStreams")) {
                dokanyOperations.setFindStreams(this::findStreams);
            }
        } else {
            if (isImplemented("zwCreateFile")) {
                dokanyOperations.setZwCreateFile((rawPath, securityContext, rawDesiredAccess, rawFileAttributes, rawShareAccess, rawCreateDisposition, rawCreateOptions, dokanFileInfo) -> {
                    IntByReference createDisposition = new IntByReference();
                    IntByReference desiredAccess = new IntByReference();
                    IntByReference fileAttributeFlags = new IntByReference();
                    NativeMethods.DokanMapKernelToUserCreateFileFlags(rawDesiredAccess, rawFileAttributes, rawCreateOptions, rawCreateDisposition, desiredAccess, fileAttributeFlags, createDisposition);
                    return NativeMethods.DokanNtStatusFromWin32(this.zwCreateFile(rawPath, securityContext, desiredAccess.getValue(), fileAttributeFlags.getValue(), rawShareAccess, createDisposition.getValue(), rawCreateOptions, dokanFileInfo));
                });
            }
            if (isImplemented("cleanup")) {
                dokanyOperations.setCleanup(this::cleanup); //cleanup returns void, so no further preprocessing is necessary
            }
            if (isImplemented("closeFile")) {
                dokanyOperations.setCloseFile(this::closeFile);
            }
            if (isImplemented("readFile")) {
                dokanyOperations.setReadFile((rawPath, rawBuffer, rawBufferLength, rawReadLength, rawOffset, dokanyFileInfo) -> NativeMethods.DokanNtStatusFromWin32(this.readFile(rawPath, rawBuffer, rawBufferLength, rawReadLength, rawOffset, dokanyFileInfo)));
            }
            if (isImplemented("writeFile")) {
                dokanyOperations.setWriteFile((rawPath, rawBuffer, rawNumberOfBytesToWrite, rawNumberOfWritesWritten, rawOffset, dokanyFileInfo) -> NativeMethods.DokanNtStatusFromWin32(this.writeFile(rawPath, rawBuffer, rawNumberOfBytesToWrite, rawNumberOfWritesWritten, rawOffset, dokanyFileInfo)));
            }
            if (isImplemented("flushFileBuffer")) {
                dokanyOperations.setFlushFileBuffers((rawPath, dokanyFileInfo) -> NativeMethods.DokanNtStatusFromWin32(this.flushFileBuffers(rawPath, dokanyFileInfo)));
            }
            if (isImplemented("getFileInformation")) {
                dokanyOperations.setGetFileInformation((rawPath, handleFileInfo, dokanFileInfo) -> NativeMethods.DokanNtStatusFromWin32(this.getFileInformation(rawPath, handleFileInfo, dokanFileInfo)));
            }
            if (isImplemented("findFiles")) {
                dokanyOperations.setFindFiles((rawPath, rawFillWin32FindData, dokanFileInfo) -> NativeMethods.DokanNtStatusFromWin32(this.findFiles(rawPath, rawFillWin32FindData, dokanFileInfo)));
            }
            if (isImplemented("findFilesWithPattern")) {
                dokanyOperations.setFindFilesWithPattern(((rawPath, rawFillWin32FindData, pattern, dokanFileInfo) -> NativeMethods.DokanNtStatusFromWin32(this.findFilesWithPattern(rawPath, rawFillWin32FindData, pattern, dokanFileInfo))));
            }
            if (isImplemented("setFileAttributes")) {
                dokanyOperations.setSetFileAttributes((rawPath, rawAttributes, dokanFileInfo) -> NativeMethods.DokanNtStatusFromWin32(this.setFileAttributes(rawPath, rawAttributes, dokanFileInfo)));
            }
            if (isImplemented("setFileTime")) {
                dokanyOperations.setSetFileTime((rawPath, rawCreatonTime, rawLastAccessTime, rawLastWriteTime, dokanFileInfo) -> NativeMethods.DokanNtStatusFromWin32(this.setFileTime(rawPath, rawCreatonTime, rawLastAccessTime, rawLastWriteTime, dokanFileInfo)));
            }
            if (isImplemented("deleteFile")) {
                dokanyOperations.setDeleteFile((rawPath, dokanFileInfo) -> NativeMethods.DokanNtStatusFromWin32(this.deleteFile(rawPath, dokanFileInfo)));
            }
            if (isImplemented("deleteDirectory")) {
                dokanyOperations.setDeleteDirectory((rawPath, dokanFileInfo) -> NativeMethods.DokanNtStatusFromWin32(this.deleteDirectory(rawPath, dokanFileInfo)));
            }
            if (isImplemented("moveFile")) {
                dokanyOperations.setMoveFile((rawPath, rawNewFileName, rawReplaceIfExisting, dokanFileInfo) -> NativeMethods.DokanNtStatusFromWin32(this.moveFile(rawPath, rawNewFileName, rawReplaceIfExisting, dokanFileInfo)));
            }
            if (isImplemented("setEndOfFile")) {
                dokanyOperations.setSetEndOfFile((rawPath, rawByteOffset, dokanFileInfo) -> NativeMethods.DokanNtStatusFromWin32(this.setEndOfFile(rawPath, rawByteOffset, dokanFileInfo)));
            }
            if (isImplemented("setAllocationSize")) {
                dokanyOperations.setSetAllocationSize((rawPath, rawLength, dokanFileInfo) -> NativeMethods.DokanNtStatusFromWin32(this.setAllocationSize(rawPath, rawLength, dokanFileInfo)));
            }
            if (isImplemented("lockFile")) {
                dokanyOperations.setLockFile((rawPath, rawByteOffset, rawLength, dokanFileInfo) -> NativeMethods.DokanNtStatusFromWin32(this.lockFile(rawPath, rawByteOffset, rawLength, dokanFileInfo)));
            }
            if (isImplemented("unlockFile")) {
                dokanyOperations.setUnlockFile((rawPath, rawByteOffset, rawLength, dokanFileInfo) -> NativeMethods.DokanNtStatusFromWin32(this.unlockFile(rawPath, rawByteOffset, rawLength, dokanFileInfo)));
            }
            if (isImplemented("getDiskFreeSpace")) {
                dokanyOperations.setGetDiskFreeSpace((freeBytesAvailable, totalNumberOfBytes, totalNumberOfFreeBytes, dokanFileInfo) -> NativeMethods.DokanNtStatusFromWin32(this.getDiskFreeSpace(freeBytesAvailable, totalNumberOfBytes, totalNumberOfFreeBytes, dokanFileInfo)));
            }
            if (isImplemented("getVolumeInformation")) {
                dokanyOperations.setGetVolumeInformation((rawVolumeNameBuffer, rawVolumeNameSize, rawVolumeSerialNumber, rawMaximumComponentLength, rawFileSystemFlags, rawFileSystemNameBuffer, rawFileSystemNameSize, dokanFileInfo) -> NativeMethods.DokanNtStatusFromWin32(this.getVolumeInformation(rawVolumeNameBuffer, rawVolumeNameSize, rawVolumeSerialNumber, rawMaximumComponentLength, rawFileSystemFlags, rawFileSystemNameBuffer, rawFileSystemNameSize, dokanFileInfo)));
            }
            if (isImplemented("mounted")) {
                dokanyOperations.setMounted((dokanFileInfo) -> NativeMethods.DokanNtStatusFromWin32(this.mounted(dokanFileInfo)));
            }
            if (isImplemented("unmounted")) {
                dokanyOperations.setUnmounted((dokanFileInfo) -> NativeMethods.DokanNtStatusFromWin32(this.unmounted(dokanFileInfo)));
            }
            if (isImplemented("getFileSecurity")) {
                dokanyOperations.setGetFileSecurity((rawPath, rawSecurityInformation, rawSecurityDescriptor, rawSecurityDescriptorLength, rawSecurityDescriptorLengthNeeded, dokanFileInfo) -> NativeMethods.DokanNtStatusFromWin32(this.getFileSecurity(rawPath, rawSecurityInformation, rawSecurityDescriptor, rawSecurityDescriptorLength, rawSecurityDescriptorLengthNeeded, dokanFileInfo)));
            }
            if (isImplemented("setFileSecurity")) {
                dokanyOperations.setSetFileSecurity((rawPath, rawSecurityInformation, rawSecurityDescriptor, rawSecurityDescriptorLength, dokanFileInfo) -> NativeMethods.DokanNtStatusFromWin32(this.setFileSecurity(rawPath, rawSecurityInformation, rawSecurityDescriptor, rawSecurityDescriptorLength, dokanFileInfo)));
            }
            if (isImplemented("fillWin32FindData")) {
                //TODO
            }
            if (isImplemented("findStreams")) {
                dokanyOperations.setFindStreams((rawPath, fillWin32FindStreamData, dokanFileInfo) -> NativeMethods.DokanNtStatusFromWin32(this.findStreams(rawPath, fillWin32FindStreamData, dokanFileInfo)));
            }

        }
    }

    private boolean isImplemented(String funcName) {
        return !notImplementedMethods.contains(funcName);
    }

    public void mount(Path mountPoint, DokanOptions dokanOptions, VolumeInformation volumeInformation) {
        //TODO
    }

    public void unmount() {
        //TODO
    }
}
