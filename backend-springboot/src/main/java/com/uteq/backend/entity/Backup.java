package com.uteq.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Set;
import lombok.*;

@Entity
@Table(name = "backups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Backup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonProperty("creadoPor")
    @Column(name = "creado_por", nullable = false)  private Long createdBy;

    @Column(name = "desde", nullable = false)
    private OffsetDateTime from;

    @Column(name = "hasta", nullable = false)
    @JsonProperty("hasta")
    private OffsetDateTime until;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "backups_tablas", joinColumns = @JoinColumn(name = "backup_id"))
    @JsonProperty("tablas")
        @Column(name = "tabla")  private Set<String> tables;

    @Column(name = "formato", nullable = false, length = 10) private String format; // "sql" o "csv"

    @Column(name = "ruta", nullable = false) private String path; // path o URL donde está el archivo

    @Column(name = "tamano_bytes")
    private Long sizeBytes;

    @Column(name = "estado", nullable = false, length = 20) private String status; // "COMPLETADO", "FALLIDO"

    @Column(name = "tipo", nullable = false, length = 20) private String type; // "manual", "automatico"

    @JsonProperty("creadoEn")
    @Column(name = "creado_en", nullable = false, updatable = false)  private OffsetDateTime created;
}