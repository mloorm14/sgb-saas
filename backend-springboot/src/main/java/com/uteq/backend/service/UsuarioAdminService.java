package com.uteq.backend.service;

import com.uteq.backend.dto.UsuarioListadoResponseDTO;
import com.uteq.backend.entity.EstadoUsuario;
import com.uteq.backend.entity.Rol;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.entity.UsuarioMotivoCambio;
import com.uteq.backend.repository.EstadoMultaRepository;
import com.uteq.backend.repository.EstadoUsuarioRepository;
import com.uteq.backend.repository.MultaRepository;
import com.uteq.backend.repository.RolRepository;
import com.uteq.backend.repository.UsuarioMotivoCambioRepository;
import com.uteq.backend.repository.UsuarioRepository;
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
public class UsuarioAdminService {

    private static final String USUARIO_NO_ENCONTRADO = "Usuario no encontrado: ";
    private static final String ROL_NO_ENCONTRADO = "Rol no válido: ";
    private static final String ESTADO_NO_ENCONTRADO = "Estado no válido: ";
    // GERENTE solo opera LECTOR/BIBLIOTECARIO creados por él.
    private static final Set<String> ROLES_GERENTE_PERMITIDOS = Set.of("LECTOR", "BIBLIOTECARIO");
    private static final Set<String> ESTADOS_GERENTE_PERMITIDOS = Set.of("ACTIVO", "INACTIVO");

    private final UsuarioRepository usuarioRepo;
    private final RolRepository rolRepo;
    private final EstadoUsuarioRepository estadoUsuarioRepo;
    private final MultaRepository multaRepo;
    private final EstadoMultaRepository estadoMultaRepo;
    private final UsuarioMotivoCambioRepository usuarioMotivoCambioRepo;

    public UsuarioAdminService(UsuarioRepository usuarioRepo,
                                RolRepository rolRepo,
                                EstadoUsuarioRepository estadoUsuarioRepo,
                                MultaRepository multaRepo,
                                EstadoMultaRepository estadoMultaRepo,
                                UsuarioMotivoCambioRepository usuarioMotivoCambioRepo) {
        this.usuarioRepo = usuarioRepo;
        this.rolRepo = rolRepo;
        this.estadoUsuarioRepo = estadoUsuarioRepo;
        this.multaRepo = multaRepo;
        this.estadoMultaRepo = estadoMultaRepo;
        this.usuarioMotivoCambioRepo = usuarioMotivoCambioRepo;
    }

    /**
     * Devuelve la página de usuarios con el filtro de texto dado para el panel de administración.
     * Delegada en la variante con alcance por rol (ver {@link #listar}), sin restringir por creador.
     *
     * @param filtro texto libre para buscar por nombre, apellido o correo; nulo o vacío lista todo
     * @param pageable paginación y orden solicitados por el panel
     * @return página de filas resumidas con roles, estado y marca de multas pendientes
     */
    @Transactional(readOnly = true)
    /**
     * Executes the listar operation.
     * @param filtro value required by the operation
     * @param pageable value required by the operation
     * @return operation result
     */
    public Page<UsuarioListadoResponseDTO> listar(String filtro, Pageable pageable) {
        return listar(filtro, pageable, null, false);
    }

    // GERENTE filtra por creado_por = miId; ADMIN ve todo.
    /**
     * Devuelve la página de usuarios aplicando filtro de texto y alcance por creador para el panel de administración.
     * Cuando quien consulta es GERENTE (o se pide solo lo propio), restringe a los usuarios creados por él;
     * ADMIN ve todo. Enriquece cada fila con la marca de multas pendientes en una sola consulta por lote para evitar N+1.
     *
     * @param filtro texto libre para buscar por nombre, apellido o correo; nulo o vacío lista todo
     * @param pageable paginación y orden solicitados por el panel
     * @param authentication identidad autenticada desde el JWT de la que se deriva el rol y el creador; nula significa sin restricción
     * @param soloMios cuando es verdadero restringe a los usuarios creados por quien consulta aunque no sea GERENTE
     * @return página de filas resumidas con roles, estado y marca de multas pendientes
     */
    @Transactional(readOnly = true)
    /**
     * Executes the listar operation.
     * @param filtro value required by the operation
     * @param pageable value required by the operation
     * @param authentication value required by the operation
     * @param soloMios value required by the operation
     * @return operation result
     */
    public Page<UsuarioListadoResponseDTO> listar(String filtro, Pageable pageable,
                                                  Authentication authentication, boolean soloMios) {
        String texto = filtro == null ? "" : filtro.trim();
        Long creadoPor = null;
        if (authentication != null && (soloMios || esGerente(authentication))) {
            creadoPor = resolverIdPorCorreo(authentication.getName());
        }
        Page<Usuario> pagina = usuarioRepo.buscarConFiltros(texto, creadoPor, pageable);

        // Batch query: multas pendientes de la página en una sola consulta (evita N+1).
        List<Long> ids = pagina.getContent().stream().map(Usuario::getId).toList();
        Integer estadoPendienteId = estadoMultaRepo.findByNombre("PENDIENTE")
                .map(e -> e.getId())
                .orElse(null);
        Set<Long> idsConMultas = Set.copyOf(
                (estadoPendienteId != null && !ids.isEmpty())
                        ? multaRepo.findUsuarioIdsConMultasPendientes(ids, estadoPendienteId)
                        : List.of());

        return pagina.map(u -> toListadoDTO(u, idsConMultas.contains(u.getId())));
    }

