package com.uteq.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mapea el catálogo {@code estados_multa} (PENDIENTE, PAGADA, ANULADA --
 * ver db/seed.sql), que hasta ahora no tenía entidad JPA propia:
 * {@link Multa#getEstadoMultaId()} se usaba siempre como Integer plano y
 * los procedimientos de db/procs/ resolvían el id por nombre dentro de
 * SQL. Se agrega para poder resolver el id del estado por su nombre desde
 * Java (mismo patrón que {@link EstadoPrestamo}), necesario para calcular
 * las multas pendientes de un usuario en la ventanilla de préstamos
 * ({@code PrestamosGestionService}) sin hardcodear identificadores.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "estados_multa")
public class EstadoMulta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank
    @Size(max = 30)
    @Column(nullable = false, unique = true, length = 30)
    private String nombre;
}
