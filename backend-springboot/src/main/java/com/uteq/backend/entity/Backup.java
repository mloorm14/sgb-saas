package com.uteq.backend.entity;

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

    @Column(name = "creado_por", nullable = false)
    private Long creadoPor;

    @Column(name = "desde", nullable = false)
    private OffsetDateTime desde;

    @Column(name = "hasta", nullable = false)
    private OffsetDateTime hasta;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "backups_tablas", joinColumns = @JoinColumn(name = "backup_id"))
    @Column(name = "tabla")
    private Set<String> tablas;

    @Column(name = "formato", nullable = false, length = 10)
    private String formato; // "sql" o "csv"

    @Column(name = "ruta", nullable = false)
    private String ruta; // path o URL donde está el archivo

    @Column(name = "tamano_bytes")
    private Long tamanoBytes;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado; // "COMPLETADO", "FALLIDO"

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;
}