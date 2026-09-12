package com.uteq.backend.service;

public class RefreshTokenInvalidException extends RuntimeException {

    public RefreshTokenInvalidException(String message) {
        super(message);
    }
}
