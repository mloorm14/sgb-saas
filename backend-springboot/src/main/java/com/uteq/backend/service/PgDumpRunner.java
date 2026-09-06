package com.uteq.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Ejecuta {@code pg_dump -Fc} como proceso hijo (reemplaza a backup-service/src/dump.js).
 * <p>
 * La conexión se deriva de la misma URL JDBC del backend (solo se quita el
 * prefijo {@code jdbc:}); libpq negocia SSL en modo {@code prefer}, que sirve
 * tanto para Postgres local (sin SSL) como gestionado (Render/Neon, con SSL).
 * Timeout duro de 5 minutos: si pg_dump cuelga, se destruye y lanza excepción
 * para que el registro quede en {@code fallido} y el cerrojo se libere.
 */
@Component
public class PgDumpRunner {

    private static final Logger log = LoggerFactory.getLogger(PgDumpRunner.class);

    private static final long TIMEOUT_MINUTOS = 5;

    public void dump(Path destino, String jdbcUrl, String usuarioFallback, String passwordFallback) throws Exception {
        String raw = jdbcUrl.trim();
        if (raw.startsWith("jdbc:")) raw = raw.substring("jdbc:".length());
        URI uri = new URI(raw);

        String userInfo = uri.getUserInfo();
        String usuario = usuarioFallback;
        String password = passwordFallback;
        if (userInfo != null) {
            String[] partes = userInfo.split(":", 2);
            usuario = URLDecoder.decode(partes[0], StandardCharsets.UTF_8);
            if (partes.length > 1) {
                password = URLDecoder.decode(partes[1], StandardCharsets.UTF_8);
            }
        }
        String host = uri.getHost() != null ? uri.getHost() : "localhost";
        String puerto = uri.getPort() > 0 ? String.valueOf(uri.getPort()) : "5432";
        String base = uri.getPath() != null ? uri.getPath().replaceFirst("^/", "") : "";
        if (base.isBlank()) throw new IllegalArgumentException("URL de BD sin base de datos: " + jdbcUrl);

        ProcessBuilder pb = new ProcessBuilder(
                "pg_dump",
                "-h", host,
                "-p", puerto,
                "-U", usuario,
                "-d", base,
                "-Fc",
                "-f", destino.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        if (password != null && !password.isBlank()) {
            pb.environment().put("PGPASSWORD", password);
        }
        log.info("pg_dump hacia {}:{}/{} (timeout {} min)", host, puerto, base, TIMEOUT_MINUTOS);
        Process proceso = pb.start();
        boolean termino = proceso.waitFor(TIMEOUT_MINUTOS, TimeUnit.MINUTES);
        if (!termino) {
            proceso.destroyForcibly();
            throw new IllegalStateException("pg_dump superó el timeout de " + TIMEOUT_MINUTOS + " minutos");
        }
        if (proceso.exitValue() != 0) {
            String salida = new String(proceso.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException("pg_dump salió con código " + proceso.exitValue() + ": " + salida);
        }
    }
}
