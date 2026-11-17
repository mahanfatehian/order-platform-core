package com.orderprocessing.walletservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class WalletException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public WalletException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static WalletException badRequest(String code, String message) {
        return new WalletException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static WalletException conflict(String code, String message) {
        return new WalletException(HttpStatus.CONFLICT, code, message);
    }

    public static WalletException notFound(String code, String message) {
        return new WalletException(HttpStatus.NOT_FOUND, code, message);
    }
}
