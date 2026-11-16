package com.ssok.server.common.exception;

public class BookmarkNotFoundException extends RuntimeException {

    public BookmarkNotFoundException(String message) {
        super(message);
    }
}
