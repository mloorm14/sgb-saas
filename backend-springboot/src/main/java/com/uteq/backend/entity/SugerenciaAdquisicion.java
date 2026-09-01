package com.uteq.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

// Mapea la tabla "sugerencias_adquisicion" (Módulo 9.3 del roadmap). El
// propio schema modela "estado" como VARCHAR con CHECK
// (PENDIENTE/APROBADA/RECHAZADA), no como catálogo FK aparte (a diferencia
// de estados_libro/estados_prestamo/etc.) -- se respeta tal cual está en
// db/schema.sql en vez de normalizarlo, no es parte del alcance de esta
// rama. FKs planos (usuarioId, revisadoPor), mismo criterio que
// Prestamo/Multa/Favorito.
@Data
@NoArgsConstructor
@Entity
@Table(name = "sugerencias_adquisicion")
public class SugerenciaAdquisicion {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADA = "APROBADA";
    public static final String RECHAZADA = "RECHAZADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false, length = 255)
    private String titulo;

    @Column(length = 150)
    private String autor;

    @Column(length = 13)
    private String isbn;

    @Column(columnDefinition = "TEXT")
    private String justificacion;

    @Column(nullable = false, length = 20)
    private String estado = PENDIENTE;

    @Column(name = "revisado_por")
    private Long revisadoPor;

    @Column(name = "proveedor_id")
    private Integer proveedorId;

    @Column(name = "creado_en", updatable = false)
    private OffsetDateTime creadoEn;

    @PrePersist
    private void antesDeGuardar() {
        this.creadoEn = OffsetDateTime.now();
        if (this.estado == null) {
            this.estado = PENDIENTE;
        }
    }
}
