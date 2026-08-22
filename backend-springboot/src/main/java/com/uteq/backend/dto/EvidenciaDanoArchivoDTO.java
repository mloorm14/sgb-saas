package com.uteq.backend.dto;

public record EvidenciaDanoArchivoDTO(
        String archivoTipo,
        byte[] archivoBytes
) {}