    /**
     * Reemplaza los roles del usuario por uno solo para ajustar sus permisos en la plataforma.
     * GERENTE solo puede reasignar LECTOR o BIBLIOTECARIO entre usuarios creados por él; ADMIN opera sin esa restricción.
     *
     * @param usuarioId identificador del usuario cuyo rol se reemplaza
     * @param nuevoRol nombre del rol destino que quedará como único rol vigente
     * @param authentication identidad autenticada desde el JWT de la que se deriva el rol del ejecutor y su alcance
     * @throws jakarta.persistence.EntityNotFoundException si no existe ningún usuario con ese identificador
     * @throws IllegalArgumentException si el nombre de rol no existe en el catálogo
     * @throws org.springframework.security.access.AccessDeniedException si un GERENTE intenta asignar un rol fuera de su alcance o tocar usuarios ajenos
     */
    @Transactional
    /**
     * Executes the cambiarRol operation.
     * @param usuarioId value required by the operation
     * @param nuevoRol value required by the operation
     * @param authentication value required by the operation
     */
    public void cambiarRol(Long usuarioId, String nuevoRol, Authentication authentication) {
        Usuario usuario = usuarioRepo.findByIdWithEstadoAndRoles(usuarioId)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + usuarioId));
        Rol rol = rolRepo.findByNombre(nuevoRol)
                .orElseThrow(() -> new IllegalArgumentException(ROL_NO_ENCONTRADO + nuevoRol));
        Long ejecutorId = resolverIdPorCorreo(authentication == null ? null : authentication.getName());
        // GERENTE solo cambia rol a sus creados y solo LECTOR/BIBLIOTECARIO.
        if (esGerente(authentication)) {
            if (!ROLES_GERENTE_PERMITIDOS.contains(nuevoRol)) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "GERENTE solo puede asignar roles LECTOR o BIBLIOTECARIO");
            }
            if (usuario.getCreadoPor() == null || !usuario.getCreadoPor().equals(ejecutorId)) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "GERENTE solo puede modificar usuarios creados por él");
            }
        }

        Set<Rol> roles = new HashSet<>();
        roles.add(rol);
        usuario.setRoles(roles);
        usuario.setActualizadoEn(Instant.now());
        usuarioRepo.save(usuario);
    }

    /**
     * Mueve al usuario al estado indicado para bloquearlo o reactivarlo manualmente desde el panel.
     * {@code motivo} queda registrado en {@code usuario_motivos_cambio}
     * (V50); el cambio de estado en sí lo audita
     * {@code trg_auditoria_usuarios} sobre {@code bitacora_auditoria}.
     *
     * @param usuarioId identificador del usuario cuyo estado se cambia
     * @param nuevoEstado nombre del estado destino en el catálogo de estados de usuario
     * @param motivo justificación escrita por el ejecutor que queda guardada en el historial de motivos
     * @param authentication identidad autenticada desde el JWT de la que se deriva el rol del ejecutor y su alcance
     * @throws jakarta.persistence.EntityNotFoundException si no existe ningún usuario con ese identificador
     * @throws IllegalArgumentException si el nombre de estado no existe en el catálogo
     * @throws org.springframework.security.access.AccessDeniedException si un GERENTE intenta un estado fuera de ACTIVO/INACTIVO o tocar usuarios ajenos
     */
     @Transactional
    /**
     * Executes the cambiarEstado operation.
     * @param usuarioId value required by the operation
     * @param nuevoEstado value required by the operation
     * @param motivo value required by the operation
     * @param authentication value required by the operation
     */
    public void cambiarEstado(Long usuarioId, String nuevoEstado, String motivo, Authentication authentication) {
        Usuario usuario = usuarioRepo.findByIdWithEstadoAndRoles(usuarioId)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + usuarioId));
        EstadoUsuario estado = estadoUsuarioRepo.findByNombre(nuevoEstado)
                .orElseThrow(() -> new IllegalArgumentException(ESTADO_NO_ENCONTRADO + nuevoEstado));
        Long ejecutorId = resolverIdPorCorreo(authentication == null ? null : authentication.getName());
        // GERENTE solo bloquea/reactiva (ACTIVO/INACTIVO) a sus creados.
        if (esGerente(authentication)) {
            if (!ESTADOS_GERENTE_PERMITIDOS.contains(nuevoEstado)) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "GERENTE solo puede bloquear o reactivar usuarios");
            }
            if (usuario.getCreadoPor() == null || !usuario.getCreadoPor().equals(ejecutorId)) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "GERENTE solo puede modificar usuarios creados por él");
            }
        }

        Integer estadoAnteriorId = usuario.getEstado().getId();
        usuario.setEstado(estado);
        usuario.setActualizadoEn(Instant.now());
        usuarioRepo.save(usuario);
        // trg_auditoria_usuarios (V49) audita el UPDATE de la fila de
        // usuarios, pero solo ve columnas (antes/después) -- no puede
        // reconstruir "motivo", que llega como parámetro suelto fuera de la
        // fila. usuario_motivos_cambio (V50) es COMPLEMENTARIA a esa
        // auditoría, no un reemplazo: guarda justo el dato que el trigger no
        // puede ver (ver OBS-28).
        registrarMotivoCambio(usuarioId, "CAMBIO_ESTADO", estadoAnteriorId, estado.getId(), motivo, ejecutorId);
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
     * @throws CorreoYaRegistradoException si ya existe un usuario con el correo solicitado
     * @throws org.springframework.security.access.AccessDeniedException si un GERENTE intenta crear un rol fuera de su alcance
     * @throws IllegalArgumentException si el nombre de rol no existe en el catálogo
     * @throws IllegalStateException si falta la fila de catálogo del estado ACTIVO
     */
    @Transactional
    public com.uteq.backend.dto.UsuarioResponseDTO crearUsuario(com.uteq.backend.dto.CrearUsuarioAdminRequestDTO dto, Authentication authentication) {
        usuarioRepo.findByCorreo(dto.correo()).ifPresent(u -> { throw new com.uteq.backend.service.CorreoYaRegistradoException("El correo ya está registrado: " + dto.correo()); });
        // GERENTE solo crea LECTOR o BIBLIOTECARIO.
        if (esGerente(authentication) && !ROLES_GERENTE_PERMITIDOS.contains(dto.rol())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "GERENTE solo puede crear usuarios LECTOR o BIBLIOTECARIO");
        }
        Rol rol = rolRepo.findByNombre(dto.rol()).orElseThrow(() -> new IllegalArgumentException(ROL_NO_ENCONTRADO + dto.rol()));
        EstadoUsuario estadoActivo = estadoUsuarioRepo.findByNombre("ACTIVO").orElseThrow(() -> new IllegalStateException("Estado ACTIVO no existe"));
        java.util.Set<Rol> roles = new java.util.HashSet<>(); roles.add(rol);
        Long ejecutorId = resolverIdPorCorreo(authentication.getName());
        Usuario usuario = Usuario.builder().nombre(dto.nombre()).apellido(dto.apellido()).correo(dto.correo()).passwordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode(dto.password())).estado(estadoActivo).correoVerificado(true).roles(roles).fechaRegistro(Instant.now()).actualizadoEn(Instant.now()).creadoPor(ejecutorId).build();
        Usuario guardado = usuarioRepo.save(usuario);
        java.util.List<String> rolesStr = guardado.getRoles().stream().map(Rol::getNombre).toList();
        return new com.uteq.backend.dto.UsuarioResponseDTO(guardado.getId(), guardado.getNombre(), guardado.getCorreo(), rolesStr);
    }

    /**
     * Desactiva la cuenta pasándola al estado INACTIVO para retirarla de la operación sin borrar su fila.
     * Persiste además el motivo en la tabla de motivos de cambio, complemento del trigger de auditoría
     * que solo ve columnas antes/después y no recibe el motivo como parámetro.
     *
     * @param usuarioId identificador del usuario a desactivar
     * @param motivo justificación escrita por el ejecutor que queda guardada en el historial de motivos
     * @param authentication identidad autenticada desde el JWT de la que se deriva el ejecutor del cambio
     * @throws jakarta.persistence.EntityNotFoundException si no existe ningún usuario con ese identificador
     * @throws IllegalStateException si falta la fila de catálogo del estado INACTIVO
     */
    @Transactional
    /**
     * Executes the eliminarUsuario operation.
     * @param usuarioId value required by the operation
     * @param motivo value required by the operation
     * @param authentication value required by the operation
     */
    public void eliminarUsuario(Long usuarioId, String motivo, Authentication authentication) {
        Usuario usuario = usuarioRepo.findByIdWithEstadoAndRoles(usuarioId).orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + usuarioId));
        EstadoUsuario inactivo = estadoUsuarioRepo.findByNombre("INACTIVO").orElseThrow(() -> new IllegalStateException("Estado INACTIVO no existe"));
        Integer estadoAnteriorId = usuario.getEstado().getId();
        usuario.setEstado(inactivo);
        usuario.setActualizadoEn(Instant.now());
        usuarioRepo.save(usuario);
        // Mismo caso que cambiarEstado(): trg_auditoria_usuarios audita el
        // UPDATE de la fila, usuario_motivos_cambio (V50) guarda el motivo
        // que el trigger no puede ver (ver OBS-28).
        Long ejecutorId = resolverIdPorCorreo(authentication == null ? null : authentication.getName());
        registrarMotivoCambio(usuarioId, "ELIMINACION", estadoAnteriorId, inactivo.getId(), motivo, ejecutorId);
    }

    // usuario_motivos_cambio (V50): historial dedicado, complementario a
    // bitacora_auditoria/trg_auditoria_usuarios -- ver OBS-28.
    private void registrarMotivoCambio(Long usuarioId, String tipoCambio, Integer estadoAnteriorId,
                                        Integer estadoNuevoId, String motivo, Long ejecutorId) {
        UsuarioMotivoCambio fila = UsuarioMotivoCambio.builder()
                .usuarioId(usuarioId)
                .tipoCambio(tipoCambio)
                .estadoAnterior(estadoAnteriorId)
                .estadoNuevo(estadoNuevoId)
                .motivo(motivo)
                .ejecutadoPor(ejecutorId)
                .creadoEn(OffsetDateTime.now())
                .build();
        usuarioMotivoCambioRepo.save(fila);
    }

    // V50/OBS-28: historial de motivos de cambio de estado/eliminación,
    // más reciente primero.
    /**
     * Recupera el historial de motivos de cambio de estado y desactivaciones del usuario, del más
     * reciente al más antiguo, para explicar en el panel por qué la cuenta llegó a su estado actual.
     *
     * @param usuarioId identificador del usuario cuyo historial se consulta
     * @return lista de registros con tipo de cambio, estado anterior y nuevo, motivo, ejecutor y fecha, ordenada por fecha descendente
     */
    @Transactional(readOnly = true)
    public List<com.uteq.backend.dto.UsuarioMotivoCambioResponseDTO> historialMotivos(Long usuarioId) {
        return usuarioMotivoCambioRepo.findByUsuarioIdOrderByCreadoEnDesc(usuarioId).stream()
                .map(m -> new com.uteq.backend.dto.UsuarioMotivoCambioResponseDTO(
                        m.getId(), m.getTipoCambio(), m.getEstadoAnterior(), m.getEstadoNuevo(),
                        m.getMotivo(), m.getEjecutadoPor(), m.getCreadoEn()))
                .toList();
    }

    private Long resolverIdPorCorreo(String correo) {
        if (correo == null) throw new EntityNotFoundException(USUARIO_NO_ENCONTRADO + "null");
        String normalizado = correo.trim().toLowerCase();
        // Usa IgnoreCase para evitar 404 por mayúsculas en el JWT.
        Optional<Usuario> opt = usuarioRepo.findByCorreoIgnoreCase(normalizado);
        if (opt.isPresent()) return opt.get().getId();
        return usuarioRepo.findByCorreo(correo)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + correo))
                .getId();
    }

    // GERENTE opera solo sobre sus creados; ADMIN sin restricción.
    private boolean esGerente(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_GERENTE".equals(a.getAuthority()));
    }

    private UsuarioListadoResponseDTO toListadoDTO(Usuario usuario, boolean multasPendientes) {
        List<String> roles = usuario.getRoles().stream()
                .map(Rol::getNombre)
                .toList();
        return new UsuarioListadoResponseDTO(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getCorreo(),
                roles,
                usuario.getEstado().getNombre(),
                multasPendientes);
    }
}
