package com.uteq.backend.entity;

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
 * Mismo patrón que {@link EstadoUsuario}/{@link Rol}: entidad simple, sin
 * lógica de negocio propia — la lectura/cache/parseo vive en
 * {@code ConfiguracionSistemaService}.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "configuracion_sistema")
public class ConfiguracionSistema {

    @Id
    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String clave;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String valor;
}
