package com.uteq.backend.service;

/**
 * Dependencia externa temporalmente no disponible (ej. Redis/Upstash caído
 * o con cuota agotada). Se devuelve 503 Service Unavailable -- honesto y
 * distinguible de un 500 de bug, para que el cliente sepa que debe reintentar
 * más tarde y el equipo que es un problema de infraestructura, no de código.
 * Ver docs/mediciones/sec/2026-08-14-incidente-500-auth-redis-produccion.md.
 */
public class ServicioTemporalmenteNoDisponibleException extends RuntimeException {

    public ServicioTemporalmenteNoDisponibleException(String message) {
        super(message);
    }
}
