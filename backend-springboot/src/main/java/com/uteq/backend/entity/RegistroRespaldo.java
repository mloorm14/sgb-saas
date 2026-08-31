package com.uteq.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "registros_respaldo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistroRespaldo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tipo", nullable = false, length = 20)
    private String tipo;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado;

    @Column(name = "nombre_archivo", length = 255)
    private String nombreArchivo;

    @Column(name = "tamano_archivo_bytes")
    private Long tamanoArchivoBytes;

    @Column(name = "ruta_r2", columnDefinition = "TEXT")
    private String rutaR2;

    @Column(name = "mensaje_error", columnDefinition = "TEXT")
    private String mensajeError;

    @Column(name = "ejecutado_por")
    private Long ejecutadoPor;

    @Column(name = "iniciado_en", insertable = false, updatable = false)
    private OffsetDateTime iniciadoEn;

    @Column(name = "finalizado_en")
    private OffsetDateTime finalizadoEn;
}
