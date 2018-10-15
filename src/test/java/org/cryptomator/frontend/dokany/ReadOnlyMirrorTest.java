package org.cryptomator.frontend.dokany;

import com.dokany.java.DokanyDriver;
import com.dokany.java.DokanyFileSystem_OLD;
import com.dokany.java.migrated.constants.microsoft.FileSystemFlag;
import com.dokany.java.migrated.constants.dokany.MountOption;
import com.dokany.java.migrated.structure.DokanOptions;
import com.dokany.java.migrated.structure.EnumIntegerSet;
import com.dokany.java.structure.VolumeInformation;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ReadOnlyMirrorTest {

	private static final long TIMEOUT = 1000;

	public static void main(String[] args) throws IOException {
		System.out.println("Starting Dokany MirrorFS");

		String mountPoint = "K:\\";
		final short threadCount = 1;
		EnumIntegerSet mountOptions = new EnumIntegerSet<>(MountOption.class);
		mountOptions.add(MountOption.DEBUG_MODE, MountOption.STD_ERR_OUTPUT, MountOption.MOUNT_MANAGER, MountOption.WRITE_PROTECTION);
		String uncName = "";
		int timeout = 10000;
		int allocationUnitSize = 4096;
		int sectorSize = 4096;

		DokanOptions dokanOptions = new DokanOptions(mountPoint, threadCount, mountOptions, uncName, timeout, allocationUnitSize, sectorSize);

		EnumIntegerSet fsFeatures = new EnumIntegerSet<>(FileSystemFlag.class);
		fsFeatures.add(FileSystemFlag.CASE_PRESERVED_NAMES, FileSystemFlag.CASE_SENSITIVE_SEARCH,
				FileSystemFlag.PERSISTENT_ACLS, FileSystemFlag.SUPPORTS_REMOTE_STORAGE, FileSystemFlag.UNICODE_ON_DISK);

		VolumeInformation volumeInfo = new VolumeInformation(VolumeInformation.DEFAULT_MAX_COMPONENT_LENGTH, "Mirror", 0x98765432, "Dokany MirrorFS", fsFeatures);

		DokanyFileSystem_OLD myFs = new ReadWriteAdapter(Paths.get("Y:\\test"), volumeInfo, new CompletableFuture());
		DokanyDriver dokanyDriver = new DokanyDriver(dokanOptions, myFs);

		int res;
		try {
			res = CompletableFuture
					.supplyAsync(() -> execMount(dokanyDriver))
					.get(TIMEOUT, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		} catch (TimeoutException e) {
			e.printStackTrace();
		}

		System.in.read();
		dokanyDriver.shutdown();
	}

	private static int execMount(DokanyDriver dd) {
		dd.start();
		return 0;
	}

}
