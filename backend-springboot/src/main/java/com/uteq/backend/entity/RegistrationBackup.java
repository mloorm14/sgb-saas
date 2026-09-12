package com.uteq.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import jakarta.persistence.Column;
import java.time.OffsetDateTime;

@Entity
@Table(name = "registros_respaldo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrationBackup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonProperty("tipo")
    @Column(name = "tipo", nullable = false, length = 20)  private String type;

    @Column(name = "estado", nullable = false, length = 20)
    @JsonProperty("estado")
    private String status;

    @JsonProperty("nombreArchivo")
    @Column(name = "nombre_archivo", length = 255)  private String nameFile;

    @Column(name = "tamano_archivo_bytes")
    @JsonProperty("tamanoArchivoBytes")
    private Long sizeFileBytes;

    @JsonProperty("rutaR2")
    @Column(name = "ruta_r2", columnDefinition = "TEXT")  private String pathR2;

    @Column(name = "mensaje_error", columnDefinition = "TEXT")
    @JsonProperty("mensajeError")
    private String messageError;

    @Column(name = "ejecutado_por")
    @JsonProperty("ejecutadoPor")
    private Long executedBy;

    @JsonProperty("iniciadoEn")
    @Column(name = "iniciado_en", insertable = false, updatable = false)  private OffsetDateTime started;

    @JsonProperty("finalizadoEn")
    @Column(name = "finalizado_en")  private OffsetDateTime finished;
}
