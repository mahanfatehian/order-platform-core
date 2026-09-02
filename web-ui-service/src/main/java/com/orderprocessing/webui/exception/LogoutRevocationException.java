package com.orderprocessing.webui.exception;

public class LogoutRevocationException extends RuntimeException {
    public LogoutRevocationException(RuntimeException cause) {
        super("Authentication service could not revoke the current session", cause);
    }
}
