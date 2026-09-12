package com.uteq.backend.service;

import com.uteq.backend.dto.UserListingResponseDTO;
import com.uteq.backend.entity.StatusUser;
import com.uteq.backend.entity.Role;
import com.uteq.backend.entity.User;
import com.uteq.backend.entity.UserReasonChange;
import com.uteq.backend.repository.StatusFineRepository;
import com.uteq.backend.repository.StatusUserRepository;
import com.uteq.backend.repository.FineRepository;
import com.uteq.backend.repository.RoleRepository;
import com.uteq.backend.repository.UserReasonChangeRepository;
import com.uteq.backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Panel de administración de usuarios: listado paginado, cambio de rol y de estado.
 * GERENTE solo opera usuarios creados por él; DELETE sigue solo ADMIN.
 */
@Service
public class UserAdminService {

    private static final String USUARIO_NO_ENCONTRADO = "Usuario no encontrado: ";
    private static final String ROL_NO_ENCONTRADO = "Rol no válido: ";
    private static final String ESTADO_NO_ENCONTRADO = "Estado no válido: ";
    // GERENTE solo opera LECTOR/BIBLIOTECARIO creados por él.
    private static final Set<String> ROLES_GERENTE_PERMITIDOS = Set.of("LECTOR", "BIBLIOTECARIO");
    private static final Set<String> ESTADOS_GERENTE_PERMITIDOS = Set.of("ACTIVO", "INACTIVO");

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final StatusUserRepository statusUserRepo;
    private final FineRepository fineRepo;
    private final StatusFineRepository statusFineRepo;
    private final UserReasonChangeRepository userReasonChangeRepo;

