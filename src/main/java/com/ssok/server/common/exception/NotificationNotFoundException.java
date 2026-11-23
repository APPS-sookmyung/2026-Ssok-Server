package com.ssok.server.common.exception;

public class NotificationNotFoundException extends RuntimeException {
    public NotificationNotFoundException() { super("notification not found"); }
}
