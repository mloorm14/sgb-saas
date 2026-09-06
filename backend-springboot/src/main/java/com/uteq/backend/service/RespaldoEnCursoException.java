package com.uteq.backend.service;

/** Se lanza cuando ya hay un respaldo completo en ejecución (cerrojo del ejecutor). Se traduce a HTTP 429. */
public class RespaldoEnCursoException extends RuntimeException {
    public RespaldoEnCursoException(String message) {
        super(message);
    }
}
