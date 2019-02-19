package org.cryptomator.frontend.dokany;

import dev.dokan.dokan_java.FileSystemInformation;
import dev.dokan.dokan_java.constants.dokany.MountOption;
import dev.dokan.dokan_java.constants.microsoft.FileSystemFlag;
import dev.dokan.dokan_java.structure.EnumIntegerSet;
import org.cryptomator.frontend.dokany.locks.LockManager;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class MirrorReadOnlyThread implements Runnable {

	private static final long TIMEOUT = 1000;

	private ReadWriteAdapter fs;
	private EnumIntegerSet<MountOption> mountOptions;
	private final Path mountPoint;
	private final Path dirToMirror;

	public MirrorReadOnlyThread(Path dirToMirror, Path mountPoint) {
		System.out.println("Initializing Dokany MirrorFS with MountPoint " + mountPoint.toString() + " and directory to mirror " + dirToMirror.toString());
		this.mountPoint = mountPoint;
		this.dirToMirror = dirToMirror;

		this.mountOptions = new EnumIntegerSet<>(MountOption.class);
		this.mountOptions.add(MountOption.DEBUG_MODE, MountOption.STD_ERR_OUTPUT, MountOption.MOUNT_MANAGER, MountOption.WRITE_PROTECTION);

		EnumIntegerSet<FileSystemFlag> fsFeatures = new EnumIntegerSet<>(FileSystemFlag.class);
		fsFeatures.add(FileSystemFlag.CASE_PRESERVED_NAMES, FileSystemFlag.CASE_SENSITIVE_SEARCH,
				FileSystemFlag.PERSISTENT_ACLS, FileSystemFlag.UNICODE_ON_DISK, FileSystemFlag.READ_ONLY_VOLUME);
		FileSystemInformation fsInfo = new FileSystemInformation(fsFeatures);

		this.fs = new ReadWriteAdapter(dirToMirror, new LockManager(), new CompletableFuture<>(), fsInfo);

	}

	@Override
	public void run() {
		System.out.println("Starting new dokany thread with mount point " + mountPoint.toString());
		fs.mount(mountPoint,"DOKAN_ONE",30975,true,3000,4096,512,null,(short) 5, mountOptions);
	}

}
