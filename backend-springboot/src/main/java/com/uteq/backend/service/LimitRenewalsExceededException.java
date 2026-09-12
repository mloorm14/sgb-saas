package com.uteq.backend.service;

public class LimitRenewalsExceededException extends RuntimeException {

    public LimitRenewalsExceededException(String message) {
        super(message);
    }
}
