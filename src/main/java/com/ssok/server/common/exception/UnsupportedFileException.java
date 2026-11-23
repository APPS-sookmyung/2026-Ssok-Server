package com.ssok.server.common.exception;

public class UnsupportedFileException extends RuntimeException {
    public UnsupportedFileException() { super("only PDF, PNG and JPEG with matching content are supported"); }
}
