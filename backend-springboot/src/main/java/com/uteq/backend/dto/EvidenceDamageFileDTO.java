package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.Objects;

// equals()/hashCode()/toString() explícitos: el record compararía el
// campo byte[] por referencia; con Arrays se compara por contenido.
// No usar esta clase como clave en HashMap ni como elemento de HashSet.
public record EvidenceDamageFileDTO( @JsonProperty("archivoTipo") String fileType, @JsonProperty("archivoBytes") byte[] fileBytes
) {
    @Override
    /**
     * Handles equals.
     *
     * @param o Object used to scope this equals
     * @return true when the check succeeds
     */
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EvidenceDamageFileDTO otro)) return false;
        return Objects.equals(fileType, otro.fileType) && Arrays.equals(fileBytes, otro.fileBytes);
    }

    @Override
    /**
     * Checks whether hash code.
     *
     * @return generated identifier of the affected record
     */
    public int hashCode() {
        return 31 * Objects.hashCode(fileType) + Arrays.hashCode(fileBytes);
    }

    @Override
    /**
     * Handles to string.
     *
     * @return resulting text payload
     */
    public String toString() {
        return "EvidenciaDanoArchivoDTO[archivoTipo=" + fileType + ", archivoBytes=" + Arrays.toString(fileBytes) + "]";
    }
}
