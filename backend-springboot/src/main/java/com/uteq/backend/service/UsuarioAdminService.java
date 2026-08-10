package com.uteq.backend.service;

import com.uteq.backend.dto.UsuarioListadoResponseDTO;
import com.uteq.backend.entity.BitacoraAuditoria;
import com.uteq.backend.entity.EstadoUsuario;
import com.uteq.backend.entity.Rol;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.BitacoraAuditoriaRepository;
import com.uteq.backend.repository.EstadoUsuarioRepository;
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
import java.util.Set;

/**
 * Panel de administración de usuarios (Módulo 5 del roadmap): listado
 * paginado con filtro, cambio de rol y cambio de estado (bloqueo/activación
 * manual). Corresponde a RF-01 y al actor "Gerente/Admin" del documento de
 * requisitos original.
 * <p>
 * Separación ADMIN vs GERENTE (ver {@code docs/adr/adr-014-separacion-admin-gerente.md}):
 * este service asume que quien invoca {@link #cambiarRol} y
 * {@link #cambiarEstado} ya fue autorizado como ADMIN por
 * {@code @PreAuthorize} en {@code UsuarioAdminController} -- no repite la
 * comprobación de rol acá (a diferencia de {@code MultaService}, donde el
 * mismo endpoint admite dos roles distintos y el service necesita saber
 * cuál de los dos ejecuta la acción). Acá cada método del controller ya
 * tiene un único rol fijo requerido, así que no hay ambigüedad que resolver
 * en tiempo de ejecución.
 */
@Service
public class UsuarioAdminService {

    private static final String USUARIO_NO_ENCONTRADO = "Usuario no encontrado: ";
    private static final String ROL_NO_ENCONTRADO = "Rol no válido: ";
    private static final String ESTADO_NO_ENCONTRADO = "Estado no válido: ";
    private static final String TABLA_USUARIOS = "usuarios";

    private final UsuarioRepository usuarioRepo;
    private final RolRepository rolRepo;
    private final EstadoUsuarioRepository estadoUsuarioRepo;
    private final BitacoraAuditoriaRepository bitacoraAuditoriaRepo;

    public UsuarioAdminService(UsuarioRepository usuarioRepo,
                                RolRepository rolRepo,
                                EstadoUsuarioRepository estadoUsuarioRepo,
                                BitacoraAuditoriaRepository bitacoraAuditoriaRepo) {
        this.usuarioRepo = usuarioRepo;
        this.rolRepo = rolRepo;
        this.estadoUsuarioRepo = estadoUsuarioRepo;
        this.bitacoraAuditoriaRepo = bitacoraAuditoriaRepo;
    }

    @Transactional(readOnly = true)
    public Page<UsuarioListadoResponseDTO> listar(String filtro, Pageable pageable) {
        String texto = filtro == null ? "" : filtro.trim();
        return usuarioRepo
                .findByNombreContainingIgnoreCaseOrCorreoContainingIgnoreCase(texto, texto, pageable)
                .map(this::toListadoDTO);
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
        Usuario usuario = usuarioRepo.findById(usuarioId)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + usuarioId));
        Rol rol = rolRepo.findByNombre(nuevoRol)
                .orElseThrow(() -> new IllegalArgumentException(ROL_NO_ENCONTRADO + nuevoRol));

        Set<Rol> roles = new HashSet<>();
        roles.add(rol);
        usuario.setRoles(roles);
        usuario.setActualizadoEn(Instant.now());
        usuarioRepo.save(usuario);

        Long ejecutorId = resolverIdPorCorreo(authentication.getName());
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
        Usuario usuario = usuarioRepo.findById(usuarioId)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + usuarioId));
        EstadoUsuario estado = estadoUsuarioRepo.findByNombre(nuevoEstado)
                .orElseThrow(() -> new IllegalArgumentException(ESTADO_NO_ENCONTRADO + nuevoEstado));

        usuario.setEstado(estado);
        usuario.setActualizadoEn(Instant.now());
        usuarioRepo.save(usuario);

        Long ejecutorId = resolverIdPorCorreo(authentication.getName());
        registrarAuditoria(ejecutorId, usuario.getId(),
                "Cambio de estado del usuario " + usuario.getCorreo() + " a " + nuevoEstado
                        + ". Motivo: " + motivo);
    }

    // Mismo patrón que MultaService/PrestamoService: el ejecutor real
    // (quién hizo el cambio) se resuelve desde el JWT autenticado, nunca
    // desde un campo del body -- evita que alguien falsifique "quién"
    // aparece en la bitácora.
    private Long resolverIdPorCorreo(String correo) {
        return usuarioRepo.findByCorreo(correo)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + correo))
                .getId();
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

    private UsuarioListadoResponseDTO toListadoDTO(Usuario usuario) {
        List<String> roles = usuario.getRoles().stream()
                .map(Rol::getNombre)
                .toList();
        boolean multasPendientes = "BLOQUEADO_POR_MULTA".equals(usuario.getEstado().getNombre());
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
