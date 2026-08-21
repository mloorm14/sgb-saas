package com.uteq.backend.service;

import com.uteq.backend.dto.HistorialReservacionDTO;
import com.uteq.backend.dto.UsuarioReservacionesGestionDTO;
import com.uteq.backend.entity.EstadoReservacion;
import com.uteq.backend.entity.Reservacion;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.EstadoReservacionRepository;
import com.uteq.backend.repository.LibroRepository;
import com.uteq.backend.repository.ReservacionRepository;
import com.uteq.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lecturas de la ventanilla de reservaciones del bibliotecario (módulo
 * "Reservaciones" del sidebar): encontrar al usuario por correo y armar
 * la pantalla -- tarjeta de identificación, historial de reservaciones.
 *
 * La CREACIÓN de la reservación se mantiene en ReservacionService.crear()
 * (POST /api/v1/reservaciones), que ahora acepta fechaRetiro opcional.
 */
@Service
public class ReservacionesGestionService {

    private static final String USUARIO_NO_ENCONTRADO =
            "No se encontró ningún usuario con este correo";
    private static final int LIMITE_RESERVAS_ACTIVAS = 3;

    private static final List<String> ESTADOS_RESERVA_VIGENTE =
            List.of("PENDIENTE", "LISTA_PARA_RETIRO");

    private final UsuarioRepository usuarioRepo;
    private final ReservacionRepository reservacionRepo;
    private final EstadoReservacionRepository estadoReservacionRepo;
    private final LibroRepository libroRepo;

    public ReservacionesGestionService(UsuarioRepository usuarioRepo,
                                       ReservacionRepository reservacionRepo,
                                       EstadoReservacionRepository estadoReservacionRepo,
                                       LibroRepository libroRepo) {
        this.usuarioRepo = usuarioRepo;
        this.reservacionRepo = reservacionRepo;
        this.estadoReservacionRepo = estadoReservacionRepo;
        this.libroRepo = libroRepo;
    }

    // ── GET /gestion/buscar-usuario?correo= ──────────────────
    @Transactional(readOnly = true)
    public UsuarioReservacionesGestionDTO buscarPorCorreo(String correo) {
        Usuario usuario = usuarioRepo.findByCorreo(correo)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO));

        List<Integer> idsVigentes = ESTADOS_RESERVA_VIGENTE.stream()
                .map(this::idEstadoReservacion)
                .toList();

        long cantidadActivas = reservacionRepo.countByUsuarioIdAndEstadoReservacionIdIn(
                usuario.getId(), idsVigentes);

        return new UsuarioReservacionesGestionDTO(
                usuario.getId(),
                (usuario.getNombre() + " " + usuario.getApellido()).trim(),
                usuario.getCorreo(),
                usuario.getEstado().getNombre(),
                cantidadActivas,
                LIMITE_RESERVAS_ACTIVAS);
    }

    // ── GET /gestion/historial-reservaciones?usuarioId= ───────
    // Retorna las reservaciones del usuario con el título del libro
    // resuelto en batch (3 queries: reservaciones, libros, estados).
    @Transactional(readOnly = true)
    public List<HistorialReservacionDTO> historialReservaciones(Long usuarioId) {
        // Validar que el usuario exista
        if (!usuarioRepo.existsById(usuarioId)) {
            throw new EntityNotFoundException("Usuario no encontrado: " + usuarioId);
        }

        List<Reservacion> reservaciones = reservacionRepo
                .findByUsuarioId(usuarioId,
                        org.springframework.data.domain.PageRequest.of(0, 50,
                                org.springframework.data.domain.Sort.by(
                                        org.springframework.data.domain.Sort.Direction.DESC,
                                        "fechaReserva")))
                .getContent();

        if (reservaciones.isEmpty()) {
            return List.of();
        }

        Map<Long, String> titulosPorLibro = libroRepo.findAllById(
                        reservaciones.stream().map(Reservacion::getLibroId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(
                        com.uteq.backend.entity.Libro::getId,
                        com.uteq.backend.entity.Libro::getTitulo));

        Map<Integer, String> nombresPorEstado = estadoReservacionRepo.findAllById(
                        reservaciones.stream().map(Reservacion::getEstadoReservacionId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(EstadoReservacion::getId, EstadoReservacion::getNombre));

        return reservaciones.stream()
                .map(r -> new HistorialReservacionDTO(
                        r.getId(),
                        titulosPorLibro.getOrDefault(r.getLibroId(), "Libro #" + r.getLibroId()),
                        nombresPorEstado.getOrDefault(r.getEstadoReservacionId(), ""),
                        r.getEstadoReservacionId(),
                        r.getFechaReserva(),
                        r.getFechaLimiteRetiro()))
                .toList();
    }

    private Integer idEstadoReservacion(String nombre) {
        return estadoReservacionRepo.findByNombre(nombre)
                .orElseThrow(() -> new IllegalStateException(
                        "Catálogo estados_reservacion sin fila '" + nombre + "'"))
                .getId();
    }
}
