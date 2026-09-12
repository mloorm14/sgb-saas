package com.uteq.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.Set;

@Entity
@Table(name = "backup_programacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackupSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonProperty("creadoPor")
    @Column(name = "creado_por", nullable = false)  private Long createdBy;

    @JsonProperty("cadaHoras")
    @Column(name = "cada_horas") private Integer everyTimes;  // 1-23, NULL si se usa cadaDias

    @JsonProperty("cadaDias")
    @Column(name = "cada_dias") private Integer everyDays;   // 1-30, NULL si se usa cadaHoras

    @JsonProperty("formato")
    @Column(name = "formato", nullable = false, length = 10) private String format; // "sql" o "csv"

    @JsonProperty("activo")
    @Column(name = "activo", nullable = false)  private Boolean active;

    @Column(name = "creado_en", nullable = false, updatable = false)
    @JsonProperty("creadoEn")
    private OffsetDateTime created;

    @JsonProperty("ultimaEjecucion")
        @Column(name = "ultima_ejecucion")  private OffsetDateTime lastExecution;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "backup_programacion_tablas", joinColumns = @JoinColumn(name = "programacion_id"))
    @JsonProperty("tablas")
    @Column(name = "tabla")  private Set<String> tables;

    // Getter explícito para boolean 'activo' (Lombok a veces genera getActivo en vez de isActivo).
    // Se usa Boolean.TRUE.equals para evitar NPE por auto-unboxing cuando activo es null.
    /**
     * Verifica is active y devuelve el resultado de la comprobacion.
     *
     * @return true cuando la comprobacion se cumple; false en caso contrario
     */
    public boolean isActive() { return Boolean.TRUE.equals(active); }

    // Restricción XOR: exactamente uno de los dos debe tener valor
    @PrePersist
    @PreUpdate
    private void validateXor() {
        long count = ((getEveryTimes() != null) ? 1 : 0) + ((getEveryDays() != null) ? 1 : 0);
        if (count != 1) {
            throw new IllegalArgumentException("Debe definirse exactamente uno de: cada_horas o cada_dias");
        }
    }
}
