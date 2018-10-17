package com.dokany.java;

import com.dokany.java.migrated.constants.microsoft.Win32ErrorCode;
import com.dokany.java.legacy.constants.WinError;

import java.io.IOException;

public final class DokanyException extends RuntimeException {

	private final int value;
	private final String message;
	private final Throwable cause;

	public DokanyException(final long errorCode, final IOException exception) {
		if ((errorCode < 0) || (errorCode > Integer.MAX_VALUE)) {
			throw new IllegalArgumentException("error code (" + errorCode + ") is not in range [0, 4294967295]", exception);
		}
		this.value = (int) errorCode;
		this.message = "";
		this.cause = null;
	}

	public DokanyException(final WinError errorCode, final IOException exception) {
		this(errorCode.getMask(), exception);
	}

	public DokanyException(final Win32ErrorCode errorCode, final IOException exception) {
		this(errorCode.getMask(), exception);
	}

	public DokanyException(Exception e) {
		this.value = Integer.MIN_VALUE;
		this.message = "";
		this.cause = e;
	}

	public DokanyException(String message, int errorCode) {
	    this.value = errorCode;
	    this.message = message;
	    this.cause = null;

	}

    public DokanyException(String message, Throwable cause) {
        this.value = Integer.MIN_VALUE;
        this.message = message;
        this.cause = cause;
    }

	public int getValue() {
		return value;
	}


	public Throwable getCause(){
	    return this.cause;
    }

    public String getMessage(){
	    return message;
    }
}