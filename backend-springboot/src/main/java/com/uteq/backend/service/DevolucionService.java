package com.uteq.backend.service;

import com.uteq.backend.dto.DanoDetalleResponseDTO;
import com.uteq.backend.dto.DevolucionCompletaResponseDTO;
import com.uteq.backend.dto.DevolucionHistorialDTO;
import com.uteq.backend.dto.DevolucionRequestDTO;
import com.uteq.backend.dto.TipoDanoDTO;
import com.uteq.backend.entity.BitacoraAuditoria;
import com.uteq.backend.entity.EstadoMulta;
import com.uteq.backend.entity.EvidenciaDano;
import com.uteq.backend.entity.Libro;
import com.uteq.backend.entity.Multa;
import com.uteq.backend.entity.Prestamo;
import com.uteq.backend.entity.RegistroDano;
import com.uteq.backend.entity.RegistroDanoDetalle;
import com.uteq.backend.entity.TipoDano;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.BitacoraAuditoriaRepository;
import com.uteq.backend.repository.EvidenciaDanoRepository;
import com.uteq.backend.repository.EstadoMultaRepository;
import com.uteq.backend.repository.EstadoPrestamoRepository;
import com.uteq.backend.repository.LibroRepository;
import com.uteq.backend.repository.MultaRepository;
import com.uteq.backend.repository.PrestamoProcedureRepository;
import com.uteq.backend.repository.PrestamoRepository;
import com.uteq.backend.repository.RegistroDanoDetalleRepository;
import com.uteq.backend.repository.RegistroDanoRepository;
import com.uteq.backend.repository.TipoDanoRepository;
import com.uteq.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DevolucionService {

    private static final String PRESTAMO_NO_ENCONTRADO = "Prestamo no encontrado: ";
    private static final String ESTADO_MULTA_PENDIENTE = "PENDIENTE";
    private static final String TABLA_REGISTRO_DANOS = "registro_danos";
    private static final String TABLA_PRESTAMOS = "prestamos";
    private static final int LIMITE_HISTORIAL = 10;

    private final PrestamoRepository prestamoRepo;
    private final PrestamoProcedureRepository prestamoProcRepo;
    private final UsuarioRepository usuarioRepo;
    private final LibroRepository libroRepo;
    private final EstadoPrestamoRepository estadoPrestamoRepo;
    private final EstadoMultaRepository estadoMultaRepo;
    private final MultaRepository multaRepo;
    private final TipoDanoRepository tipoDanoRepo;
    private final RegistroDanoRepository registroDanoRepo;
    private final RegistroDanoDetalleRepository registroDanoDetalleRepo;
    private final EvidenciaDanoRepository evidenciaDanoRepo;
    private final BitacoraAuditoriaRepository bitacoraAuditoriaRepo;

    public DevolucionService(PrestamoRepository prestamoRepo,
                             PrestamoProcedureRepository prestamoProcRepo,
                             UsuarioRepository usuarioRepo,
                             LibroRepository libroRepo,
                             EstadoPrestamoRepository estadoPrestamoRepo,
                             EstadoMultaRepository estadoMultaRepo,
                             MultaRepository multaRepo,
                             TipoDanoRepository tipoDanoRepo,
                             RegistroDanoRepository registroDanoRepo,
                             RegistroDanoDetalleRepository registroDanoDetalleRepo,
                             EvidenciaDanoRepository evidenciaDanoRepo,
                             BitacoraAuditoriaRepository bitacoraAuditoriaRepo) {
        this.prestamoRepo = prestamoRepo;
        this.prestamoProcRepo = prestamoProcRepo;
        this.usuarioRepo = usuarioRepo;
        this.libroRepo = libroRepo;
        this.estadoPrestamoRepo = estadoPrestamoRepo;
        this.estadoMultaRepo = estadoMultaRepo;
        this.multaRepo = multaRepo;
        this.tipoDanoRepo = tipoDanoRepo;
        this.registroDanoRepo = registroDanoRepo;
        this.registroDanoDetalleRepo = registroDanoDetalleRepo;
        this.evidenciaDanoRepo = evidenciaDanoRepo;
        this.bitacoraAuditoriaRepo = bitacoraAuditoriaRepo;
    }

    @Transactional
    public DevolucionCompletaResponseDTO registrarDevolucion(
            Long prestamoId, DevolucionRequestDTO dto, Long bibliotecarioId) {

        Prestamo prestamo = prestamoRepo.findById(prestamoId)
                .orElseThrow(() -> new EntityNotFoundException(PRESTAMO_NO_ENCONTRADO + prestamoId));

        if (prestamo.getFechaDevolucionReal() != null) {
            throw new IllegalStateException("El prestamo " + prestamoId + " ya fue devuelto.");
        }

        Map<String, Object> resultadoSp = prestamoProcRepo.spRegistrarDevolucion(prestamoId);
        Boolean huboMultaAtraso = (Boolean) resultadoSp.get("o_hubo_multa");
        BigDecimal montoMultaAtraso = resultadoSp.get("o_monto_multa") != null
                ? new BigDecimal(resultadoSp.get("o_monto_multa").toString())
                : null;

        boolean hayDanos = dto.estadoDevolucion() != null
                && "CON_DANO".equals(dto.estadoDevolucion())
                && dto.danos() != null
                && !dto.danos().isEmpty();

        boolean esPerdido = "PERDIDO".equals(dto.estadoDevolucion());

        BigDecimal montoMultaDano = BigDecimal.ZERO;
        List<DanoDetalleResponseDTO> danosRegistrados = new ArrayList<>();

        if (hayDanos || esPerdido) {
            RegistroDano registro = new RegistroDano();
            registro.setPrestamoId(prestamoId);
            registro.setEstadoDevolucion(dto.estadoDevolucion());
            registro.setDescripcion(dto.descripcion());
            registro.setBibliotecarioId(bibliotecarioId);
            registro.setFechaRegistro(OffsetDateTime.now());
            registro = registroDanoRepo.save(registro);

            if (hayDanos) {
                for (DevolucionRequestDTO.DanoItemDTO item : dto.danos()) {
                    RegistroDanoDetalle detalle = new RegistroDanoDetalle();
                    detalle.setRegistroDanoId(registro.getId());
                    detalle.setTipoDanoId(item.tipoDanoId());
                    detalle.setNombreCustom(item.nombreCustom());
                    detalle.setPrecioCobrado(item.precioCobrado());
                    registroDanoDetalleRepo.save(detalle);

                    montoMultaDano = montoMultaDano.add(item.precioCobrado());

                    String nombreDano = item.tipoDanoId() != null
                            ? tipoDanoRepo.findById(item.tipoDanoId())
                                    .map(TipoDano::getNombre)
                                    .orElse("Desconocido")
                            : item.nombreCustom();

                    danosRegistrados.add(new DanoDetalleResponseDTO(
                            detalle.getId(),
                            nombreDano,
                            item.nombreCustom(),
                            item.precioCobrado()));
                }
            }

            if (esPerdido) {
                Libro libro = libroRepo.findById(prestamo.getLibroId())
                        .orElse(null);
                BigDecimal valorLibro = BigDecimal.valueOf(15.00);
                montoMultaDano = valorLibro;

                danosRegistrados.add(new DanoDetalleResponseDTO(
                        null, "Libro perdido", "Libro perdido", valorLibro));
            }

            if (montoMultaDano.compareTo(BigDecimal.ZERO) > 0) {
                Integer estadoPendienteId = estadoMultaRepo.findByNombre(ESTADO_MULTA_PENDIENTE)
                        .map(EstadoMulta::getId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Catalogo estados_multa sin fila '" + ESTADO_MULTA_PENDIENTE + "'"));

                Multa multaDano = new Multa();
                multaDano.setPrestamoId(prestamoId);
                multaDano.setMonto(montoMultaDano);
                multaDano.setEstadoMultaId(estadoPendienteId);
                multaDano.setFechaGenerada(OffsetDateTime.now());
                multaDano.setObservaciones("Dano registrado: " + dto.estadoDevolucion());
                multaRepo.save(multaDano);

                if (huboMultaAtraso != null && huboMultaAtraso) {
                    Usuario usuario = usuarioRepo.findById(prestamo.getUsuarioId()).orElse(null);
                }
            }

            registrarAuditoria(bibliotecarioId, registro.getId(),
                    "Devolucion prestamo " + prestamoId
                            + " - Estado: " + dto.estadoDevolucion()
                            + " - Multa atraso: $" + (montoMultaAtraso != null ? montoMultaAtraso : "0.00")
                            + " - Multa dano: $" + montoMultaDano
                            + " - Danos: " + danosRegistrados.size());
        } else {
            Usuario bibliotecario = usuarioRepo.findById(bibliotecarioId).orElse(null);
            String nombreBiblio = bibliotecario != null
                    ? (bibliotecario.getNombre() + " " + bibliotecario.getApellido()).trim()
                    : "Desconocido";

            registrarAuditoria(bibliotecarioId, prestamoId,
                    "Devolucion prestamo " + prestamoId
                            + " - Estado: " + dto.estadoDevolucion()
                            + " - Multa atraso: $" + (montoMultaAtraso != null ? montoMultaAtraso : "0.00")
                            + " - Sin danos - Bibliotecario: " + nombreBiblio);
        }

        BigDecimal montoTotal = BigDecimal.ZERO;
        if (montoMultaAtraso != null) montoTotal = montoTotal.add(montoMultaAtraso);
        montoTotal = montoTotal.add(montoMultaDano);

        return new DevolucionCompletaResponseDTO(
                prestamoId,
                huboMultaAtraso != null && huboMultaAtraso,
                montoMultaAtraso,
                montoMultaDano.compareTo(BigDecimal.ZERO) > 0,
                montoMultaDano,
                montoTotal,
                danosRegistrados);
    }

    @Transactional(readOnly = true)
    public List<TipoDanoDTO> listarTiposDano() {
        return tipoDanoRepo.findByActivoTrue().stream()
                .map(t -> new TipoDanoDTO(t.getId(), t.getNombre(), t.getPrecio()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DevolucionHistorialDTO> historialDevoluciones(Long bibliotecarioId) {
        List<RegistroDano> registros = registroDanoRepo
                .findTop10ByBibliotecarioIdOrderByFechaRegistroDesc(
                        bibliotecarioId, PageRequest.of(0, LIMITE_HISTORIAL));

        if (registros.isEmpty()) return List.of();

        List<Long> prestamoIds = registros.stream()
                .map(RegistroDano::getPrestamoId).distinct().toList();

        Map<Long, Prestamo> prestamosMap = prestamoRepo.findAllById(prestamoIds).stream()
                .collect(Collectors.toMap(Prestamo::getId, p -> p));

        Map<Long, Libro> librosMap = libroRepo.findAllById(
                prestamosMap.values().stream().map(Prestamo::getLibroId).distinct().toList())
                .stream().collect(Collectors.toMap(Libro::getId, l -> l));

        Map<Long, Usuario> usuariosMap = usuarioRepo.findAllById(
                prestamosMap.values().stream().map(Prestamo::getUsuarioId).distinct().toList())
                .stream().collect(Collectors.toMap(Usuario::getId, u -> u));

        Usuario bibliotecario = usuarioRepo.findById(bibliotecarioId).orElse(null);
        String nombreBiblio = bibliotecario != null
                ? (bibliotecario.getNombre() + " " + bibliotecario.getApellido()).trim()
                : "Desconocido";

        List<Long> registroIds = registros.stream().map(RegistroDano::getId).toList();
        Map<Long, BigDecimal> multasPorRegistro = calcularMultasPorRegistro(registroIds);

        return registros.stream().map(rd -> {
            Prestamo p = prestamosMap.get(rd.getPrestamoId());
            if (p == null) return null;

            Libro l = librosMap.get(p.getLibroId());
            Usuario u = usuariosMap.get(p.getUsuarioId());

            return new DevolucionHistorialDTO(
                    p.getId(),
                    l != null ? l.getTitulo() : "Libro #" + p.getLibroId(),
                    l != null ? l.getIsbn() : "",
                    u != null ? (u.getNombre() + " " + u.getApellido()).trim() : "Desconocido",
                    p.getFechaPrestamo(),
                    p.getFechaDevolucionEstimada(),
                    p.getFechaDevolucionReal(),
                    rd.getEstadoDevolucion(),
                    multasPorRegistro.getOrDefault(rd.getId(), BigDecimal.ZERO),
                    nombreBiblio,
                    rd.getFechaRegistro());
        }).filter(rd -> rd != null).toList();
    }

    private Map<Long, BigDecimal> calcularMultasPorRegistro(List<Long> registroIds) {
        if (registroIds.isEmpty()) return Map.of();

        Map<Long, BigDecimal> resultado = new java.util.HashMap<>();

        List<Multa> multas = multaRepo.findAll().stream()
                .filter(m -> m.getObservaciones() != null && m.getObservaciones().contains("Dano registrado"))
                .toList();

        return resultado;
    }

    private void registrarAuditoria(Long ejecutorId, Long registroId, String detalles) {
        BitacoraAuditoria evento = BitacoraAuditoria.builder()
                .usuarioId(ejecutorId)
                .tipoOperacion("INSERT")
                .tablaAfectada(TABLA_REGISTRO_DANOS)
                .registroId(registroId)
                .detalles(detalles)
                .fechaHora(OffsetDateTime.now())
                .build();
        bitacoraAuditoriaRepo.save(evento);
    }
}
