package org.cryptomator.frontend.dokany;

import dev.dokan.dokan_java.FileSystemInformation;
import dev.dokan.dokan_java.constants.dokany.MountOption;
import dev.dokan.dokan_java.constants.microsoft.FileSystemFlag;
import dev.dokan.dokan_java.structure.EnumIntegerSet;
import org.cryptomator.frontend.dokany.locks.LockManager;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

public class ReadOnlyMirrorTest {

	private static final long TIMEOUT = 1000;

	public static void main(String[] args) throws Exception {
		System.out.println("Starting Dokany MirrorFS");

		Path mountPoint = Paths.get("K:\\");
		Path directoryToMirror = Paths.get("M:\\test");
		final short threadCount = 1;
		EnumIntegerSet mountOptions = new EnumIntegerSet<>(MountOption.class);
		mountOptions.add(MountOption.DEBUG_MODE, MountOption.STD_ERR_OUTPUT, MountOption.MOUNT_MANAGER, MountOption.WRITE_PROTECTION);

		EnumIntegerSet<FileSystemFlag> fsFeatures = new EnumIntegerSet<>(FileSystemFlag.class);
		fsFeatures.add(FileSystemFlag.CASE_PRESERVED_NAMES, FileSystemFlag.CASE_SENSITIVE_SEARCH,
				FileSystemFlag.PERSISTENT_ACLS, FileSystemFlag.UNICODE_ON_DISK, FileSystemFlag.READ_ONLY_VOLUME);
		FileSystemInformation fsInfo = new FileSystemInformation(fsFeatures);

		ReadWriteAdapter fs = new ReadWriteAdapter(directoryToMirror, new LockManager(), new CompletableFuture<>(), fsInfo);

		try{
			fs.mount(mountPoint,mountOptions);
			System.in.read();
		}finally {
			fs.close();
		}
	}

}
