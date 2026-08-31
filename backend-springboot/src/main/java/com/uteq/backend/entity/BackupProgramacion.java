package com.uteq.backend.entity;

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
public class BackupProgramacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creado_por", nullable = false)
    private Long creadoPor;

    @Column(name = "cada_horas")
    private Integer cadaHoras;  // 1-23, NULL si se usa cadaDias

    @Column(name = "cada_dias")
    private Integer cadaDias;   // 1-30, NULL si se usa cadaHoras

    @Column(name = "formato", nullable = false, length = 10)
    private String formato; // "sql" o "csv"

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "ultima_ejecucion")
    private OffsetDateTime ultimaEjecucion;

    // Getter explícito para boolean 'activo' (Lombok a veces genera getActivo en vez de isActivo)
    public boolean isActivo() { return activo; }

    // Restricción XOR: exactamente uno de los dos debe tener valor
    @PrePersist
    @PreUpdate
    private void validarXor() {
        long count = ((getCadaHoras() != null) ? 1 : 0) + ((getCadaDias() != null) ? 1 : 0);
        if (count != 1) {
            throw new IllegalArgumentException("Debe definirse exactamente uno de: cada_horas o cada_dias");
        }
    }
}