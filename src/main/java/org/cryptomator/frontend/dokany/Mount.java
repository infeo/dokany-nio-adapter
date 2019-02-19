package org.cryptomator.frontend.dokany;

import dev.dokan.dokan_java.DokanyFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * TODO: Fix this class
 */
public class Mount implements AutoCloseable {

	private static final Logger LOG = LoggerFactory.getLogger(Mount.class);
	private static final int REVEAL_TIMEOUT_MS = 5000;
	private static final int UNMOUNT_TIMEOUT_MS = 5000;

	private final DokanyFileSystem fs;
	private final Path mountPoint;
	private final ProcessBuilder revealCommand;

	private Future<?> driverJob;

	public Mount(Path mountPoint, DokanyFileSystem fs) {
		this.fs = fs;
		this.mountPoint = mountPoint;
		this.revealCommand = new ProcessBuilder("explorer", "/root,", mountPoint.toString());
	}

	public void mount() throws ExecutionException, InterruptedException {
		try {
			driverJob.get(3000, TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			LOG.trace("Mounting still in progress.");
		}
	}

	public boolean reveal() {
		try {
			Process proc = revealCommand.start();
			boolean finishedInTime = proc.waitFor(REVEAL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
			if (finishedInTime) {
				// The check proc.exitValue() == 0 is always false since Windows explorer return every time an exit value of 1
				return true;
			} else {
				proc.destroyForcibly();
				return false;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		} catch (IOException e) {
			LOG.error("Failed to reveal drive.", e);
			return false;
		}
	}

	@Override
	public void close() {
		LOG.debug("Unmounting drive {}: ...", mountPoint);
		fs.unmount();
		LOG.debug("Unmounted drive {}: successfully.", mountPoint);
	}
}
