package com.uteq.backend.service;

public class EmailDomainNotAllowedException extends RuntimeException {
    public EmailDomainNotAllowedException(String message) {
        super(message);
    }
}