    public UserAdminService(UserRepository userRepo,
                                RoleRepository roleRepo,
                                StatusUserRepository statusUserRepo,
                                FineRepository fineRepo,
                                StatusFineRepository statusFineRepo,
                                UserReasonChangeRepository userReasonChangeRepo) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.statusUserRepo = statusUserRepo;
        this.fineRepo = fineRepo;
        this.statusFineRepo = statusFineRepo;
        this.userReasonChangeRepo = userReasonChangeRepo;
    }

    /**
     * Devuelve la página de usuarios con el filtro de texto dado para el panel de administración.
     * Delegada en la variante con alcance por rol (ver {@link #list}), sin restringir por creador.
     *
     * @param filter texto libre para buscar por nombre, apellido o correo; nulo o vacío lista todo
     * @param pageable paginación y orden solicitados por el panel
     * @return página de filas resumidas con roles, estado y marca de multas pendientes
     */
    @Transactional(readOnly = true)
    /**
     * Lists user Listado Response DTO records.
     *
     * @param filter text value used to scope this user Listado Response DTO records
     * @param pageable pagination information used to scope this user Listado Response DTO records
     * @return page of user Listado Response data transfer object for the requested pagination
     */
    public Page<UserListingResponseDTO> list(String filter, Pageable pageable) {
        return list(filter, pageable, null, false);
    }

    // GERENTE filtra por creado_por = miId; ADMIN ve todo.
    /**
     * Devuelve la página de usuarios aplicando filtro de texto y alcance por creador para el panel de administración.
     * Cuando quien consulta es GERENTE (o se pide solo lo propio), restringe a los usuarios creados por él;
     * ADMIN ve todo. Enriquece cada fila con la marca de multas pendientes en una sola consulta por lote para evitar N+1.
     *
     * @param filter texto libre para buscar por nombre, apellido o correo; nulo o vacío lista todo
     * @param pageable paginación y orden solicitados por el panel
     * @param authentication identidad autenticada desde el JWT de la que se deriva el rol y el creador; nula significa sin restricción
     * @param soloMios cuando es verdadero restringe a los usuarios creados por quien consulta aunque no sea GERENTE
     * @return página de filas resumidas con roles, estado y marca de multas pendientes
     */
    @Transactional(readOnly = true)
    /**
     * Lists user Listado Response DTO records.
     *
     * @param filter text value used to scope this user Listado Response DTO records
     * @param pageable pagination information used to scope this user Listado Response DTO records
     * @param authentication authentication of the caller used to scope this user Listado Response DTO records
     * @param soloMios flag used to scope this user Listado Response DTO records
     * @return page of user Listado Response data transfer object for the requested pagination
     */
    public Page<UserListingResponseDTO> list(String filter, Pageable pageable,
                                                  Authentication authentication, boolean soloMios) {
        String text = filter == null ? "" : filter.trim();
        Long createdBy = null;
        if (authentication != null && (soloMios || esManager(authentication))) {
            createdBy = resolveIdByEmail(authentication.getName());
        }
        Page<User> page = userRepo.searchWithFilters(text, createdBy, pageable);

        // Batch query: multas pendientes de la página en una sola consulta (evita N+1).
        List<Long> ids = page.getContent().stream().map(User::getId).toList();
        Integer statusPendingId = statusFineRepo.findByName("PENDIENTE")
                .map(e -> e.getId())
                .orElse(null);
        Set<Long> idsWithFines = Set.copyOf(
                (statusPendingId != null && !ids.isEmpty())
                        ? fineRepo.findUserIdsWithFinesPendientes(ids, statusPendingId)
                        : List.of());

        return page.map(u -> toListingDTO(u, idsWithFines.contains(u.getId())));
    }

    /**
     * Reemplaza los roles del usuario por uno solo para ajustar sus permisos en la plataforma.
     * GERENTE solo puede reasignar LECTOR o BIBLIOTECARIO entre usuarios creados por él; ADMIN opera sin esa restricción.
     *
     * @param userId identificador del usuario cuyo rol se reemplaza
     * @param freshRole nombre del rol destino que quedará como único rol vigente
     * @param authentication identidad autenticada desde el JWT de la que se deriva el rol del ejecutor y su alcance
     * @throws jakarta.persistence.EntityNotFoundException si no existe ningún usuario con ese identificador
     * @throws IllegalArgumentException si el nombre de rol no existe en el catálogo
     * @throws org.springframework.security.access.AccessDeniedException si un GERENTE intenta asignar un rol fuera de su alcance o tocar usuarios ajenos
     */
    @Transactional
    /**
     * Changes user Admin.
     *
     * @param userId numeric identifier used to scope this user Admin
     * @param freshRole text value used to scope this user Admin
     * @param authentication authentication of the caller used to scope this user Admin
     * @throws org when the user Admin cannot be processed with the given input
     * @throws EntityNotFoundException when the user Admin cannot be processed with the given input
     * @throws IllegalArgumentException when the user Admin cannot be processed with the given input
     */
    public void changeRole(Long userId, String freshRole, Authentication authentication) {
        User user = userRepo.findByIdWithStatusAndRoles(userId)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + userId));
        Role role = roleRepo.findByName(freshRole)
                .orElseThrow(() -> new IllegalArgumentException(ROL_NO_ENCONTRADO + freshRole));
        Long executorId = resolveIdByEmail(authentication == null ? null : authentication.getName());
        // GERENTE solo cambia rol a sus creados y solo LECTOR/BIBLIOTECARIO.
        if (esManager(authentication)) {
            if (!ROLES_GERENTE_PERMITIDOS.contains(freshRole)) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "GERENTE solo puede asignar roles LECTOR o BIBLIOTECARIO");
            }
            if (user.getCreatedBy() == null || !user.getCreatedBy().equals(executorId)) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "GERENTE solo puede modificar usuarios creados por él");
            }
        }

        Set<Role> roles = new HashSet<>();
        roles.add(role);
        user.setRoles(roles);
        user.setUpdated(Instant.now());
        userRepo.save(user);
    }

    /**
     * Mueve al usuario al estado indicado para bloquearlo o reactivarlo manualmente desde el panel.
     * {@code motivo} queda registrado en {@code usuario_motivos_cambio}
     * (V50); el cambio de estado en sí lo audita
     * {@code trg_auditoria_usuarios} sobre {@code bitacora_auditoria}.
     *
     * @param userId identificador del usuario cuyo estado se cambia
     * @param freshStatus nombre del estado destino en el catálogo de estados de usuario
     * @param reason justificación escrita por el ejecutor que queda guardada en el historial de motivos
     * @param authentication identidad autenticada desde el JWT de la que se deriva el rol del ejecutor y su alcance
     * @throws jakarta.persistence.EntityNotFoundException si no existe ningún usuario con ese identificador
     * @throws IllegalArgumentException si el nombre de estado no existe en el catálogo
     * @throws org.springframework.security.access.AccessDeniedException si un GERENTE intenta un estado fuera de ACTIVO/INACTIVO o tocar usuarios ajenos
     */
     @Transactional
    /**
     * Changes user Admin.
     *
     * @param userId numeric identifier used to scope this user Admin
     * @param freshStatus text value used to scope this user Admin
     * @param reason text value used to scope this user Admin
     * @param authentication authentication of the caller used to scope this user Admin
     * @throws org when the user Admin cannot be processed with the given input
     * @throws EntityNotFoundException when the user Admin cannot be processed with the given input
     * @throws IllegalArgumentException when the user Admin cannot be processed with the given input
     */
    public void changeStatus(Long userId, String freshStatus, String reason, Authentication authentication) {
        User user = userRepo.findByIdWithStatusAndRoles(userId)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + userId));
        StatusUser status = statusUserRepo.findByName(freshStatus)
                .orElseThrow(() -> new IllegalArgumentException(ESTADO_NO_ENCONTRADO + freshStatus));
        Long executorId = resolveIdByEmail(authentication == null ? null : authentication.getName());
        // GERENTE solo bloquea/reactiva (ACTIVO/INACTIVO) a sus creados.
        if (esManager(authentication)) {
            if (!ESTADOS_GERENTE_PERMITIDOS.contains(freshStatus)) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "GERENTE solo puede bloquear o reactivar usuarios");
            }
            if (user.getCreatedBy() == null || !user.getCreatedBy().equals(executorId)) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "GERENTE solo puede modificar usuarios creados por él");
            }
        }

        Integer statusAnteriorId = user.getStatus().getId();
        user.setStatus(status);
        user.setUpdated(Instant.now());
        userRepo.save(user);
        // trg_auditoria_usuarios (V49) audita el UPDATE de la fila de
        // usuarios, pero solo ve columnas (antes/después) -- no puede
        // reconstruir "motivo", que llega como parámetro suelto fuera de la
        // fila. usuario_motivos_cambio (V50) es COMPLEMENTARIA a esa
        // auditoría, no un reemplazo: guarda justo el dato que el trigger no
        // puede ver (ver OBS-28).
        registerReasonChange(userId, "CAMBIO_ESTADO", statusAnteriorId, status.getId(), reason, executorId);
    }

    // El ejecutor se resuelve desde el JWT autenticado, nunca desde el body.
    /**
     * Da de alta un usuario ya verificado y activo desde el panel para que pueda operar sin pasar por
     * la confirmación por correo. El ejecutor se deriva del JWT autenticado y queda como creador del registro.
     * GERENTE solo puede crear LECTOR o BIBLIOTECARIO.
     *
     * @param dto solicitud con nombre, apellido, correo, contraseña en claro sin cifrar y nombre del rol a asignar
     * @param authentication identidad autenticada desde el JWT de la que se deriva el ejecutor y su alcance
     * @return vista resumida del usuario persistido con identificador, nombre, correo y roles asignados
     * @throws EmailYaRegistradoException si ya existe un usuario con el correo solicitado
     * @throws org.springframework.security.access.AccessDeniedException si un GERENTE intenta crear un rol fuera de su alcance
     * @throws IllegalArgumentException si el nombre de rol no existe en el catálogo
     * @throws IllegalStateException si falta la fila de catálogo del estado ACTIVO
     */
    @Transactional
    public com.uteq.backend.dto.UserResponseDTO createUser(com.uteq.backend.dto.CreateUserAdminRequestDTO dto, Authentication authentication) {
        userRepo.findByEmail(dto.email()).ifPresent(u -> { throw new com.uteq.backend.service.EmailYaRegistradoException("El correo ya está registrado: " + dto.email()); });
        // GERENTE solo crea LECTOR o BIBLIOTECARIO.
        if (esManager(authentication) && !ROLES_GERENTE_PERMITIDOS.contains(dto.role())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "GERENTE solo puede crear usuarios LECTOR o BIBLIOTECARIO");
        }
        Role role = roleRepo.findByName(dto.role()).orElseThrow(() -> new IllegalArgumentException(ROL_NO_ENCONTRADO + dto.role()));
        StatusUser statusActive = statusUserRepo.findByName("ACTIVO").orElseThrow(() -> new IllegalStateException("Estado ACTIVO no existe"));
        java.util.Set<Role> roles = new java.util.HashSet<>(); roles.add(role);
        Long executorId = resolveIdByEmail(authentication.getName());
        User user = User.builder().name(dto.name()).lastName(dto.lastName()).email(dto.email()).passwordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode(dto.password())).status(statusActive).emailVerified(true).roles(roles).dateRegistration(Instant.now()).updated(Instant.now()).createdBy(executorId).build();
        User guardado = userRepo.save(user);
        java.util.List<String> rolesStr = guardado.getRoles().stream().map(Role::getName).toList();
        return new com.uteq.backend.dto.UserResponseDTO(guardado.getId(), guardado.getName(), guardado.getEmail(), rolesStr);
    }

    /**
     * Desactiva la cuenta pasándola al estado INACTIVO para retirarla de la operación sin borrar su fila.
     * Persiste además el motivo en la tabla de motivos de cambio, complemento del trigger de auditoría
     * que solo ve columnas antes/después y no recibe el motivo como parámetro.
     *
     * @param userId identificador del usuario a desactivar
     * @param reason justificación escrita por el ejecutor que queda guardada en el historial de motivos
     * @param authentication identidad autenticada desde el JWT de la que se deriva el ejecutor del cambio
     * @throws jakarta.persistence.EntityNotFoundException si no existe ningún usuario con ese identificador
     * @throws IllegalStateException si falta la fila de catálogo del estado INACTIVO
     */
    @Transactional
    /**
     * Deletes user Admin.
     *
     * @param userId numeric identifier used to scope this user Admin
     * @param reason text value used to scope this user Admin
     * @param authentication authentication of the caller used to scope this user Admin
     * @throws EntityNotFoundException when the user Admin cannot be processed with the given input
     * @throws IllegalStateException when the user Admin cannot be processed with the given input
     */
    public void deleteUser(Long userId, String reason, Authentication authentication) {
        User user = userRepo.findByIdWithStatusAndRoles(userId).orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + userId));
        StatusUser inactivo = statusUserRepo.findByName("INACTIVO").orElseThrow(() -> new IllegalStateException("Estado INACTIVO no existe"));
        Integer statusAnteriorId = user.getStatus().getId();
        user.setStatus(inactivo);
        user.setUpdated(Instant.now());
        userRepo.save(user);
        // Mismo caso que cambiarEstado(): trg_auditoria_usuarios audita el
        // UPDATE de la fila, usuario_motivos_cambio (V50) guarda el motivo
        // que el trigger no puede ver (ver OBS-28).
        Long executorId = resolveIdByEmail(authentication == null ? null : authentication.getName());
        registerReasonChange(userId, "ELIMINACION", statusAnteriorId, inactivo.getId(), reason, executorId);
    }

    // usuario_motivos_cambio (V50): historial dedicado, complementario a
    // bitacora_auditoria/trg_auditoria_usuarios -- ver OBS-28.
    private void registerReasonChange(Long userId, String typeChange, Integer statusAnteriorId,
                                        Integer statusFreshId, String reason, Long executorId) {
        UserReasonChange row = UserReasonChange.builder()
                .userId(userId)
                .typeChange(typeChange)
                .statusAnterior(statusAnteriorId)
                .statusFresh(statusFreshId)
                .reason(reason)
                .executedBy(executorId)
                .created(OffsetDateTime.now())
                .build();
        userReasonChangeRepo.save(row);
    }

    // V50/OBS-28: historial de motivos de cambio de estado/eliminación,
    // más reciente primero.
    /**
     * Recupera el historial de motivos de cambio de estado y desactivaciones del usuario, del más
     * reciente al más antiguo, para explicar en el panel por qué la cuenta llegó a su estado actual.
     *
     * @param userId identificador del usuario cuyo historial se consulta
     * @return lista de registros con tipo de cambio, estado anterior y nuevo, motivo, ejecutor y fecha, ordenada por fecha descendente
     */
    @Transactional(readOnly = true)
    public List<com.uteq.backend.dto.UserReasonChangeResponseDTO> historyReasons(Long userId) {
        return userReasonChangeRepo.findByUserIdOrderByCreatedDesc(userId).stream()
                .map(m -> new com.uteq.backend.dto.UserReasonChangeResponseDTO(
                        m.getId(), m.getTypeChange(), m.getStatusAnterior(), m.getStatusFresh(),
                        m.getReason(), m.getExecutedBy(), m.getCreated()))
                .toList();
    }

    private Long resolveIdByEmail(String email) {
        if (email == null) throw new EntityNotFoundException(USUARIO_NO_ENCONTRADO + "null");
        String normalized = email.trim().toLowerCase();
        // Usa IgnoreCase para evitar 404 por mayúsculas en el JWT.
        Optional<User> opt = userRepo.findByEmailIgnoreCase(normalized);
        if (opt.isPresent()) return opt.get().getId();
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + email))
                .getId();
    }

    // GERENTE opera solo sobre sus creados; ADMIN sin restricción.
    private boolean esManager(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_GERENTE".equals(a.getAuthority()));
    }

    private UserListingResponseDTO toListingDTO(User user, boolean finesPendientes) {
        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .toList();
        return new UserListingResponseDTO(
                user.getId(),
                user.getName(),
                user.getLastName(),
                user.getEmail(),
                roles,
                user.getStatus().getName(),
                finesPendientes);
    }
}
