package com.uteq.backend.service;

import com.uteq.backend.entity.Backup;
import com.uteq.backend.entity.Reserva;
import com.uteq.backend.entity.Multa;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.BackupRepository;
import com.uteq.backend.repository.ReservaRepository;
import com.uteq.backend.repository.MultaRepository;
import com.uteq.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.time.*;
import java.time.temporal.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BackupService {

    private static final int MAX_DIAS = 30;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${backup.storage.url:local}")
    private String backupStorageUrl;

    @Value("${backup.encryption.key:}")
    private String backupEncryptionKey;

    private final BackupRepository backupRepository;
    private final ReservaRepository reservaRepository;
    private final MultaRepository multaRepository;
    private final UsuarioRepository usuarioRepository;

    public BackupService(BackupRepository backupRepository,
                         ReservaRepository reservaRepository,
                         MultaRepository multaRepository,
                         UsuarioRepository usuarioRepository) {
        this.backupRepository = backupRepository;
        this.reservaRepository = reservaRepository;
        this.multaRepository = multaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    private void validarRango(OffsetDateTime desde, OffsetDateTime hasta) {
        if (desde == null || hasta == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los parámetros 'desde' y 'hasta' son obligatorios");
        }
        if (desde.isAfter(hasta)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El parámetro 'desde' debe ser anterior a 'hasta'");
        }
        long dias = ChronoUnit.DAYS.between(desde, hasta);
        if (dias > MAX_DIAS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rango máximo de backup es de " + MAX_DIAS + " días. Solicitados: " + dias + " días");
        }
        if (hasta.isAfter(OffsetDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El parámetro 'hasta' no puede ser una fecha futura");
        }
    }

    private void validarTablas(Set<String> tablas) {
        if (tablas == null || tablas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar al menos una tabla para el backup");
        }
        Set<String> tablasPermitidas = Set.of("prestamos", "reservas", "multas", "libros", "usuarios", "bitacora_auditoria");
        for (String tabla : tablas) {
            if (!tablasPermitidas.contains(tabla)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tabla no permitida: " + tabla);
            }
        }
    }

    @Transactional
    public Backup generarBackup(BackupRequestDTO req) {
        validarRango(req.getDesde(), req.getHasta());
        validarTablas(req.getTablas());

        OffsetDateTime desde = req.getDesde();
        OffsetDateTime hasta = req.getHasta();
        Set<String> tablas = req.getTablas();
        String formato = req.getFormato();

        String sqlContent = generarSqlBackup(tabla, formato, desde, hasta);

        String rutaAlmacenamiento;
        if ("s3://".equals(backupStorageUrl.substring(0, 5))) {
            rutaAlmacenamiento = "s3://" + backupStorageUrl.replace("s3://", "") + "/backup_" + System.currentTimeMillis() + ".zip";
        } else {
            rutaAlmacenamiento = "./backups/backup_" + System.currentTimeMillis() + ".zip";
            File dir = new File("./backups");
            if (!dir.exists()) dir.mkdirs();
            try (FileOutputStream fos = new FileOutputStream(rutaAlmacenamiento)) {
                fos.write(sqlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error guardando backup local", e);
            }
        }

        Backup backup = Backup.builder()
                .creadoPor(obtenerUsuarioActualId())
                .desde(desde)
                .hasta(hasta)
                .tablas(tablas)
                .formato(formato)
                .ruta(rutaAlmacenamiento)
                .tamanoBytes(sqlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .estado("COMPLETADO")
                .creadoEn(OffsetDateTime.now())
                .build();

        return backupRepository.save(backup);
    }

    private String generarSqlBackup(String tabla, String formato, OffsetDateTime desde, OffsetDateTime hasta) {
        StringBuilder sb = new StringBuilder();
        if ("sql".equals(formato)) {
            sb.append("-- Backup generado: ").append(formato).append(" rango ").append(desde).append(" - ").append(hasta).append("\n\n");
            switch (tabla) {
                case "prestamos":
                    sb.append("-- Tabla: prestamos\n").append("COPY (SELECT * FROM prestamos WHERE fecha_devolucion_estimada BETWEEN '")
                            .append(formatTimestamp(desde)).append("' AND '").append(formatTimestamp(hasta)).append("') TO STDOUT WITH (FORMAT text, HEADER);\n");
                    break;
                case "reservas":
                    sb.append("-- Tabla: reservas\n").append("COPY (SELECT * FROM reservas WHERE fecha_reserva BETWEEN '")
                            .append(formatTimestamp(desde)).append("' AND '").append(formatTimestamp(hasta)).append("') TO STDOUT WITH (FORMAT text, HEADER);\n");
                    break;
                case "multas":
                    sb.append("-- Tabla: multas\n").append("COPY (SELECT * FROM multas WHERE fecha_multa BETWEEN '")
                            .append(formatTimestamp(desde)).append("' AND '").append(formatTimestamp(hasta)).append("') TO STDOUT WITH (FORMAT text, HEADER);\n");
                    break;
                case "libros":
                    sb.append("-- Tabla: libros (sin filtro de fecha - todo el registro)\n").append("COPY (SELECT id, titulo, isbn, autor, categoria FROM libros) TO STDOUT WITH (FORMAT text, HEADER);\n");
                    break;
                case "usuarios":
                    sb.append("-- Tabla: usuarios (sin filtro de fecha - datos básicos)\n").append("COPY (SELECT id, nombre, email, rol FROM usuarios) TO STDOUT WITH (FORMAT text, HEADER);\n");
                    break;
                case "bitacora_auditoria":
                    sb.append("-- Tabla: bitacora_auditoria\n").append("COPY (SELECT * FROM bitacora_auditoria WHERE created_en BETWEEN '")
                            .append(formatTimestamp(desde)).append("' AND '").append(formatTimestamp(hasta)).append("') TO STDOUT WITH (FORMAT text, HEADER);\n");
                    break;
                default:
                    sb.append("-- Tabla no reconocida: ").append(tabla).append("\n");
            }
        } else if ("csv".equals(formato)) {
            sb.append("-- Backup CSV: tabla ").append(tabla).append("\n").append("id,titulo,isbn,autor\n");
        }
        return sb.toString();
    }

    private String formatTimestamp(OffsetDateTime offsetDateTime) {
        return offsetDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx"));
    }

    public List<Backup> listarTodos() {
        return backupRepository.findAllOrderByCreatedDesc();
    }

    public Backup obtenerPorId(Long id) {
        return backupRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Backup no encontrado con id: " + id));
    }

    public List<Backup> listarPorRango(OffsetDateTime desde, OffsetDateTime hasta) {
        return backupRepository.findByFechaRange(desde, hasta);
    }

    private Long obtenerUsuarioActualId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return 1L;
        Object principal = auth.getPrincipal();
        if (principal instanceof Usuario usuario) {
            return usuario.getId();
        }
        try {
            return usuarioRepository.findByEmail((String) principal).getId();
        } catch (Exception e) {
            return 1L;
        }
    }

    public static class BackupRequestDTO {
        OffsetDateTimeDesde;
        OffsetDateTimeHasta;
        Set<String> tablas;
        String formato;

        OffsetDateTime getDesde() { return desde; }
        void setDesde(OffsetDateTimeDesde desde) { this.desde = desde; }
        OffsetDateTime getHasta() { return hasta; }
        void setHasta(OffsetDateTimeHasta hasta) { this.hasta = hasta; }
        Set<String> getTablas() { return tablas; }
        void setTablas(Set<String> tablas) { this.tablas = tablas; }
        String getFormato() { return formato; }
        void setFormato(String formato) { this.formato = formato; }
    }
}