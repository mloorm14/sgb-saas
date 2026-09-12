package com.uteq.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
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
import java.util.UUID;

/**
 * Mapea la tabla {@code usuarios} del schema normalizado (db/schema.sql):
 * el antiguo campo {@code rol} (texto libre) se reemplaza por la relación
 * muchos-a-muchos {@link #roles} vía la tabla puente {@code usuario_roles},
 * y el antiguo {@code activo} (boolean) por {@link #status}, una referencia
 * a {@link StatusUser}.
 * <p>
 * Decisión de mapeo — {@code estado} y {@code roles} van {@code EAGER}
 * (no {@code LAZY}) a propósito, aunque {@code Book.statusBookId} sí usa
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
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @JsonProperty("nombre")
    @Column(name = "nombre", nullable = false)  private String name;

    @Column(name = "apellido", nullable = false)
    private String lastName;

    @JsonProperty("correo")
    @Column(name = "correo", nullable = false, unique = true)  private String email;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "identificacion_usuario", length = 20)
    @JsonProperty("identificacionUsuario")
    private String identificacionUser;

    // Modulo 8 (credencial QR): UUID generado una sola vez por la migracion
    // V5, nunca reemplazado por la app. Es lo unico que el codigo QR codifica
    // -- no el correo ni la identificacion en claro (ver CredencialQrService).
    @JsonProperty("credencialQrToken")
    @Column(name = "credencial_qr_token", nullable = false, unique = true, insertable = false, updatable = false)  private UUID credentialQrToken;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "estado_id", nullable = false)
    private StatusUser status;

    @Column(name = "correo_verificado", nullable = false)
    @JsonProperty("correoVerificado")
    private boolean emailVerified;

    @Column(name = "fecha_registro", nullable = false)
    @JsonProperty("fechaRegistro")
    private Instant dateRegistration;

    @Column(name = "actualizado_en", nullable = false)
    @JsonProperty("actualizadoEn")
    private Instant updated;

    // F8-gerente (V38): quién creó este usuario. NULL = histórico/sistema
    // (ADMIN lo ve igual). Sin relación JPA a propósito — solo FK libre de
    // joins, mismo criterio que Prestamo/Reservacion.
    @Column(name = "creado_por")
    @JsonProperty("creadoPor")
    private Long createdBy;

    @Builder.Default
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "usuario_roles",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "rol_id")
    )
    private Set<Role> roles = new HashSet<>();
}
