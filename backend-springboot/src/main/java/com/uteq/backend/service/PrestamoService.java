package com.uteq.backend.service;

import com.uteq.backend.dto.DevolucionResponseDTO;
import com.uteq.backend.dto.LibroMasPrestadoResponseDTO;
import com.uteq.backend.dto.PrestamoActivoResponseDTO;
import com.uteq.backend.dto.PrestamoRequestDTO;
import com.uteq.backend.dto.PrestamoResponseDTO;
import com.uteq.backend.entity.Prestamo;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.PrestamoProcedureRepository;
import com.uteq.backend.repository.PrestamoRepository;
import com.uteq.backend.repository.UsuarioRepository;
import com.uteq.backend.repository.projection.LibroMasPrestadoProjection;
import com.uteq.backend.repository.projection.PrestamoActivoProjection;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
public class PrestamoService {

    private static final String PRESTAMO_NO_ENCONTRADO = "Préstamo no encontrado con id: ";
    private static final String USUARIO_NO_ENCONTRADO = "Usuario no encontrado: ";
    private static final String ROL_LECTOR = "LECTOR";

    private final PrestamoRepository prestamoRepo;
    private final PrestamoProcedureRepository prestamoProcRepo;
    private final UsuarioRepository usuarioRepo;

    public PrestamoService(PrestamoRepository prestamoRepo,
                           PrestamoProcedureRepository prestamoProcRepo,
                           UsuarioRepository usuarioRepo) {
        this.prestamoRepo = prestamoRepo;
        this.prestamoProcRepo = prestamoProcRepo;
        this.usuarioRepo = usuarioRepo;
    }

    @Transactional
    public PrestamoResponseDTO crear(PrestamoRequestDTO dto, Authentication authentication) {
        Long bibliotecarioId = resolverIdPorCorreo(authentication.getName());
        Long prestamoId = prestamoProcRepo.spCrearPrestamo(
                dto.usuarioId(), dto.libroId(), bibliotecarioId, dto.diasPrestamo());
        Prestamo prestamo = prestamoRepo.findById(prestamoId)
                .orElseThrow(() -> new EntityNotFoundException(PRESTAMO_NO_ENCONTRADO + prestamoId));
        return toDTO(prestamo);
    }

    @Transactional
    public DevolucionResponseDTO registrarDevolucion(Long prestamoId) {
        Map<String, Object> resultado = prestamoProcRepo.spRegistrarDevolucion(prestamoId);
        return new DevolucionResponseDTO(
                (Long) resultado.get("o_prestamo_id"),
                (Boolean) resultado.get("o_hubo_multa"),
                (BigDecimal) resultado.get("o_monto_multa"));
    }

    @Transactional(readOnly = true)
    public Page<PrestamoResponseDTO> listarPorUsuario(Long usuarioId, Authentication authentication, Pageable pageable) {
        validarAccesoUsuario(usuarioId, authentication);
        return prestamoRepo.findByUsuarioId(usuarioId, pageable).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public List<PrestamoActivoResponseDTO> listarActivosPorUsuario(Long usuarioId, Authentication authentication) {
        validarAccesoUsuario(usuarioId, authentication);
        return prestamoProcRepo.fnListarPrestamosActivosPorUsuario(usuarioId).stream()
                .map(this::toDTO)
                .toList();
    }

    private static final int LIMITE_REPORTE_DEFAULT = 10;

    @Transactional(readOnly = true)
    public List<LibroMasPrestadoResponseDTO> reporteLibrosMasPrestados(
            Integer limite, OffsetDateTime desde, OffsetDateTime hasta) {
        // El DEFAULT 10 de fn_reporte_libros_mas_prestados solo se activa
        // cuando el parámetro se omite POR COMPLETO de la llamada SQL. La
        // @Query nativeQuery de PrestamoProcedureRepository siempre envía
        // los 3 parámetros nombrados, así que un null explícito produce
        // "LIMIT NULL" en Postgres = sin límite, no el default esperado.
        // Se aplica el default aquí para que el comportamiento.
        Integer limiteEfectivo = (limite != null) ? limite : LIMITE_REPORTE_DEFAULT;
        return prestamoProcRepo.fnReporteLibrosMasPrestados(limiteEfectivo, desde, hasta).stream()
                .map(this::toDTO)
                .toList();
    }

    // ── "Propio vs cualquiera": LECTOR solo puede pedir su propio
    // usuarioId; BIBLIOTECARIO/GERENTE no tienen restricción (mismo
    // patrón descrito en docs/reparto-entrega-3/cajas-backend/INSTRUCCIONES.md,
    // sección 1). ──
    private void validarAccesoUsuario(Long usuarioIdSolicitado, Authentication authentication) {
        boolean esLector = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(rol -> rol.equals("ROLE_" + ROL_LECTOR));
        if (!esLector) {
            return;
        }
        Long idPropio = resolverIdPorCorreo(authentication.getName());
        if (!idPropio.equals(usuarioIdSolicitado)) {
            throw new AuthorizationDeniedException(
                    "Un LECTOR solo puede consultar sus propios préstamos.");
        }
    }

    private Long resolverIdPorCorreo(String correo) {
        Usuario usuario = usuarioRepo.findByCorreo(correo)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + correo));
        return usuario.getId();
    }

    private PrestamoResponseDTO toDTO(Prestamo p) {
        return new PrestamoResponseDTO(
                p.getId(),
                p.getUsuarioId(),
                p.getLibroId(),
                p.getBibliotecarioId(),
                p.getReservacionId(),
                p.getFechaPrestamo(),
                p.getFechaDevolucionEstimada(),
                p.getFechaDevolucionReal(),
                p.getRenovacionesRealizadas(),
                p.getEstadoPrestamoId());
    }

    private PrestamoActivoResponseDTO toDTO(PrestamoActivoProjection p) {
        return new PrestamoActivoResponseDTO(
                p.getPrestamoId(),
                p.getLibroTitulo(),
                p.getLibroIsbn(),
                p.getFechaPrestamo(),
                p.getFechaDevolucionEstimada(),
                p.getDiasRestantes(),
                p.getEstadoNombre());
    }

    private LibroMasPrestadoResponseDTO toDTO(LibroMasPrestadoProjection p) {
        return new LibroMasPrestadoResponseDTO(
                p.getLibroId(),
                p.getTitulo(),
                p.getIsbn(),
                p.getTotalPrestamos());
    }
}