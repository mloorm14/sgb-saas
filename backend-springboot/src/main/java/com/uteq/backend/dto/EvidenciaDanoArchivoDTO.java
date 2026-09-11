package com.uteq.backend.dto;

import java.util.Arrays;
import java.util.Objects;

// equals()/hashCode()/toString() explícitos: el record compararía el
// campo byte[] por referencia; con Arrays se compara por contenido.
// No usar esta clase como clave en HashMap ni como elemento de HashSet.
public record EvidenciaDanoArchivoDTO(
        String archivoTipo,
        byte[] archivoBytes
) {
    @Override
    /**
     * Executes the equals operation.
     * @param o value required by the operation
     * @return operation result
     */
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EvidenciaDanoArchivoDTO otro)) return false;
        return Objects.equals(archivoTipo, otro.archivoTipo) && Arrays.equals(archivoBytes, otro.archivoBytes);
    }

    @Override
    /**
     * Executes the hashCode operation.
     * @return operation result
     */
    public int hashCode() {
        return 31 * Objects.hashCode(archivoTipo) + Arrays.hashCode(archivoBytes);
    }

    @Override
    /**
     * Executes the toString operation.
     * @return operation result
     */
    public String toString() {
        return "EvidenciaDanoArchivoDTO[archivoTipo=" + archivoTipo + ", archivoBytes=" + Arrays.toString(archivoBytes) + "]";
    }
}
