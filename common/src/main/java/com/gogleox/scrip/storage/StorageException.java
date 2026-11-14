package com.gogleox.scrip.storage;

/** Signals a storage condition that must prevent a financial acknowledgement. */
public final class StorageException extends RuntimeException {
    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
