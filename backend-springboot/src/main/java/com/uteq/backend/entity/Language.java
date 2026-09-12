package com.uteq.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "idiomas")
public class Language {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank
    @Size(max = 50)
    @JsonProperty("nombre")
    @Column(name = "nombre", nullable = false, unique = true, length = 50)  private String name;

    @NotBlank
    @Size(max = 5)
    @Column(name = "codigo_iso", nullable = false, unique = true, length = 5)  private String code;
}