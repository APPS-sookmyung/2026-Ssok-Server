package com.ssok.server.common.exception;

public class FileNotFoundException extends RuntimeException {
    public FileNotFoundException() { super("file not found"); }
}
