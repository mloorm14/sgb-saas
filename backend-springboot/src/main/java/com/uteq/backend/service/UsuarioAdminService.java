package com.uteq.backend.service;

import com.uteq.backend.dto.UsuarioListadoResponseDTO;
import com.uteq.backend.entity.BitacoraAuditoria;
import com.uteq.backend.entity.EstadoUsuario;
import com.uteq.backend.entity.Rol;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.BitacoraAuditoriaRepository;
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
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Panel de administración de usuarios (Módulo 5 del roadmap): listado
 * paginado con filtro, cambio de rol y cambio de estado (bloqueo/activación
 * manual). Corresponde a RF-01 y al actor "Gerente/Admin" del documento de
 * requisitos original.
 * <p>
 * Separación ADMIN vs GERENTE (ver {@code docs/adr/adr-014-separacion-admin-gerente.md},
 * ampliada por F8-gerente/V38): el controller admite ADMIN y GERENTE en
 * listar/crear/cambiar-rol/cambiar-estado, y este service aplica el recorte
 * fino — GERENTE solo ve y opera usuarios con creado_por propio y solo
 * roles LECTOR/BIBLIOTECARIO + estados ACTIVO/INACTIVO. Solo ADMIN crea
 * GERENTE/ADMIN y ve todo. DELETE sigue solo ADMIN en el controller.
 */
@Service
public class UsuarioAdminService {

    private static final String USUARIO_NO_ENCONTRADO = "Usuario no encontrado: ";
    private static final String ROL_NO_ENCONTRADO = "Rol no válido: ";
    private static final String ESTADO_NO_ENCONTRADO = "Estado no válido: ";
    private static final String TABLA_USUARIOS = "usuarios";
    // F8-gerente: GERENTE solo opera LECTOR/BIBLIOTECARIO creados por él.
    // Solo ADMIN crea GERENTE/ADMIN (ver UsuarioAdminController).
    private static final Set<String> ROLES_GERENTE_PERMITIDOS = Set.of("LECTOR", "BIBLIOTECARIO");
    private static final Set<String> ESTADOS_GERENTE_PERMITIDOS = Set.of("ACTIVO", "INACTIVO");

    private final UsuarioRepository usuarioRepo;
    private final RolRepository rolRepo;
    private final EstadoUsuarioRepository estadoUsuarioRepo;
    private final BitacoraAuditoriaRepository bitacoraAuditoriaRepo;
    private final MultaRepository multaRepo;
    private final EstadoMultaRepository estadoMultaRepo;

    public UsuarioAdminService(UsuarioRepository usuarioRepo,
                                RolRepository rolRepo,
                                EstadoUsuarioRepository estadoUsuarioRepo,
                                BitacoraAuditoriaRepository bitacoraAuditoriaRepo,
                                MultaRepository multaRepo,
                                EstadoMultaRepository estadoMultaRepo) {
        this.usuarioRepo = usuarioRepo;
        this.rolRepo = rolRepo;
        this.estadoUsuarioRepo = estadoUsuarioRepo;
        this.bitacoraAuditoriaRepo = bitacoraAuditoriaRepo;
        this.multaRepo = multaRepo;
        this.estadoMultaRepo = estadoMultaRepo;
    }

    @Transactional(readOnly = true)
    public Page<UsuarioListadoResponseDTO> listar(String filtro, Pageable pageable) {
        return listar(filtro, pageable, null, false);
    }

    // F8-gerente: si authentication es GERENTE (o soloMios=true), filtra por
    // creado_por = miId. ADMIN con soloMios=false ve todo como antes.
    @Transactional(readOnly = true)
    public Page<UsuarioListadoResponseDTO> listar(String filtro, Pageable pageable,
                                                  Authentication authentication, boolean soloMios) {
        String texto = filtro == null ? "" : filtro.trim();
        Long creadoPor = null;
        if (authentication != null && (soloMios || esGerente(authentication))) {
            creadoPor = resolverIdPorCorreo(authentication.getName());
        }
        Page<Usuario> pagina = usuarioRepo.buscarConFiltros(texto, creadoPor, pageable);

        // Batch query: una sola consulta para saber qué usuarios de la
        // página tienen multas pendientes (evita N+1).
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
     * Reemplaza el conjunto de roles del usuario por uno solo (
     * {@code nuevoRol}). No es un "agregar rol" -- simplifica el modelo de
     * administración a "cada usuario tiene un rol operativo vigente",
     * consistente con que hoy {@code AuthService.registrar} también asigna
     * un único rol (LECTOR) al crear la cuenta.
     */
    @Transactional
    public void cambiarRol(Long usuarioId, String nuevoRol, Authentication authentication) {
        Usuario usuario = usuarioRepo.findByIdWithEstadoAndRoles(usuarioId)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + usuarioId));
        Rol rol = rolRepo.findByNombre(nuevoRol)
                .orElseThrow(() -> new IllegalArgumentException(ROL_NO_ENCONTRADO + nuevoRol));
        Long ejecutorId = resolverIdPorCorreo(authentication == null ? null : authentication.getName());
        // F8-gerente: GERENTE solo cambia rol a sus creados y solo LECTOR/BIBLIOTECARIO.
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

