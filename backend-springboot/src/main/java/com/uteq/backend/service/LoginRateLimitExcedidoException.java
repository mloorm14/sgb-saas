package com.uteq.backend.service;

public class LoginRateLimitExcedidoException extends RuntimeException {

    public LoginRateLimitExcedidoException(String message) {
        super(message);
    }
}
