package com.uteq.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Parámetros del sistema administrables por el rol ADMIN sin requerir un
 * despliegue nuevo (tabla {@code configuracion_sistema}: clave-valor).
 * Mismo patrón que {@link StatusUser}/{@link Role}: entidad simple, sin
 * lógica de negocio propia — la lectura/cache/parseo vive en
 * {@code ConfiguracionSistemaService}.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "configuracion_sistema")
public class ConfigurationSystem {

    @Id
    @NotBlank
    @Size(max = 50)
    @JsonProperty("clave")
    @Column(name = "clave", nullable = false, length = 50)  private String key;

    @NotBlank
    @Size(max = 200)
    @Column(name = "valor", nullable = false, length = 200)  private String value;
}
