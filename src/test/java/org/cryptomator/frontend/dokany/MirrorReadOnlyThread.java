package org.cryptomator.frontend.dokany;

import com.dokany.java.DokanyDriver;
import com.dokany.java.DokanyFileSystem_OLD;
import com.dokany.java.migrated.constants.microsoft.FileSystemFlag;
import com.dokany.java.migrated.constants.dokany.MountOption;
import com.dokany.java.migrated.structure.DokanOptions;
import com.dokany.java.migrated.structure.EnumIntegerSet;
import com.dokany.java.legacy.structure.VolumeInformation;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class MirrorReadOnlyThread implements Runnable {

	private static final long TIMEOUT = 1000;

	private final Path mountPoint;
	private final Path dirToMirror;
	private final DokanOptions devOps;
	private final DokanyDriver dokany;

	public MirrorReadOnlyThread(Path dirToMirror, Path mountPoint) {
		System.out.println("Initializing Dokany MirrorFS with MountPoint " + mountPoint.toString() + " and directory to mirror " + dirToMirror.toString());
		this.mountPoint = mountPoint;
		this.dirToMirror = dirToMirror;

		final short threadCount = 1;
		EnumIntegerSet mountOptions = new EnumIntegerSet<>(MountOption.class);
		mountOptions.add(MountOption.DEBUG_MODE, MountOption.STD_ERR_OUTPUT, MountOption.MOUNT_MANAGER);
		String uncName = "";
		int timeout = 10000;
		int allocationUnitSize = 4096;
		int sectorSize = 4096;

		devOps = new DokanOptions(mountPoint.toString(), threadCount, mountOptions, uncName, timeout, allocationUnitSize, sectorSize);

		EnumIntegerSet fsFeatures = new EnumIntegerSet<>(FileSystemFlag.class);
		fsFeatures.add(FileSystemFlag.CASE_PRESERVED_NAMES, FileSystemFlag.CASE_SENSITIVE_SEARCH,
				FileSystemFlag.PERSISTENT_ACLS, FileSystemFlag.SUPPORTS_REMOTE_STORAGE, FileSystemFlag.UNICODE_ON_DISK);

		VolumeInformation volumeInfo = new VolumeInformation(VolumeInformation.DEFAULT_MAX_COMPONENT_LENGTH, "Mirror", 0x98765432, "Dokany MirrorFS", fsFeatures);

		DokanyFileSystem_OLD myFs = new ReadWriteAdapter(dirToMirror, volumeInfo, new CompletableFuture());
		dokany = new DokanyDriver(devOps, myFs);
	}

	@Override
	public void run() {
		dokany.start();
		System.out.println("Starting new dokany thread with mount point " + mountPoint.toString());
	}

	public DokanyDriver getDokanyDriver() {
		return dokany;
	}
}
