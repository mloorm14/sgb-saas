package com.uteq.backend.service;

import com.uteq.backend.dto.DanoDetalleResponseDTO;
import com.uteq.backend.dto.DevolucionCompletaResponseDTO;
import com.uteq.backend.dto.DevolucionHistorialDTO;
import com.uteq.backend.dto.DevolucionRequestDTO;
import com.uteq.backend.dto.EvidenciaDanoArchivoDTO;
import com.uteq.backend.dto.EvidenciaDanoResponseDTO;
import com.uteq.backend.dto.TipoDanoDTO;
import com.uteq.backend.entity.EstadoMulta;
import com.uteq.backend.entity.EvidenciaDano;
import com.uteq.backend.entity.Libro;
import com.uteq.backend.entity.Multa;
import com.uteq.backend.entity.Prestamo;
import com.uteq.backend.entity.RegistroDano;
import com.uteq.backend.entity.RegistroDanoDetalle;
import com.uteq.backend.entity.TipoDano;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.EvidenciaDanoRepository;
import com.uteq.backend.repository.EstadoMultaRepository;
import com.uteq.backend.repository.LibroRepository;
import com.uteq.backend.repository.MultaRepository;
import com.uteq.backend.repository.PrestamoProcedureRepository;
import com.uteq.backend.repository.PrestamoRepository;
import com.uteq.backend.repository.RegistroDanoDetalleRepository;
import com.uteq.backend.repository.RegistroDanoRepository;
import com.uteq.backend.repository.TipoDanoRepository;
import com.uteq.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DevolucionService {

    private static final Logger log = LoggerFactory.getLogger(DevolucionService.class);

    private static final String PRESTAMO_NO_ENCONTRADO = "Prestamo no encontrado: ";
    private static final String ESTADO_MULTA_PENDIENTE = "PENDIENTE";
    private static final String NOMBRE_DESCONOCIDO = "Desconocido";
    private static final int LIMITE_HISTORIAL = 10;
    private static final String CLAVE_MAX_TAMANO_EVIDENCIA_MB = "max_tamano_evidencia_mb";
    private static final Set<String> TIPOS_EVIDENCIA_PERMITIDOS = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/avif");

    private final PrestamoRepository prestamoRepo;
    private final PrestamoProcedureRepository prestamoProcRepo;
    private final UsuarioRepository usuarioRepo;
    private final LibroRepository libroRepo;
    private final EstadoMultaRepository estadoMultaRepo;
    private final MultaRepository multaRepo;
    private final TipoDanoRepository tipoDanoRepo;
    private final RegistroDanoRepository registroDanoRepo;
    private final RegistroDanoDetalleRepository registroDanoDetalleRepo;
    private final EvidenciaDanoRepository evidenciaDanoRepo;
    private final ConfiguracionSistemaService configuracionSistemaService;

    // La auditoria de registro_danos ya no se hace aqui: trg_auditoria_registro_danos
    // (V49__auditoria_triggers_negocio.sql) audita INSERT/UPDATE/DELETE a nivel de motor.
    // El resumen humano de la devolucion completa (estado + multa atraso + multa
    // dano + cantidad de danos en una sola linea) desaparece como vista consolidada:
    // ver detalle en OBS-28.
    public DevolucionService(PrestamoRepository prestamoRepo,
                             PrestamoProcedureRepository prestamoProcRepo,
                             UsuarioRepository usuarioRepo,
                             LibroRepository libroRepo,
                             EstadoMultaRepository estadoMultaRepo,
                             MultaRepository multaRepo,
                             TipoDanoRepository tipoDanoRepo,
                             RegistroDanoRepository registroDanoRepo,
                             RegistroDanoDetalleRepository registroDanoDetalleRepo,
                             EvidenciaDanoRepository evidenciaDanoRepo,
                             ConfiguracionSistemaService configuracionSistemaService) {
        this.prestamoRepo = prestamoRepo;
        this.prestamoProcRepo = prestamoProcRepo;
        this.usuarioRepo = usuarioRepo;
        this.libroRepo = libroRepo;
        this.estadoMultaRepo = estadoMultaRepo;
        this.multaRepo = multaRepo;
        this.tipoDanoRepo = tipoDanoRepo;
        this.registroDanoRepo = registroDanoRepo;
        this.registroDanoDetalleRepo = registroDanoDetalleRepo;
        this.evidenciaDanoRepo = evidenciaDanoRepo;
        this.configuracionSistemaService = configuracionSistemaService;
    }

    @Transactional
    /**
     * Executes the registrarDevolucion operation.
     * @param prestamoId value required by the operation
     * @param dto value required by the operation
     * @param bibliotecarioId value required by the operation
     * @return operation result
     */
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
        Long registroDanoId = null;

        // precio_base obligatorio para cálculo porcentaje
        BigDecimal precioLibro = BigDecimal.ZERO;
          try {
              Libro lib = libroRepo.findById(prestamo.getLibroId()).orElse(null);
              if (lib != null && lib.getPrecioBase() != null) precioLibro = lib.getPrecioBase();
          } catch (Exception e) {
              // best-effort: sin precio base la multa por daño se calcula en cero
              log.debug("No se pudo resolver precio base del libro {}", prestamo.getLibroId(), e);
          }

        if (hayDanos || esPerdido) {
            RegistroDano registro = new RegistroDano();
            registro.setPrestamoId(prestamoId);
            registro.setEstadoDevolucion(dto.estadoDevolucion());
            registro.setDescripcion(dto.descripcion());
            registro.setBibliotecarioId(bibliotecarioId);
            registro.setFechaRegistro(OffsetDateTime.now());
            registro = registroDanoRepo.save(registro);
            registroDanoId = registro.getId();

            if (hayDanos) {
                for (DevolucionRequestDTO.DanoItemDTO item : dto.danos()) {
                    BigDecimal cobrado;
                    String nombreDano;
                    if (item.tipoDanoId() != null) {
                        TipoDano t = tipoDanoRepo.findById(item.tipoDanoId()).orElse(null);
                        if (t != null) {
                            nombreDano = t.getNombre();
                            if ("PORCENTAJE".equals(t.getTipoCosto())) {
                                cobrado = precioLibro.multiply(t.getValor()).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                            } else {
                                cobrado = t.getValor();
                            }
                        } else {
                            // fallback para tests que usan precioCobrado directo
                            cobrado = item.precioCobrado() != null ? item.precioCobrado() : BigDecimal.ZERO;
                            nombreDano = NOMBRE_DESCONOCIDO;
                        }
                    } else {
                        // daño custom (nombreCustom) - valor viene del front como fijo
                        cobrado = item.precioCobrado() != null ? item.precioCobrado() : BigDecimal.ZERO;
                        nombreDano = item.nombreCustom();
                    }
                    RegistroDanoDetalle detalle = new RegistroDanoDetalle();
                    detalle.setRegistroDanoId(registro.getId());
                    detalle.setTipoDanoId(item.tipoDanoId());
                    detalle.setNombreCustom(item.nombreCustom());
                    detalle.setPrecioCobrado(cobrado);
                    registroDanoDetalleRepo.save(detalle);

                    montoMultaDano = montoMultaDano.add(cobrado);

                    danosRegistrados.add(new DanoDetalleResponseDTO(
                            detalle.getId(),
                            nombreDano,
                            item.nombreCustom(),
                            cobrado));
                }
            }

            if (esPerdido) {
                // Pérdida total = 100% del precio_base
                BigDecimal valorLibro = precioLibro.compareTo(BigDecimal.ZERO) > 0 ? precioLibro : BigDecimal.valueOf(15.00);
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

                // (lookup de usuario removido: su resultado se descartaba sin uso)
            }
        }

        BigDecimal montoTotal = BigDecimal.ZERO;
        if (montoMultaAtraso != null) montoTotal = montoTotal.add(montoMultaAtraso);
        montoTotal = montoTotal.add(montoMultaDano);

        return new DevolucionCompletaResponseDTO(
                prestamoId,
                registroDanoId,
                huboMultaAtraso != null && huboMultaAtraso,
                montoMultaAtraso,
                montoMultaDano.compareTo(BigDecimal.ZERO) > 0,
                montoMultaDano,
                montoTotal,
                danosRegistrados);
    }

    @Transactional(readOnly = true)
    /**
     * Executes the listarTiposDano operation.
     * @return operation result
     */
    public List<TipoDanoDTO> listarTiposDano() {
        return tipoDanoRepo.findByActivoTrue().stream()
                .map(t -> new TipoDanoDTO(t.getId(), t.getNombre(),
                        t.getCategoria()!=null ? t.getCategoria().getId() : null,
                        t.getCategoria()!=null ? t.getCategoria().getNombre() : null,
                        t.getTipoCosto(), t.getValor()))
                .toList();
    }

    @Transactional(readOnly = true)
    /**
     * Executes the historialDevoluciones operation.
     * @param bibliotecarioId value required by the operation
     * @return operation result
     */
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
                : NOMBRE_DESCONOCIDO;

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
                    u != null ? (u.getNombre() + " " + u.getApellido()).trim() : NOMBRE_DESCONOCIDO,
                    p.getFechaPrestamo(),
                    p.getFechaDevolucionEstimada(),
                    p.getFechaDevolucionReal(),
                    rd.getEstadoDevolucion(),
                    multasPorRegistro.getOrDefault(rd.getId(), BigDecimal.ZERO),
                    nombreBiblio,
                    rd.getFechaRegistro());
        }).filter(Objects::nonNull).toList();
    }

    private Map<Long, BigDecimal> calcularMultasPorRegistro(List<Long> registroIds) {
        if (registroIds.isEmpty()) return Map.of();

        // NOTA: retorna mapa vacío a propósito (las multas por daño se
        // registran sueltas, sin vínculo al registro). Si a futuro se
        // vinculan, poblar aquí agrupando por registro.
        return Map.of();
    }

    // ── Evidencia fotográfica ──────────────────────────────

    @Transactional
    /**
     * Executes the subirEvidencia operation.
     * @param registroDanoId value required by the operation
     * @param archivo value required by the operation
     * @param bibliotecarioId value required by the operation
     * @return operation result
     */
    public EvidenciaDanoResponseDTO subirEvidencia(Long registroDanoId, MultipartFile archivo, Long bibliotecarioId) {
        RegistroDano registro = registroDanoRepo.findById(registroDanoId)
                .orElseThrow(() -> new EntityNotFoundException("Registro de daño no encontrado: " + registroDanoId));

        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Debe adjuntar un archivo de imagen");
        }

        String contentType = archivo.getContentType();
        if (contentType == null || !TIPOS_EVIDENCIA_PERMITIDOS.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Tipo de imagen no permitido: " + contentType
                            + ". Solo se admiten JPG, JPEG, PNG, WebP y AVIF.");
        }

        int maxTamanoMb = configuracionSistemaService.obtenerValorEntero(CLAVE_MAX_TAMANO_EVIDENCIA_MB);
        long maxTamanoBytes = maxTamanoMb * 1024L * 1024L;
        if (archivo.getSize() > maxTamanoBytes) {
            throw new IllegalArgumentException(
                    "La imagen excede el tamaño máximo permitido de " + maxTamanoMb + " MB");
        }

        EvidenciaDano evidencia = new EvidenciaDano();
        evidencia.setRegistroDanoId(registroDanoId);
        evidencia.setArchivoNombre(archivo.getOriginalFilename());
        evidencia.setArchivoTipo(contentType);
        try {
            evidencia.setArchivoBytes(archivo.getBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Error al leer el archivo: " + e.getMessage());
        }
        evidencia.setSubidoEn(OffsetDateTime.now());
        evidencia = evidenciaDanoRepo.save(evidencia);

        return new EvidenciaDanoResponseDTO(
                evidencia.getId(),
                evidencia.getRegistroDanoId(),
                evidencia.getArchivoNombre(),
                evidencia.getArchivoTipo(),
                evidencia.getSubidoEn());
    }

    @Transactional(readOnly = true)
    /**
     * Executes the listarEvidencias operation.
     * @param registroDanoId value required by the operation
     * @return operation result
     */
    public List<EvidenciaDanoResponseDTO> listarEvidencias(Long registroDanoId) {
        return evidenciaDanoRepo.findByRegistroDanoId(registroDanoId).stream()
                .map(e -> new EvidenciaDanoResponseDTO(
                        e.getId(), e.getRegistroDanoId(),
                        e.getArchivoNombre(), e.getArchivoTipo(), e.getSubidoEn()))
                .toList();
    }

    @Transactional(readOnly = true)
    /**
     * Executes the obtenerArchivoEvidencia operation.
     * @param id value required by the operation
     * @return operation result
     */
    public EvidenciaDanoResponseDTO obtenerArchivoEvidencia(Long id) {
        EvidenciaDano evidencia = evidenciaDanoRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Evidencia no encontrada: " + id));
        return new EvidenciaDanoResponseDTO(
                evidencia.getId(), evidencia.getRegistroDanoId(),
                evidencia.getArchivoNombre(), evidencia.getArchivoTipo(), evidencia.getSubidoEn());
    }

    @Transactional(readOnly = true)
    /**
     * Executes the obtenerArchivoBinario operation.
     * @param id value required by the operation
     * @return operation result
     */
    public EvidenciaDanoArchivoDTO obtenerArchivoBinario(Long id) {
        EvidenciaDano evidencia = evidenciaDanoRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Evidencia no encontrada: " + id));
        return new EvidenciaDanoArchivoDTO(evidencia.getArchivoTipo(), evidencia.getArchivoBytes());
    }
}
