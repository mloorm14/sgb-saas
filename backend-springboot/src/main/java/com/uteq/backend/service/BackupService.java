package com.uteq.backend.service;

import com.uteq.backend.entity.Backup;
import com.uteq.backend.repository.BackupRepository;
import com.uteq.backend.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class BackupService {

    private static final int MAX_DIAS = 30;
    private static final Set<String> TABLAS_PERMITIDAS = Set.of(
            "prestamos", "reservas", "reservaciones", "multas", "libros", "usuarios",
            "bitacora_auditoria", "auditoria", "configuracion_sistema",
            "notificaciones", "favoritos", "sugerencias_adquisicion", "categorias", "autores"
    );

    private static final Map<String, String> TABLA_COL = Map.ofEntries(
            Map.entry("prestamos", "fecha_prestamo"),
            Map.entry("reservas", "fecha_reserva"),
            Map.entry("reservaciones", "fecha_reserva"),
            Map.entry("multas", "fecha_generada"),
            Map.entry("bitacora_auditoria", "fecha_hora"),
            Map.entry("auditoria", "fecha_hora"),
            Map.entry("libros", "fecha_registro"),
            Map.entry("usuarios", "fecha_registro"),
            Map.entry("notificaciones", "creado_en"),
            Map.entry("favoritos", "agregado_en"),
            Map.entry("sugerencias_adquisicion", "creado_en")
            // categorias, autores, configuracion_sistema -> sin fecha, volcado completo
    );

    private final BackupRepository backupRepository;
    private final UsuarioRepository usuarioRepository;
    private final JdbcTemplate jdbcTemplate;
    private final BackupStorageService storageService;

    @Value("${app.backup.r2.bucket:}")
    private String bucket;

    public BackupService(BackupRepository backupRepository, UsuarioRepository usuarioRepository, JdbcTemplate jdbcTemplate, BackupStorageService storageService) {
        this.backupRepository = backupRepository;
        this.usuarioRepository = usuarioRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.storageService = storageService;
    }

    private void validarRango(OffsetDateTime desde, OffsetDateTime hasta) {
        if (desde == null || hasta == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "desde y hasta son obligatorios");
        if (desde.isAfter(hasta)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "desde debe ser anterior a hasta");
        long dias = ChronoUnit.DAYS.between(desde, hasta);
        if (dias > MAX_DIAS) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rango max 30 dias, solicitados: " + dias);
        if (hasta.isAfter(OffsetDateTime.now())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hasta no puede ser futuro");
    }

    private void validarTablas(Set<String> tablas) {
        if (tablas == null || tablas.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar al menos una tabla");
        for (String t : tablas) if (!TABLAS_PERMITIDAS.contains(t)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tabla no permitida: " + t);
    }

    @Transactional
    public Backup generarBackup(OffsetDateTime desde, OffsetDateTime hasta, Set<String> tablas, String formato) {
        validarRango(desde, hasta);
        validarTablas(tablas);
        String fmt = formato == null ? "sql" : formato.toLowerCase();
        if (!fmt.equals("sql") && !fmt.equals("csv")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "formato debe ser sql o csv");

        byte[] zipBytes = generarZip(desde, hasta, tablas, fmt);
        String key = "backups/backup_" + System.currentTimeMillis() + ".zip";
        if (storageService.isEncryptionEnabled()) key += ".enc";
        storageService.upload(key, zipBytes);

        Backup backup = Backup.builder()
                .creadoPor(obtenerUsuarioActualId())
                .desde(desde)
                .hasta(hasta)
                .tablas(new HashSet<>(tablas))
                .formato(fmt)
                .ruta(key)
                .tamanoBytes((long) zipBytes.length)
                .estado("COMPLETADO")
                .creadoEn(OffsetDateTime.now())
                .build();
        return backupRepository.save(backup);
    }

    private byte[] generarZip(OffsetDateTime desde, OffsetDateTime hasta, Set<String> tablas, String formato) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            for (String tabla : tablas) {
                String col = TABLA_COL.get(tabla);
                String phys = physTable(tabla);
                List<Map<String, Object>> rows;
                if (col != null) {
                    rows = jdbcTemplate.queryForList("SELECT * FROM " + phys + " WHERE " + col + " >= ? AND " + col + " <= ?", desde, hasta);
                } else {
                    // sin columna de fecha (categorias, autores, configuracion_sistema) -> volcado completo
                    rows = jdbcTemplate.queryForList("SELECT * FROM " + phys);
                }
                String ext = fmtExt(formato);
                zos.putNextEntry(new ZipEntry(tabla + "." + ext));
                String content = formato.equals("sql") ? toSql(physTable(tabla), rows) : toCsv(rows);
                zos.write(content.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            zos.finish();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error generando zip", e);
        }
    }

    private String physName(String logical) {
        if (logical.equals("reservas")) return "reservaciones";
        if (logical.equals("auditoria")) return "bitacora_auditoria";
        return logical;
    }
    private String physTable(String logical) {
        if (logical.equals("reservas") || logical.equals("reservaciones")) return "reservaciones";
        if (logical.equals("auditoria") || logical.equals("bitacora_auditoria")) return "bitacora_auditoria";
        return logical;
    }
    private String fmtExt(String f) { return f.equals("sql") ? "sql" : "csv"; }

    private String toCsv(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", rows.get(0).keySet())).append("\n");
        for (Map<String, Object> r : rows) {
            sb.append(r.values().stream().map(v -> {
                if (v == null) return "";
                String s = v.toString().replace("\"", "\"\"");
                if (s.contains(",") || s.contains("\n") || s.contains("\"")) return "\"" + s + "\"";
                return s;
            }).collect(Collectors.joining(","))).append("\n");
        }
        return sb.toString();
    }

    private String toSql(String tabla, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return "-- sin filas " + tabla + "\n";
        StringBuilder sb = new StringBuilder("-- tabla " + tabla + "\n");
        for (Map<String, Object> r : rows) {
            String cols = String.join(", ", r.keySet());
            String vals = r.values().stream().map(v -> {
                if (v == null) return "NULL";
                if (v instanceof Number) return v.toString();
                if (v instanceof Boolean) return (Boolean) v ? "TRUE" : "FALSE";
                return "'" + v.toString().replace("'", "''") + "'";
            }).collect(Collectors.joining(", "));
            sb.append("INSERT INTO ").append(tabla).append(" (").append(cols).append(") VALUES (").append(vals).append(");\n");
        }
        return sb.toString();
    }

    public List<Backup> listarTodos() { return backupRepository.findAllOrderByCreatedDesc(); }
    public List<Backup> listarPorRango(OffsetDateTime desde, OffsetDateTime hasta) { return backupRepository.findByFechaRange(desde, hasta); }
    public Backup obtenerPorId(Long id) { return backupRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Backup no encontrado " + id)); }

    @Transactional
    public void eliminar(Long id) {
        Backup b = obtenerPorId(id);
        try { storageService.delete(b.getRuta()); } catch (Exception ignored) {}
        backupRepository.delete(b);
    }

    public byte[] descargar(Long id) {
        Backup b = obtenerPorId(id);
        return storageService.download(b.getRuta());
    }

    private Long obtenerUsuarioActualId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return 1L;
        Object p = auth.getPrincipal();
        try {
            var f = p.getClass().getDeclaredField("id");
            f.setAccessible(true);
            return (Long) f.get(p);
        } catch (Exception e) {
            try { return usuarioRepository.findByCorreo(auth.getName()).map(u -> u.getId()).orElse(1L); } catch (Exception ex) { return 1L; }
        }
    }
}
