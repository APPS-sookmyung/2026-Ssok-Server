package com.ssok.server.common.exception;

public class FileTooLargeException extends RuntimeException {
    public FileTooLargeException() { super("maximum file size is 10 MiB"); }
}
