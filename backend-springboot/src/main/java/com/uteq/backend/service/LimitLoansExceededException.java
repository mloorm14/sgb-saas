package com.uteq.backend.service;

public class LimitLoansExceededException extends RuntimeException {
    public LimitLoansExceededException(String message) {
        super(message);
    }
}
