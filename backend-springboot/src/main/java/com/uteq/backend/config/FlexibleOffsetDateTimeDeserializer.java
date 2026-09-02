package com.uteq.backend.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

public class FlexibleOffsetDateTimeDeserializer extends StdDeserializer<OffsetDateTime> {
    private static final ZoneOffset DEFAULT_OFFSET = ZoneOffset.ofHours(-5); // America/Guayaquil
    private static final DateTimeFormatter FMT = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral('T')
            .appendPattern("HH:mm")
            .optionalStart().appendPattern(":ss").optionalEnd()
            .optionalStart().appendPattern(".SSS").optionalEnd()
            .optionalStart().appendOffsetId().optionalEnd()
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
            .parseDefaulting(ChronoField.NANO_OF_SECOND, 0)
            .parseDefaulting(ChronoField.OFFSET_SECONDS, DEFAULT_OFFSET.getTotalSeconds())
            .toFormatter();

    public FlexibleOffsetDateTimeDeserializer() { super(OffsetDateTime.class); }

    @Override
    public OffsetDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getText();
        if (text == null || text.isBlank()) return null;
        text = text.trim();
        // Normaliza: si viene con espacio en vez de T, corrige
        text = text.replace(' ', 'T');
        try {
            return OffsetDateTime.parse(text, FMT);
        } catch (Exception e) {
            // Fallback: intenta LocalDateTime
            try {
                LocalDateTime ldt = LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                return ldt.atOffset(DEFAULT_OFFSET);
            } catch (Exception ex) {
                throw new IOException("Formato de fecha no soportado: " + text, ex);
            }
        }
    }
}
