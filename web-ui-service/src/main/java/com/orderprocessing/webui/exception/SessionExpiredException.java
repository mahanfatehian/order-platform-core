package com.orderprocessing.webui.exception;

public class SessionExpiredException extends RuntimeException {
    public SessionExpiredException(String message, Throwable cause) { super(message, cause); }
}
