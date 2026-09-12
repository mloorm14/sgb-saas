package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record SummaryCategoryAuditDTO( @JsonProperty("tablaAfectada") String tableAfectada, @JsonProperty("totalEventos") long totalEvents, @JsonProperty("eventosHoy") long eventsToday, @JsonProperty("ultimoEvento") OffsetDateTime lastEvent, @JsonProperty("requiereRevision") boolean requiereReview
) {
}
