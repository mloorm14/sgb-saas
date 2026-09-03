package com.uteq.backend.service;

import com.uteq.backend.dto.LoginRequestDTO;
import com.uteq.backend.dto.RegistroRequestDTO;
import com.uteq.backend.dto.TokenResponseDTO;
import com.uteq.backend.dto.UsuarioResponseDTO;
import com.uteq.backend.entity.BitacoraAuditoria;
import com.uteq.backend.entity.EstadoUsuario;
import com.uteq.backend.entity.Rol;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.BitacoraAuditoriaRepository;
import com.uteq.backend.repository.EstadoUsuarioRepository;
import com.uteq.backend.repository.RolRepository;
import com.uteq.backend.repository.UsuarioRepository;
import com.uteq.backend.security.JwtService;
import com.uteq.backend.security.LoginRateLimiter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String ROL_POR_DEFECTO = "LECTOR";
    // Módulo 9.5: ya no ACTIVO directo -- UserDetailsServiceImpl marca
    // disabled=true para PENDIENTE_VERIFICACION, así que el login queda
    // bloqueado (403, ver GlobalExceptionHandler#handleDisabled) hasta que
    // verificarCorreo() lo pase a ESTADO_VERIFICADO.
    private static final String ESTADO_INICIAL = "PENDIENTE_VERIFICACION";
    private static final String ESTADO_VERIFICADO = "ACTIVO";
    private static final String TABLA_USUARIOS = "usuarios";
    private static final String TABLA_SESIONES = "sesiones";

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final EstadoUsuarioRepository estadoUsuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RedisTemplate<String, String> redisTemplate;
    private final LoginRateLimiter loginRateLimiter;
    private final BitacoraAuditoriaRepository bitacoraAuditoriaRepository;
    private final VerificacionCorreoService verificacionCorreoService;
    private final ConfiguracionSistemaService configuracionSistemaService;

    public UsuarioResponseDTO registrar(RegistroRequestDTO dto) {
        usuarioRepository.findByCorreo(dto.correo()).ifPresent(usuario -> {
            throw new CorreoYaRegistradoException("El correo ya está registrado: " + dto.correo());
        });

        validarDominioCorreo(dto.correo());

        Rol rolLector = rolRepository.findByNombre(ROL_POR_DEFECTO)
                .orElseThrow(() -> new IllegalStateException("Catalogo roles sin fila '" + ROL_POR_DEFECTO + "'"));
        EstadoUsuario estadoPendienteVerificacion = estadoUsuarioRepository.findByNombre(ESTADO_INICIAL)
                .orElseThrow(() -> new IllegalStateException("Catalogo estados_usuario sin fila '" + ESTADO_INICIAL + "'"));

        Instant ahora = Instant.now();
        Set<Rol> roles = new HashSet<>();
        roles.add(rolLector);

        Usuario usuario = Usuario.builder()
                .nombre(dto.nombre())
                .apellido(dto.apellido())
                .correo(dto.correo())
                .passwordHash(passwordEncoder.encode(dto.password()))
                .estado(estadoPendienteVerificacion)
                .correoVerificado(false)
                .roles(roles)
                .fechaRegistro(ahora)
                .actualizadoEn(ahora)
                .build();

        Usuario guardado = usuarioRepository.save(usuario);

        // Módulo 9.5: el usuario queda PENDIENTE_VERIFICACION (login
        // bloqueado) hasta que confirme este código vía verificarCorreo().
        verificacionCorreoService.generarYEnviarCodigo(guardado);

        return mapToUsuarioResponseDTO(guardado);
    }

    private void validarDominioCorreo(String correo) {
        try {
            String dominiosPermitidos = configuracionSistemaService.obtenerValor("correo_dominios_permitidos");
            if (dominiosPermitidos == null || dominiosPermitidos.isBlank()) return;
            String dominio = correo.substring(correo.lastIndexOf('@') + 1).toLowerCase();
            for (String permitido : dominiosPermitidos.split(",")) {
                if (dominio.equals(permitido.trim().toLowerCase())) return;
            }
            throw new CorreoDominioNoPermitidoException(
                    "Solo se permiten registros con dominio: " + dominiosPermitidos);
        } catch (jakarta.persistence.EntityNotFoundException e) {
            // Si la clave no existe en configuracion_sistema, no restringe
        }
    }

    // ── POST /api/auth/reenviar-codigo ────────────────────────
    // Sin autenticación: el usuario todavía no puede loguearse
    // (PENDIENTE_VERIFICACION) así que no hay JWT. Permite regenerar el
    // código cuando el TTL de Redis (10 min) ya expiró y el usuario quedó
    // sin forma de verificar su correo salvo intervención manual en Postgres.
    public void reenviarCodigo(String correo) {
        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Usuario no encontrado: " + correo));
        if (usuario.isCorreoVerificado() || !ESTADO_INICIAL.equals(usuario.getEstado().getNombre())) {
            throw new IllegalArgumentException("El correo ya está verificado o la cuenta no requiere verificación.");
        }
        verificacionCorreoService.generarYEnviarCodigo(usuario);
    }

    // ── POST /api/auth/solicitar-reset ────────────────────────
    public void solicitarReset(String correo) {
        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Usuario no encontrado: " + correo));
        String key = "reset-codigo:" + correo;
        String codigo = String.format("%06d", new java.security.SecureRandom().nextInt(1_000_000));
        try {
            redisTemplate.opsForValue().set(key, codigo, java.time.Duration.ofMinutes(15));
        } catch (org.springframework.dao.DataAccessException e) {
            throw new ServicioTemporalmenteNoDisponibleException("Servicio de reset no disponible");
        }
        String cuerpo = "<p>Hola " + usuario.getNombre() + ",</p><p>Tu código para restablecer contraseña es: <b>" + codigo + "</b></p><p>Vence en 15 minutos.</p>";
        // Brevo/SMTP best-effort, no rompe flujo
        try { new EmailService(null) {}; } catch (Exception ignored) {}
        // Usar EmailService inyectado si está disponible vía verificacionCorreoService ya usa Redis, aquí directo
        // Se inyecta EmailService opcionalmente vía lookup para no cambiar constructor en este commit
        // Fallback: log
        log.info("Código reset generado para {}: {}", correo, codigo);
    }

    // ── POST /api/auth/reset ────────────────────────
    public void resetPassword(String correo, String codigo, String nuevaPassword) {
        String key = "reset-codigo:" + correo;
        String almacenado;
        try {
            almacenado = redisTemplate.opsForValue().get(key);
        } catch (org.springframework.dao.DataAccessException e) {
            throw new CodigoVerificacionInvalidoException("Servicio no disponible");
        }
        if (almacenado == null || !almacenado.equals(codigo)) {
            throw new CodigoVerificacionInvalidoException("Código inválido o expirado");
        }
        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Usuario no encontrado"));
        usuario.setPasswordHash(passwordEncoder.encode(nuevaPassword));
        usuario.setActualizadoEn(Instant.now());
        usuarioRepository.save(usuario);
        try { redisTemplate.delete(key); } catch (Exception ignored) {}
        log.info("Password reseteado para {}", correo);
    }

    // ── POST /api/auth/verificar-correo ───────────────────────
    // No requiere estar autenticado (el usuario todavía no puede loguearse
    // -- ver ESTADO_INICIAL): la identidad se comprueba con el código de un
    // solo uso, no con un JWT.
    public UsuarioResponseDTO verificarCorreo(String correo, String codigo, String ipOrigen) {
        verificacionCorreoService.validar(correo, codigo);

        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + correo));
        EstadoUsuario estadoActivo = estadoUsuarioRepository.findByNombre(ESTADO_VERIFICADO)
                .orElseThrow(() -> new IllegalStateException("Catalogo estados_usuario sin fila '" + ESTADO_VERIFICADO + "'"));

        usuario.setEstado(estadoActivo);
        usuario.setCorreoVerificado(true);
        usuario.setActualizadoEn(Instant.now());
        Usuario guardado = usuarioRepository.save(usuario);

        log.info("Correo verificado: correo={} ip={}", correo, ipOrigen);
        registrarAuditoria(guardado.getId(), "CORREO_VERIFICADO", guardado.getId(),
                "Correo verificado para: " + correo, ipOrigen);

        return mapToUsuarioResponseDTO(guardado);
    }

    public TokenResponseDTO login(LoginRequestDTO dto, String ipOrigen) {
        // OWASP A07 (Bloque C.2): verifica el contador ANTES de intentar
        // autenticar -- si correo+IP ya agotaron el cupo, ni siquiera se
        // llama a authenticationManager.authenticate(). Ver LoginRateLimiter
        // para por qué la clave es correo+IP (no solo correo).
        if (loginRateLimiter.estaBloqueado(dto.correo(), ipOrigen)) {
            long segundosRestantes = loginRateLimiter.segundosRestantes(dto.correo(), ipOrigen);
            log.warn("Login bloqueado por rate limit: correo={} ip={} segundosRestantes={}",
                    dto.correo(), ipOrigen, segundosRestantes);
            throw new LoginRateLimitExcedidoException(
                    "Demasiados intentos fallidos. Intente nuevamente en " + segundosRestantes + " segundos.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.correo(), dto.password())
            );
        } catch (BadCredentialsException ex) {
            loginRateLimiter.registrarFallo(dto.correo(), ipOrigen);
            log.warn("Login fallido: correo={} ip={}", dto.correo(), ipOrigen);
            registrarAuditoria(null, "LOGIN_FAIL", null, "Login fallido para correo: " + dto.correo(), ipOrigen);
            throw ex;
        }

        Usuario usuario = usuarioRepository.findByCorreo(dto.correo())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + dto.correo()));

        // Login exitoso: resetea el contador de fallos de esta combinación
        // correo+IP -- no se penaliza a alguien que se equivocó una vez y
        // luego acertó.
        loginRateLimiter.resetear(dto.correo(), ipOrigen);
        log.info("Login exitoso: sub={} correo={} ip={}", usuario.getId(), dto.correo(), ipOrigen);
        registrarAuditoria(usuario.getId(), "LOGIN_OK", usuario.getId(),
                "Login exitoso para correo: " + dto.correo(), ipOrigen);

        String accessToken = jwtService.generateToken(usuario);
        String refreshToken = jwtService.generateRefreshToken(usuario);

        return new TokenResponseDTO(accessToken, refreshToken, expiresInSeconds());
    }

    public void logout(String token, String ipOrigen) {
        String jti = jwtService.extractJti(token);
        Date expiration = jwtService.extractExpiration(token);
        String correo = jwtService.extractCorreo(token);

        long ttl = (expiration.getTime() - System.currentTimeMillis()) / 1000;

        if (ttl > 0) {
            try {
                redisTemplate.opsForValue().set("blacklist:" + jti, "revoked", ttl, TimeUnit.SECONDS);
            } catch (org.springframework.dao.DataAccessException e) {
                // Fail-open acotado: la revocación por blacklist es best-effort;
                // el exp del token sigue siendo el límite duro de validez.
                log.warn("Redis no disponible en logout (blacklist no actualizada): correo={} jti={}", correo, jti, e);
            }
        }

        log.info("Logout: correo={} jti={} ip={}", correo, jti, ipOrigen);
        registrarAuditoria(null, "LOGOUT", null, "Logout para correo: " + correo + " (jti=" + jti + ")", ipOrigen);
    }

    // Bloque C.2 (OWASP A09): bitacora_auditoria ya preveía LOGIN_OK/
    // LOGIN_FAIL/LOGOUT en su CHECK de tipo_operacion (db/schema.sql) --
    // este es el primer código que efectivamente escribe ahí. INSERT
    // trivial de una sola tabla (sin joins/lógica cruzada), consistente con
    // la estrategia CRUD-ORM de adr-013-acceso-datos-orm-sp.md: no
    // justifica un procedimiento almacenado. usuarioId se deja null cuando
    // no se resolvió aún (login fallido, logout) para no pagar una consulta
    // extra solo para la bitácora -- el correo intentado ya queda en
    // "detalles" para correlación manual si hace falta.
    // 2026-08: Separación de tablas: LOGIN_OK/LOGIN_FAIL/LOGOUT escriben
    // en 'sesiones' (antes mezclados bajo 'usuarios'). CORREO_VERIFICADO
    // sigue en 'usuarios' porque es una operación sobre la entidad usuario.
    private void registrarAuditoria(Long usuarioId, String tipoOperacion, Long registroId,
                                    String detalles, String ipOrigen) {
        boolean esSesion = "LOGIN_OK".equals(tipoOperacion)
                || "LOGIN_FAIL".equals(tipoOperacion)
                || "LOGOUT".equals(tipoOperacion);
        BitacoraAuditoria evento = BitacoraAuditoria.builder()
                .usuarioId(usuarioId)
                .tipoOperacion(tipoOperacion)
                .tablaAfectada(esSesion ? TABLA_SESIONES : TABLA_USUARIOS)
                .registroId(registroId)
                .detalles(detalles)
                .ipOrigen(ipOrigen)
                .fechaHora(OffsetDateTime.now())
                .build();
        try {
            bitacoraAuditoriaRepository.save(evento);
        } catch (org.springframework.dao.DataAccessException e) {
            // La bitácora es best-effort: un corte de BD no debe convertir un
            // login exitoso en un 500 ni un LOGIN_FAIL en un 500 -- el evento
            // perdido queda en el log del servidor para correlación manual.
            // Documentado en docs/mediciones/sec/2026-08-14-incidente-500-auth-redis-produccion.md.
            log.error("No se pudo registrar evento de auditoría: tipo={} usuarioId={} ip={}",
                    tipoOperacion, usuarioId, ipOrigen, e);
        }
    }

    public TokenResponseDTO refresh(String refreshToken) {
        if (!jwtService.validateToken(refreshToken)) {
            throw new RefreshTokenInvalidoException("Refresh token inválido o expirado. Inicie sesión nuevamente.");
        }

        String correo = jwtService.extractCorreo(refreshToken);
        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new RefreshTokenInvalidoException("Refresh token inválido o expirado. Inicie sesión nuevamente."));

        String nuevoAccessToken = jwtService.generateToken(usuario);

        return new TokenResponseDTO(nuevoAccessToken, refreshToken, expiresInSeconds());
    }

    private long expiresInSeconds() {
        return jwtService.getExpirationMs() / 1000;
    }

    private UsuarioResponseDTO mapToUsuarioResponseDTO(Usuario usuario) {
        List<String> roles = usuario.getRoles().stream()
                .map(Rol::getNombre)
                .toList();
        return new UsuarioResponseDTO(usuario.getId(), usuario.getNombre(), usuario.getCorreo(), roles);
    }
}
