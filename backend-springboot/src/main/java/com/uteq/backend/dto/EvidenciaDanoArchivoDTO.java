package com.uteq.backend.dto;

// NOTA: contiene byte[]. El record genera equals()/hashCode() mediante
// Arrays.equals/hashCode. No usar esta clase como clave en HashMap
// ni como elemento de HashSet.
public record EvidenciaDanoArchivoDTO(
        String archivoTipo,
        byte[] archivoBytes
) {}
