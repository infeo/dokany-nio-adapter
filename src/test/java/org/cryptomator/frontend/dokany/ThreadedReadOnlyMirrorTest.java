package org.cryptomator.frontend.dokany;

import dev.dokan.dokan_java.FileSystemInformation;
import dev.dokan.dokan_java.constants.dokany.MountOption;
import dev.dokan.dokan_java.constants.microsoft.FileSystemFlag;
import dev.dokan.dokan_java.structure.EnumIntegerSet;
import org.cryptomator.frontend.dokany.locks.LockManager;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

/**
 * TODO: this class must be tested
 */
public class ThreadedReadOnlyMirrorTest {

	public static void main(String[] args) {
		try (Scanner scanner = new Scanner(System.in)) {
			System.out.println("Enter path to the directory you want to mirror:");
			Path p1 = Paths.get(scanner.nextLine());
			System.out.println("Enter path to mount point to use:");
			Path mountPoint1 = Paths.get(scanner.nextLine());
			System.out.println("Enter path to the second directory you want to mirror:");
			Path p2 = Paths.get(scanner.nextLine());
			System.out.println("Enter path to second mount point to use:");
			Path mountPoint2 = Paths.get(scanner.nextLine());

			ReadWriteAdapter adapter1 = createReadWriteAdapter(p1);
			ReadWriteAdapter adapter2 = createReadWriteAdapter(p2);

			Thread t1 = new Thread( () -> {
				System.out.println("Starting new dokany thread with mount point " + mountPoint1.toString());
				EnumIntegerSet<MountOption> mountOptions = new EnumIntegerSet<>(MountOption.class);
				mountOptions.add(MountOption.DEBUG_MODE, MountOption.STD_ERR_OUTPUT, MountOption.MOUNT_MANAGER, MountOption.WRITE_PROTECTION);
				adapter1.mount(mountPoint1,"DOKAN_ONE",30975,true,3000,4096,512,null,(short) 5, mountOptions);
			});

			Thread t2 = new Thread( () -> {
				System.out.println("Starting new dokany thread with mount point " + mountPoint2.toString());
				EnumIntegerSet<MountOption> mountOptions = new EnumIntegerSet<>(MountOption.class);
				mountOptions.add(MountOption.DEBUG_MODE, MountOption.STD_ERR_OUTPUT, MountOption.MOUNT_MANAGER, MountOption.WRITE_PROTECTION);
				adapter2.mount(mountPoint2,"DOKAN_TWO",30975,true,3000,4096,512,null,(short) 5, mountOptions);
			});

			t1.start();
			//t2.start();
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				adapter1.unmount();
				//two.unmount();
			}));
		}
	}

	public static ReadWriteAdapter createReadWriteAdapter(Path dirToMirror) {

		EnumIntegerSet<FileSystemFlag> fsFeatures = new EnumIntegerSet<>(FileSystemFlag.class);
		fsFeatures.add(FileSystemFlag.CASE_PRESERVED_NAMES, FileSystemFlag.CASE_SENSITIVE_SEARCH,
				FileSystemFlag.PERSISTENT_ACLS, FileSystemFlag.UNICODE_ON_DISK, FileSystemFlag.READ_ONLY_VOLUME);
		FileSystemInformation fsInfo = new FileSystemInformation(fsFeatures);

		return new ReadWriteAdapter(dirToMirror, new LockManager(), new CompletableFuture<>(), fsInfo);
	}

}
