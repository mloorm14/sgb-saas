package com.uteq.backend.service;

import com.uteq.backend.dto.UsuarioListadoResponseDTO;
import com.uteq.backend.entity.EstadoUsuario;
import com.uteq.backend.entity.Rol;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.EstadoMultaRepository;
import com.uteq.backend.repository.EstadoUsuarioRepository;
import com.uteq.backend.repository.MultaRepository;
import com.uteq.backend.repository.RolRepository;
import com.uteq.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

    public UsuarioAdminService(UsuarioRepository usuarioRepo,
                                RolRepository rolRepo,
                                EstadoUsuarioRepository estadoUsuarioRepo,
                                MultaRepository multaRepo,
                                EstadoMultaRepository estadoMultaRepo) {
        this.usuarioRepo = usuarioRepo;
        this.rolRepo = rolRepo;
        this.estadoUsuarioRepo = estadoUsuarioRepo;
        this.multaRepo = multaRepo;
        this.estadoMultaRepo = estadoMultaRepo;
    }

    @Transactional(readOnly = true)
    public Page<UsuarioListadoResponseDTO> listar(String filtro, Pageable pageable) {
        return listar(filtro, pageable, null, false);
    }

    // GERENTE filtra por creado_por = miId; ADMIN ve todo.
    @Transactional(readOnly = true)
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
     * Reemplaza los roles del usuario por uno solo ({@code nuevoRol}).
     */
    @Transactional
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
     * Cambia el estado del usuario (bloqueo/activación manual).
     * {@code motivo} queda registrado en la bitácora.
     */
     @Transactional
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

        usuario.setEstado(estado);
        usuario.setActualizadoEn(Instant.now());
        usuarioRepo.save(usuario);
        // NOTA: "motivo" ya no se persiste en ningún lado (no es columna de
        // usuarios, y el INSERT manual que lo guardaba como texto libre en
        // bitacora_auditoria.detalles se retiró junto con V49 -- ver OBS-28).
        // trg_auditoria_usuarios sí audita este cambio de estado, pero solo
        // ve las columnas de la fila (antes/después), no puede reconstruir
        // el motivo que el llamante pasó como parámetro suelto.
    }

    // El ejecutor se resuelve desde el JWT autenticado, nunca desde el body.
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

    @Transactional
    public void eliminarUsuario(Long usuarioId, String motivo, Authentication authentication) {
        Usuario usuario = usuarioRepo.findByIdWithEstadoAndRoles(usuarioId).orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + usuarioId));
        EstadoUsuario inactivo = estadoUsuarioRepo.findByNombre("INACTIVO").orElseThrow(() -> new IllegalStateException("Estado INACTIVO no existe"));
        usuario.setEstado(inactivo);
        usuario.setActualizadoEn(Instant.now());
        usuarioRepo.save(usuario);
        // NOTA: mismo caso que cambiarEstado() -- "motivo" no es columna de
        // usuarios y ya no se persiste en ningún lado tras retirar el INSERT
        // manual (ver OBS-28); trg_auditoria_usuarios audita el cambio de
        // estado pero no el motivo suelto que llega como parámetro.
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
