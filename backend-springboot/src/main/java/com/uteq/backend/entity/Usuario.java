package com.uteq.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Mapea la tabla {@code usuarios} del schema normalizado (db/schema.sql):
 * el antiguo campo {@code rol} (texto libre) se reemplaza por la relación
 * muchos-a-muchos {@link #roles} vía la tabla puente {@code usuario_roles},
 * y el antiguo {@code activo} (boolean) por {@link #estado}, una referencia
 * a {@link EstadoUsuario}.
 * <p>
 * Decisión de mapeo — {@code estado} y {@code roles} van {@code EAGER}
 * (no {@code LAZY}) a propósito, aunque {@link Libro#getEstado()} sí usa
 * LAZY: Usuario se carga en varios puntos que NO están dentro de un límite
 * {@code @Transactional} explícito (p.ej. {@code UserDetailsServiceImpl
 * #loadUserByUsername}, invocado por el filtro de seguridad en cada
 * request, y {@code AuthService#login}/{@code #refresh}, que usan el
 * usuario recién leído para construir el JWT inmediatamente después de que
 * el repositorio retorna). Con LAZY, cualquiera de esos puntos lanzaría
 * {@code LazyInitializationException} al intentar leer el rol/estado fuera
 * de sesión. Ambas asociaciones son pequeñas (una fila / un puñado de
 * roles) y se necesitan siempre que se carga un Usuario para autenticación,
 * así que EAGER es la opción más simple que no obliga a auditar/anotar
 * {@code @Transactional} en cada punto de llamada actual y futuro.
 */
@Entity
@Table(name = "usuarios")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "apellido", nullable = false)
    private String apellido;

    @Column(name = "correo", nullable = false, unique = true)
    private String correo;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "identificacion_usuario", length = 20)
    private String identificacionUsuario;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "estado_id", nullable = false)
    private EstadoUsuario estado;

    @Column(name = "correo_verificado", nullable = false)
    private boolean correoVerificado;

    @Column(name = "fecha_registro", nullable = false)
    private Instant fechaRegistro;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Builder.Default
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "usuario_roles",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "rol_id")
    )
    private Set<Rol> roles = new HashSet<>();
}
