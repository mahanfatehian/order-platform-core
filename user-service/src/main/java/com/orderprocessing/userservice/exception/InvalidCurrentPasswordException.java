package com.orderprocessing.userservice.exception;

/**
 * The caller is authenticated; it is the current-password field in the payload that is wrong. That is a rejected
 * field, not a rejected identity, so it must not be answered as 401 - callers treat a 401 as proof their access
 * token is finished and tear the session down.
 */
public class InvalidCurrentPasswordException extends RuntimeException {
    public static final String FIELD = "currentPassword";

    public InvalidCurrentPasswordException(String message) {
        super(message);
    }
}
