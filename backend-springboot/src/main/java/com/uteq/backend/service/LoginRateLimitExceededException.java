package com.uteq.backend.service;

public class LoginRateLimitExceededException extends RuntimeException {

    public LoginRateLimitExceededException(String message) {
        super(message);
    }
}
