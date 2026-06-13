package com.uteq.backend;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "editoriales")
public class Editorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, unique = true, length = 120)
    private String nombre;

    @Column(name = "creado_en", updatable = false)
    private OffsetDateTime creadoEn;

    @PrePersist
    private void antesDeGuardar() {
        this.creadoEn = OffsetDateTime.now();
    }
}