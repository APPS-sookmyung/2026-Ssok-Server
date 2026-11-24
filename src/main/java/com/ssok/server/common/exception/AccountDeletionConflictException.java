package com.ssok.server.common.exception;

public class AccountDeletionConflictException extends RuntimeException {
    public AccountDeletionConflictException(String message) { super(message); }
}
