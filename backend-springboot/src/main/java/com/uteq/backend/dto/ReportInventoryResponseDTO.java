package com.uteq.backend.dto;
import com.fasterxml.jackson.annotation.JsonProperty;


public record ReportInventoryResponseDTO( @JsonProperty("libroId") Long bookId, @JsonProperty("titulo") String title,
        String isbn, @JsonProperty("autorNombre") String authorName, @JsonProperty("categoriaNombre") String categoryName, @JsonProperty("stockDisponible") Short stockTotal, Short stockAvailable, @JsonProperty("estadoDisponibilidad") String statusAvailability, @JsonProperty("editorialNombre") String publisherName, @JsonProperty("proveedorNombre") String supplierName, @JsonProperty("idiomaNombre") String languageName, @JsonProperty("estadoLibroNombre") String statusBookName,
        @JsonProperty("anioPublicacion") Short yearPublication,
        @JsonProperty("ubicacionFisica") String locationPhysical
) {}
