package com.uteq.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import jakarta.persistence.Column;
import java.time.OffsetDateTime;

@Entity
@Table(name = "configuracion_respaldo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfigurationBackup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonProperty("habilitado")
        @Column(name = "habilitado")  private Boolean enabled;

    @Column(name = "frecuencia_horas", nullable = false)
    @JsonProperty("frecuenciaHoras")
    private Integer frequencyTimes;

    @JsonProperty("diasRetencion")
    @Column(name = "dias_retencion", nullable = false)  private Integer daysRetention;

    @Column(name = "ultima_ejecucion")
    @JsonProperty("ultimaEjecucion")
    private OffsetDateTime lastExecution;

    @JsonProperty("proximaEjecucion")
        @Column(name = "proxima_ejecucion")  private OffsetDateTime nextExecution;

    @Column(name = "actualizado_por")
    private Long updatedBy;

    @Column(name = "actualizado_en")
    @JsonProperty("actualizadoEn")
    private OffsetDateTime updated;

    /**
     * Checks whether is habilitado.
     *
     * @return true when the operation succeeds
     */

    public boolean isEnabled() {
        return enabled != null && enabled;
    }

    @PrePersist
    @PreUpdate
    private void updateMarkTime() {
        updated = OffsetDateTime.now();
    }
}
