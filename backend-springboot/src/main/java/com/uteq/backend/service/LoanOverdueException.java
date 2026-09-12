package com.uteq.backend.service;

public class LoanOverdueException extends RuntimeException {

    public LoanOverdueException(String message) {
        super(message);
    }
}
