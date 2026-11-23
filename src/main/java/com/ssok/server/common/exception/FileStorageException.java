package com.ssok.server.common.exception;

public class FileStorageException extends RuntimeException {
    public FileStorageException() { super("file storage is temporarily unavailable"); }
}