        registrarAuditoria(ejecutorId, usuario.getId(),
                "Cambio de rol del usuario " + usuario.getCorreo() + " a " + nuevoRol);
    }

    /**
     * Cambia el estado del usuario (p.ej. ACTIVO -> INACTIVO para dar de
     * baja, o INACTIVO -> ACTIVO para reactivar). {@code motivo} es
     * obligatorio a nivel de DTO ({@link com.uteq.backend.dto.CambioEstadoUsuarioRequestDTO})
     * y queda registrado en la bitácora -- distinto del bloqueo automático
     * por multas impagas, que lo aplica {@code sp_pagar_multa}/
     * {@code sp_anular_multa} sin intervención de un ADMIN.
     */
     @Transactional
    public void cambiarEstado(Long usuarioId, String nuevoEstado, String motivo, Authentication authentication) {
        Usuario usuario = usuarioRepo.findByIdWithEstadoAndRoles(usuarioId)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + usuarioId));
        EstadoUsuario estado = estadoUsuarioRepo.findByNombre(nuevoEstado)
                .orElseThrow(() -> new IllegalArgumentException(ESTADO_NO_ENCONTRADO + nuevoEstado));
        Long ejecutorId = resolverIdPorCorreo(authentication == null ? null : authentication.getName());
        // F8-gerente: GERENTE solo bloquea/reactiva (ACTIVO/INACTIVO) a sus creados.
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

        registrarAuditoria(ejecutorId, usuario.getId(),
                "Cambio de estado del usuario " + usuario.getCorreo() + " a " + nuevoEstado
                        + ". Motivo: " + motivo);
    }

    // Mismo patrón que MultaService/PrestamoService: el ejecutor real
    // (quién hizo el cambio) se resuelve desde el JWT autenticado, nunca
    // desde un campo del body -- evita que alguien falsifique "quién"
    // aparece en la bitácora.
    @Transactional
    public com.uteq.backend.dto.UsuarioResponseDTO crearUsuario(com.uteq.backend.dto.CrearUsuarioAdminRequestDTO dto, Authentication authentication) {
        usuarioRepo.findByCorreo(dto.correo()).ifPresent(u -> { throw new com.uteq.backend.service.CorreoYaRegistradoException("El correo ya está registrado: " + dto.correo()); });
        // F8-gerente: solo ADMIN crea GERENTE/ADMIN.
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
        registrarAuditoria(ejecutorId, guardado.getId(), "Creación admin de usuario " + dto.correo() + " rol " + dto.rol());
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
        Long ejecutorId = resolverIdPorCorreo(authentication.getName());
        registrarAuditoria(ejecutorId, usuarioId, "Eliminación soft usuario " + usuario.getCorreo() + " motivo: " + (motivo != null ? motivo : "no especificado"));
    }

    private Long resolverIdPorCorreo(String correo) {
        if (correo == null) throw new EntityNotFoundException(USUARIO_NO_ENCONTRADO + "null");
        String normalizado = correo.trim().toLowerCase();
        // Usa IgnoreCase para evitar 404 fantasma por mayúsculas en JWT; tolera mock sin stub
        Optional<Usuario> opt = usuarioRepo.findByCorreoIgnoreCase(normalizado);
        if (opt != null && opt.isPresent()) return opt.get().getId();
        return usuarioRepo.findByCorreo(correo)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + correo))
                .getId();
    }

    // F8-gerente: GERENTE opera solo sobre sus creados; ADMIN sin restricción.
    private boolean esGerente(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_GERENTE".equals(a.getAuthority()));
    }

    // Mismo criterio que AuthService.registrarAuditoria(): INSERT plano de
    // una sola tabla, sin lógica cruzada -- no justifica un procedimiento
    // almacenado (ver adr-013-acceso-datos-orm-sp.md). usuarioId acá es el
    // ejecutor (el ADMIN que hizo el cambio), no el usuario afectado --
    // ese va en registroId, para poder distinguir "quién hizo qué a quién"
    // al leer la bitácora.
    private void registrarAuditoria(Long ejecutorId, Long usuarioAfectadoId, String detalles) {
        BitacoraAuditoria evento = BitacoraAuditoria.builder()
                .usuarioId(ejecutorId)
                .tipoOperacion("UPDATE")
                .tablaAfectada(TABLA_USUARIOS)
                .registroId(usuarioAfectadoId)
                .detalles(detalles)
                .fechaHora(OffsetDateTime.now())
                .build();
        bitacoraAuditoriaRepo.save(evento);
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
