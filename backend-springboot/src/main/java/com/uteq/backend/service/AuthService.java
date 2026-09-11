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
    // Login bloqueado hasta verificar el correo → verificarCorreo() lo pasa a ACTIVO (403 mientras tanto).
    private static final String ESTADO_INICIAL = "PENDIENTE_VERIFICACION";
    private static final String ESTADO_VERIFICADO = "ACTIVO";
    private static final String TABLA_USUARIOS = "usuarios";
    private static final String TABLA_SESIONES = "sesiones";
    private static final String USUARIO_NO_ENCONTRADO = "Usuario no encontrado: ";

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
    private final EmailService emailService;

    /**
     * Crea una cuenta nueva con rol LECTOR en estado PENDIENTE_VERIFICACION para permitir el registro
     * autónomo y dejarla lista para la confirmación por correo.
     * Valida que el correo no esté duplicado ni pertenezca a un dominio restringido, cifra la
     * contraseña y dispara el envío del código de verificación (ver {@link #verificarCorreo}).
     *
     * @param dto solicitud con nombre, apellido, correo de acceso y contraseña en claro sin cifrar
     * @return vista resumida del usuario persistido con identificador, nombre, correo y roles asignados
     * @throws CorreoYaRegistradoException si ya existe un usuario con el correo solicitado
     * @throws CorreoDominioNoPermitidoException si el dominio del correo no figura entre los permitidos
     * @throws IllegalStateException si faltan las filas de catálogo del rol LECTOR o del estado inicial
     */
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

        // Queda PENDIENTE_VERIFICACION hasta confirmar el código vía verificarCorreo().
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

    // ── POST /api/auth/reenviar-codigo ──
    // Sin JWT (aún no puede loguearse): regenera el código cuando el TTL de Redis ya expiró.
    /**
     * Regenera y reenvía el código de verificación para una cuenta aún pendiente, de modo que el
     * titular pueda completar la activación cuando el código anterior ya expiró en Redis.
     * Rechaza la operación si la cuenta ya quedó verificada.
     *
     * @param correo dirección asociada a la cuenta pendiente de verificación
     * @throws jakarta.persistence.EntityNotFoundException si no existe ningún usuario con ese correo
     * @throws IllegalArgumentException si el correo ya está verificado o la cuenta no requiere verificación
     */
    public void reenviarCodigo(String correo) {
        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(USUARIO_NO_ENCONTRADO + correo));
        if (usuario.isCorreoVerificado() || !ESTADO_INICIAL.equals(usuario.getEstado().getNombre())) {
            throw new IllegalArgumentException("El correo ya está verificado o la cuenta no requiere verificación.");
        }
        verificacionCorreoService.generarYEnviarCodigo(usuario);
    }

    // ── POST /api/auth/solicitar-reset ──
    // Recuperación en 2 pasos: código de 6 dígitos con TTL 10 min en Redis, enviado por correo.
    /**
     * Inicia la recuperación de acceso en dos pasos para que el titular pueda definir una contraseña
     * nueva sin estar autenticado. Genera un código aleatorio de 6 dígitos con vigencia de 10 minutos
     * en Redis y lo envía por correo como mecanismo best-effort (ver {@link #resetPassword}).
     *
     * @param correo dirección de la cuenta que solicita la recuperación
     * @throws jakarta.persistence.EntityNotFoundException si no existe ningún usuario con ese correo
     * @throws ServicioTemporalmenteNoDisponibleException si Redis no acepta el guardado del código
     */
    public void solicitarReset(String correo) {
        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(USUARIO_NO_ENCONTRADO + correo));
        String key = "reset-codigo:" + correo;
        String codigo = String.format("%06d", new java.security.SecureRandom().nextInt(1_000_000));
        try {
            redisTemplate.opsForValue().set(key, codigo, java.time.Duration.ofMinutes(10));
        } catch (org.springframework.dao.DataAccessException e) {
            throw new ServicioTemporalmenteNoDisponibleException("Servicio de reset no disponible");
        }
        String cuerpo = "<p>Hola " + usuario.getNombre() + ",</p>"
                + "<p>Tu código para recuperar la cuenta es: <b>" + codigo + "</b></p>"
                + "<p>Vence en 10 minutos.</p>";
        // Envío best-effort: si falla, el código queda en Redis y el usuario puede reintentar.
        boolean enviado = emailService.enviarCorreo(correo, "Recuperar cuenta - SGB-SaaS", cuerpo);
        if (!enviado) {
            log.warn("No se pudo enviar correo de recuperacion a {} (codigo en Redis)", correo);
        }
        log.info("Código de recuperacion generado para {}: {}", correo, codigo);
    }

    // ── POST /api/auth/reset ────────────────────────
    /**
     * Reemplaza la contraseña de una cuenta usando el código de recuperación de un solo uso, para
     * devolverle el acceso al titular tras validar su identidad sin JWT. Consume el código en Redis
     * cuando el cambio se persiste.
     *
     * @param correo dirección de la cuenta a recuperar
     * @param codigo código de 6 dígitos previamente generado por {@link #solicitarReset} y aún vigente en Redis
     * @param nuevaPassword contraseña en claro sin cifrar que reemplazará a la anterior
     * @throws CodigoVerificacionInvalidoException si el código no coincide, expiró o Redis no responde a la lectura
     * @throws jakarta.persistence.EntityNotFoundException si no existe ningún usuario con ese correo
     */
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
          try { redisTemplate.delete(key); } catch (Exception ignored) {
              // best-effort: si Redis cae, el reseteo ya se completó en BD
          }
        log.info("Password reseteado para {}", correo);
    }

    // ── POST /api/auth/verificar-correo ──
    // Sin JWT: la identidad se comprueba con el código de un solo uso.
    /**
     * Confirma el código de verificación de un solo uso y activa la cuenta, para desbloquear el inicio
     * de sesión que permanece restringido mientras el correo sigue pendiente. Registra el evento en la
     * bitácora de auditoría con la IP de origen.
     *
     * @param correo dirección pendiente de confirmación
     * @param codigo código de un solo uso previamente enviado al correo del titular
     * @param ipOrigen dirección IP desde donde se confirma, usada solo para auditoría y registro
     * @return vista resumida del usuario ya activado con identificador, nombre, correo y roles
     * @throws IllegalArgumentException si no existe ningún usuario con ese correo
     * @throws IllegalStateException si falta la fila de catálogo del estado ACTIVO
     */
    public UsuarioResponseDTO verificarCorreo(String correo, String codigo, String ipOrigen) {
        verificacionCorreoService.validar(correo, codigo);

        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new IllegalArgumentException(USUARIO_NO_ENCONTRADO + correo));
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

    /**
     * Autentica las credenciales y emite los tokens de sesión para mantener conectado al titular.
     * Aplica el límite de intentos por correo e IP antes de autenticar, reinicia el contador tras el
     * éxito y deja traza de cada resultado en la bitácora de auditoría.
     *
     * @param dto credenciales de acceso con correo y contraseña en claro sin cifrar
     * @param ipOrigen dirección IP desde donde se intenta el acceso, usada para el límite de intentos y auditoría
     * @return par de tokens con el JWT de acceso, el token de refresco y su vigencia en segundos
     * @throws LoginRateLimitExcedidoException si la combinación de correo e IP agotó los intentos permitidos
     * @throws org.springframework.security.authentication.BadCredentialsException si la contraseña o el usuario no son válidos
     * @throws RuntimeException si la autenticación prospera pero el usuario ya no existe en la base
     */
    public TokenResponseDTO login(LoginRequestDTO dto, String ipOrigen) {
        // Verifica el rate limit ANTES de autenticar → 429 si se agotó.
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
                .orElseThrow(() -> new RuntimeException(USUARIO_NO_ENCONTRADO + dto.correo()));

        // Login exitoso: resetea el contador de fallos de esta combinación correo+IP.
        loginRateLimiter.resetear(dto.correo(), ipOrigen);
        log.info("Login exitoso: sub={} correo={} ip={}", usuario.getId(), dto.correo(), ipOrigen);
        registrarAuditoria(usuario.getId(), "LOGIN_OK", usuario.getId(),
                "Login exitoso para correo: " + dto.correo(), ipOrigen);

        String accessToken = jwtService.generateToken(usuario);
        String refreshToken = jwtService.generateRefreshToken(usuario);

        return new TokenResponseDTO(accessToken, refreshToken, expiresInSeconds());
    }

    /**
     * Revoca la sesión marcando el identificador del token como inválido en Redis hasta su expiración,
     * para que no pueda reutilizarse aunque su firma siga vigente. La revocación es best-effort: si
     * Redis no responde, la expiración propia del token sigue siendo el límite duro de validez.
     *
     * @param token JWT de acceso del cual se extraen identificador, vencimiento y correo del titular
     * @param ipOrigen dirección IP desde donde se cierra la sesión, usada solo para auditoría y registro
     */
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

    // Escribe LOGIN_OK/LOGIN_FAIL/LOGOUT en bitacora_auditoria.
    // LOGIN_* van a 'sesiones'; CORREO_VERIFICADO a 'usuarios'.
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
            // Bitácora best-effort: si falla no rompe el login; el evento queda en el log.
            log.error("No se pudo registrar evento de auditoría: tipo={} usuarioId={} ip={}",
                    tipoOperacion, usuarioId, ipOrigen, e);
        }
    }

    /**
     * Emite un JWT de acceso nuevo a partir de un token de refresco vigente, para extender la sesión
     * sin volver a pedir las credenciales al titular.
     *
     * @param refreshToken token de refresco previamente emitido por {@link #login} y aún vigente
     * @return par de tokens con el JWT de acceso renovado, el mismo refresco recibido y su vigencia en segundos
     * @throws RefreshTokenInvalidoException si el refresco no es válido, expiró o su correo ya no existe
     */
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
