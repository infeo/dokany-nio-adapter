package org.cryptomator.frontend.dokany;

import dev.dokan.dokan_java.DokanyFileSystem;
import dev.dokan.dokan_java.FileSystemInformation;
import dev.dokan.dokan_java.constants.dokany.MountOption;
import dev.dokan.dokan_java.constants.microsoft.FileSystemFlag;
import dev.dokan.dokan_java.structure.EnumIntegerSet;
import org.apache.commons.cli.ParseException;
import org.cryptomator.frontend.dokany.locks.LockManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * TODO: fix this class
 */
public class MountFactory {

	private static final Logger LOG = LoggerFactory.getLogger(MountFactory.class);
	private static final int MOUNT_TIMEOUT_MS = 5000;
	private static final short THREAD_COUNT = 5;
	private static final EnumIntegerSet<MountOption> MOUNT_OPTIONS = new EnumIntegerSet<>( //
			// MountOption.DEBUG_MODE, //
			// MountOption.STD_ERR_OUTPUT, //
			// MountOption.REMOVABLE_DRIVE, //
			MountOption.CURRENT_SESSION);
	private static final EnumIntegerSet<FileSystemFlag> FILE_SYSTEM_FEATURES = new EnumIntegerSet<>( //
			FileSystemFlag.CASE_PRESERVED_NAMES, //
			FileSystemFlag.CASE_SENSITIVE_SEARCH, //
			// FileSystemFeature.PERSISTENT_ACLS, //
			// FileSystemFeature.SUPPORTS_REMOTE_STORAGE, //
			FileSystemFlag.UNICODE_ON_DISK);
	private static final String UNC_NAME = "";
	private static final int TIMEOUT = 10000;
	private static final int ALLOC_UNIT_SIZE = 4096;
	private static final int SECTOR_SIZE = 4096;


	public MountFactory(){

	}
	/**
	 * Mounts a virtual drive at the given mount point containing contents of the given path.
	 * This method blocks until the mount succeeds or times out.
	 *
	 * @param fileSystemRoot Path to the directory which will be the content root of the mounted drive.
	 * @param mountPoint The mount point of the mounted drive. Can be an empty directory or a drive letter.
	 * @param volumeName The name of the drive as shown to the user.
	 * @param fileSystemName The technical file system name shown in the drive properties window.
	 * @return The mount object.
	 * @throws MountFailedException if the mount process is aborted due to errors
	 */
	public Mount mount(Path fileSystemRoot, Path mountPoint, String volumeName, String fileSystemName) throws MountFailedException {
		Path absMountPoint = mountPoint.toAbsolutePath();
		FileSystemInformation fsInfo = new FileSystemInformation(FILE_SYSTEM_FEATURES);
		CompletableFuture<Void> mountDidSucceed = new CompletableFuture<>();
		LockManager lockManager = new LockManager();
		DokanyFileSystem dokanyFs = new ReadWriteAdapter(fileSystemRoot, lockManager, mountDidSucceed, fsInfo);
		LOG.debug("Mounting on {}: ...", absMountPoint);
		Mount mount = new Mount(absMountPoint, dokanyFs);
		dokanyFs.mount(absMountPoint, volumeName, 30974, false, TIMEOUT, ALLOC_UNIT_SIZE, SECTOR_SIZE, null, THREAD_COUNT, MOUNT_OPTIONS);
		LOG.debug("Mounted directory at {} successfully.", absMountPoint.toString());
		return mount;
	}

	/**
	 * Mounts a virtual drive at the given mount point containing contents of the given path with the specified additional mount options.
	 * If an additional mount option is not specified the default value is used.
	 * This method blocks until the mount succeeds or times out.
	 *
	 * @param fileSystemRoot Path to the directory which will be the content root of the mounted drive.
	 * @param mountPoint The mount point of the mounted drive. Can be an empty directory or a drive letter.
	 * @param volumeName The name of the drive as shown to the user.
	 * @param fileSystemName The technical file system name shown in the drive properties window.
	 * @param additionalOptions String of additional options to overwrite default values. See {@link MountUtil} for details.
	 * @return The mount object.
	 * @throws MountFailedException if the mount process is aborted due to errors
	 */
	public Mount mount(Path fileSystemRoot, Path mountPoint, String volumeName, String fileSystemName, String additionalOptions) throws MountFailedException {
		Path absMountPoint = mountPoint.toAbsolutePath();
		MountUtil.MountOptions options = parseMountOptions(additionalOptions);
		FileSystemInformation fsInfo = new FileSystemInformation(FILE_SYSTEM_FEATURES);
		CompletableFuture<Void> mountDidSucceed = new CompletableFuture<>();
		LockManager lockManager = new LockManager();
		DokanyFileSystem dokanyFs = new ReadWriteAdapter(fileSystemRoot, lockManager, mountDidSucceed, fsInfo);

		LOG.debug("Mounting on {}: ...", absMountPoint);
		Mount mount = new Mount(absMountPoint, dokanyFs);
		dokanyFs.mount(absMountPoint, volumeName, 30974, false,
				options.getTimeout().orElse(TIMEOUT),
				options.getAllocationUnitSize().orElse(ALLOC_UNIT_SIZE),
				options.getSectorSize().orElse(SECTOR_SIZE),
				null,
				options.getThreadCount().orElse(THREAD_COUNT),
				options.getDokanOptions());
		return mount;
	}

	private MountUtil.MountOptions parseMountOptions(String options) throws MountFailedException {
		try {
			return MountUtil.parse(options);
		} catch (IllegalArgumentException | ParseException e) {
			throw new MountFailedException(e);
		}
	}

	public static boolean isApplicable() {
		return System.getProperty("os.name").toLowerCase().contains("windows")
				&& Files.exists(Paths.get("C:\\Windows\\System32\\drivers\\dokan1.sys")); // https://github.com/dokan-dev/dokany/wiki/How-to-package-your-application-with-Dokan#check-for-previous-dokan-installations
	}

}
