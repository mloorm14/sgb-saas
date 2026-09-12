package com.uteq.backend.service;

import com.uteq.backend.dto.LoginRequestDTO;
import com.uteq.backend.dto.RegistrationRequestDTO;
import com.uteq.backend.dto.TokenResponseDTO;
import com.uteq.backend.dto.UserResponseDTO;
import com.uteq.backend.entity.AuditLogAudit;
import com.uteq.backend.entity.StatusUser;
import com.uteq.backend.entity.Role;
import com.uteq.backend.entity.User;
import com.uteq.backend.repository.AuditLogAuditRepository;
import com.uteq.backend.repository.StatusUserRepository;
import com.uteq.backend.repository.RoleRepository;
import com.uteq.backend.repository.UserRepository;
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

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StatusUserRepository statusUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RedisTemplate<String, String> redisTemplate;
    private final LoginRateLimiter loginRateLimiter;
    private final AuditLogAuditRepository auditLogAuditRepository;
    private final VerificationEmailService verificationEmailService;
    private final ConfigurationSystemService configurationSystemService;
    private final EmailService emailService;

    /**
     * Crea una cuenta nueva con rol LECTOR en estado PENDIENTE_VERIFICACION para permitir el registro
     * autónomo y dejarla lista para la confirmación por correo.
     * Valida que el correo no esté duplicado ni pertenezca a un dominio restringido, cifra la
     * contraseña y dispara el envío del código de verificación (ver {@link #verifyEmail}).
     *
     * @param dto solicitud con nombre, apellido, correo de acceso y contraseña en claro sin cifrar
     * @return vista resumida del usuario persistido con identificador, nombre, correo y roles asignados
     * @throws EmailYaRegistradoException si ya existe un usuario con el correo solicitado
     * @throws EmailDomainNotAllowedException si el dominio del correo no figura entre los permitidos
     * @throws IllegalStateException si faltan las filas de catálogo del rol LECTOR o del estado inicial
     */
    public UserResponseDTO register(RegistrationRequestDTO dto) {
        userRepository.findByEmail(dto.email()).ifPresent(user -> {
            throw new EmailYaRegistradoException("El correo ya está registrado: " + dto.email());
        });

        validateDomainEmail(dto.email());

        Role roleReader = roleRepository.findByName(ROL_POR_DEFECTO)
                .orElseThrow(() -> new IllegalStateException("Catalogo roles sin fila '" + ROL_POR_DEFECTO + "'"));
        StatusUser statusPendingVerification = statusUserRepository.findByName(ESTADO_INICIAL)
                .orElseThrow(() -> new IllegalStateException("Catalogo estados_usuario sin fila '" + ESTADO_INICIAL + "'"));

        Instant ahora = Instant.now();
        Set<Role> roles = new HashSet<>();
        roles.add(roleReader);

        User user = User.builder()
                .name(dto.name())
                .lastName(dto.lastName())
                .email(dto.email())
                .passwordHash(passwordEncoder.encode(dto.password()))
                .status(statusPendingVerification)
                .emailVerified(false)
                .roles(roles)
                .dateRegistration(ahora)
                .updated(ahora)
                .build();

        User guardado = userRepository.save(user);

        // Queda PENDIENTE_VERIFICACION hasta confirmar el código vía verificarCorreo().
        verificationEmailService.generateYSendCode(guardado);

        return mapToUserResponseDTO(guardado);
    }

    private void validateDomainEmail(String email) {
        try {
            String domainsAlloweds = configurationSystemService.getValue("correo_dominios_permitidos");
            if (domainsAlloweds == null || domainsAlloweds.isBlank()) return;
            String domain = email.substring(email.lastIndexOf('@') + 1).toLowerCase();
            for (String allowed : domainsAlloweds.split(",")) {
                if (domain.equals(allowed.trim().toLowerCase())) return;
            }
            throw new EmailDomainNotAllowedException(
                    "Solo se permiten registros con dominio: " + domainsAlloweds);
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
     * @param email dirección asociada a la cuenta pendiente de verificación
     * @throws jakarta.persistence.EntityNotFoundException si no existe ningún usuario con ese correo
     * @throws IllegalArgumentException si el correo ya está verificado o la cuenta no requiere verificación
     */
    public void resendCode(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(USUARIO_NO_ENCONTRADO + email));
        if (user.isEmailVerified() || !ESTADO_INICIAL.equals(user.getStatus().getName())) {
            throw new IllegalArgumentException("El correo ya está verificado o la cuenta no requiere verificación.");
        }
        verificationEmailService.generateYSendCode(user);
    }

    // ── POST /api/auth/solicitar-reset ──
    // Recuperación en 2 pasos: código de 6 dígitos con TTL 10 min en Redis, enviado por correo.
    /**
     * Inicia la recuperación de acceso en dos pasos para que el titular pueda definir una contraseña
     * nueva sin estar autenticado. Genera un código aleatorio de 6 dígitos con vigencia de 10 minutos
     * en Redis y lo envía por correo como mecanismo best-effort (ver {@link #resetPassword}).
     *
     * @param email dirección de la cuenta que solicita la recuperación
     * @throws jakarta.persistence.EntityNotFoundException si no existe ningún usuario con ese correo
     * @throws ServiceTemporalmenteNotAvailableException si Redis no acepta el guardado del código
     */
    public void requestReset(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(USUARIO_NO_ENCONTRADO + email));
        String key = "reset-codigo:" + email;
        String code = String.format("%06d", new java.security.SecureRandom().nextInt(1_000_000));
        try {
            redisTemplate.opsForValue().set(key, code, java.time.Duration.ofMinutes(10));
        } catch (org.springframework.dao.DataAccessException e) {
            throw new ServiceTemporalmenteNotAvailableException("Servicio de reset no disponible");
        }
        String body = "<p>Hola " + user.getName() + ",</p>"
                + "<p>Tu código para recuperar la cuenta es: <b>" + code + "</b></p>"
                + "<p>Vence en 10 minutos.</p>";
        // Envío best-effort: si falla, el código queda en Redis y el usuario puede reintentar.
        boolean enviado = emailService.sendEmail(email, "Recuperar cuenta - SGB-SaaS", body);
        if (!enviado) {
            log.warn("No se pudo enviar correo de recuperacion a {} (codigo en Redis)", email);
        }
        log.info("Código de recuperacion generado para {}: {}", email, code);
    }

    // ── POST /api/auth/reset ────────────────────────
    /**
     * Reemplaza la contraseña de una cuenta usando el código de recuperación de un solo uso, para
     * devolverle el acceso al titular tras validar su identidad sin JWT. Consume el código en Redis
     * cuando el cambio se persiste.
     *
     * @param email dirección de la cuenta a recuperar
     * @param code código de 6 dígitos previamente generado por {@link #requestReset} y aún vigente en Redis
     * @param freshPassword contraseña en claro sin cifrar que reemplazará a la anterior
     * @throws CodeVerificationInvalidException si el código no coincide, expiró o Redis no responde a la lectura
     * @throws jakarta.persistence.EntityNotFoundException si no existe ningún usuario con ese correo
     */
    public void resetPassword(String email, String code, String freshPassword) {
        String key = "reset-codigo:" + email;
        String almacenado;
        try {
            almacenado = redisTemplate.opsForValue().get(key);
        } catch (org.springframework.dao.DataAccessException e) {
            throw new CodeVerificationInvalidException("Servicio no disponible");
        }
        if (almacenado == null || !almacenado.equals(code)) {
            throw new CodeVerificationInvalidException("Código inválido o expirado");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Usuario no encontrado"));
        user.setPasswordHash(passwordEncoder.encode(freshPassword));
        user.setUpdated(Instant.now());
        userRepository.save(user);
          try { redisTemplate.delete(key); } catch (Exception ignored) {
              // best-effort: si Redis cae, el reseteo ya se completó en BD
          }
        log.info("Password reseteado para {}", email);
    }

    // ── POST /api/auth/verificar-correo ──
    // Sin JWT: la identidad se comprueba con el código de un solo uso.
    /**
     * Confirma el código de verificación de un solo uso y activa la cuenta, para desbloquear el inicio
     * de sesión que permanece restringido mientras el correo sigue pendiente. Registra el evento en la
     * bitácora de auditoría con la IP de origen.
     *
     * @param email dirección pendiente de confirmación
     * @param code código de un solo uso previamente enviado al correo del titular
     * @param ipSource dirección IP desde donde se confirma, usada solo para auditoría y registro
     * @return vista resumida del usuario ya activado con identificador, nombre, correo y roles
     * @throws IllegalArgumentException si no existe ningún usuario con ese correo
     * @throws IllegalStateException si falta la fila de catálogo del estado ACTIVO
     */
    public UserResponseDTO verifyEmail(String email, String code, String ipSource) {
        verificationEmailService.validate(email, code);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException(USUARIO_NO_ENCONTRADO + email));
        StatusUser statusActive = statusUserRepository.findByName(ESTADO_VERIFICADO)
                .orElseThrow(() -> new IllegalStateException("Catalogo estados_usuario sin fila '" + ESTADO_VERIFICADO + "'"));

        user.setStatus(statusActive);
        user.setEmailVerified(true);
        user.setUpdated(Instant.now());
        User guardado = userRepository.save(user);

        log.info("Correo verificado: correo={} ip={}", email, ipSource);
        registerAudit(guardado.getId(), "CORREO_VERIFICADO", guardado.getId(),
                "Correo verificado para: " + email, ipSource);

        return mapToUserResponseDTO(guardado);
    }

    /**
     * Autentica las credenciales y emite los tokens de sesión para mantener conectado al titular.
     * Aplica el límite de intentos por correo e IP antes de autenticar, reinicia el contador tras el
     * éxito y deja traza de cada resultado en la bitácora de auditoría.
     *
     * @param dto credenciales de acceso con correo y contraseña en claro sin cifrar
     * @param ipSource dirección IP desde donde se intenta el acceso, usada para el límite de intentos y auditoría
     * @return par de tokens con el JWT de acceso, el token de refresco y su vigencia en segundos
     * @throws LoginRateLimitExceededException si la combinación de correo e IP agotó los intentos permitidos
     * @throws org.springframework.security.authentication.BadCredentialsException si la contraseña o el usuario no son válidos
     * @throws RuntimeException si la autenticación prospera pero el usuario ya no existe en la base
     */
    public TokenResponseDTO login(LoginRequestDTO dto, String ipSource) {
        // Verifica el rate limit ANTES de autenticar → 429 si se agotó.
        if (loginRateLimiter.estaBlocked(dto.email(), ipSource)) {
            long secondsRestantes = loginRateLimiter.secondsRestantes(dto.email(), ipSource);
            log.warn("Login bloqueado por rate limit: correo={} ip={} segundosRestantes={}",
                    dto.email(), ipSource, secondsRestantes);
            throw new LoginRateLimitExceededException(
                    "Demasiados intentos fallidos. Intente nuevamente en " + secondsRestantes + " segundos.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.email(), dto.password())
            );
        } catch (BadCredentialsException ex) {
            loginRateLimiter.registerFailure(dto.email(), ipSource);
            log.warn("Login fallido: correo={} ip={}", dto.email(), ipSource);
            registerAudit(null, "LOGIN_FAIL", null, "Login fallido para correo: " + dto.email(), ipSource);
            throw ex;
        }

        User user = userRepository.findByEmail(dto.email())
                .orElseThrow(() -> new RuntimeException(USUARIO_NO_ENCONTRADO + dto.email()));

        // Login exitoso: resetea el contador de fallos de esta combinación correo+IP.
        loginRateLimiter.resetear(dto.email(), ipSource);
        log.info("Login exitoso: sub={} correo={} ip={}", user.getId(), dto.email(), ipSource);
        registerAudit(user.getId(), "LOGIN_OK", user.getId(),
                "Login exitoso para correo: " + dto.email(), ipSource);

        String accessToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return new TokenResponseDTO(accessToken, refreshToken, expiresInSeconds());
    }

    /**
     * Revoca la sesión marcando el identificador del token como inválido en Redis hasta su expiración,
     * para que no pueda reutilizarse aunque su firma siga vigente. La revocación es best-effort: si
     * Redis no responde, la expiración propia del token sigue siendo el límite duro de validez.
     *
     * @param token JWT de acceso del cual se extraen identificador, vencimiento y correo del titular
     * @param ipSource dirección IP desde donde se cierra la sesión, usada solo para auditoría y registro
     */
    public void logout(String token, String ipSource) {
        String jti = jwtService.extractJti(token);
        Date expiration = jwtService.extractExpiration(token);
        String email = jwtService.extractEmail(token);

        long ttl = (expiration.getTime() - System.currentTimeMillis()) / 1000;

        if (ttl > 0) {
            try {
                redisTemplate.opsForValue().set("blacklist:" + jti, "revoked", ttl, TimeUnit.SECONDS);
            } catch (org.springframework.dao.DataAccessException e) {
                // Fail-open acotado: la revocación por blacklist es best-effort;
                // el exp del token sigue siendo el límite duro de validez.
                log.warn("Redis no disponible en logout (blacklist no actualizada): correo={} jti={}", email, jti, e);
            }
        }

        log.info("Logout: correo={} jti={} ip={}", email, jti, ipSource);
        registerAudit(null, "LOGOUT", null, "Logout para correo: " + email + " (jti=" + jti + ")", ipSource);
    }

    // Escribe LOGIN_OK/LOGIN_FAIL/LOGOUT en bitacora_auditoria.
    // LOGIN_* van a 'sesiones'; CORREO_VERIFICADO a 'usuarios'.
    private void registerAudit(Long userId, String typeOperacion, Long registrationId,
                                    String detalles, String ipSource) {
        boolean esSession = "LOGIN_OK".equals(typeOperacion)
                || "LOGIN_FAIL".equals(typeOperacion)
                || "LOGOUT".equals(typeOperacion);
        AuditLogAudit event = AuditLogAudit.builder()
                .userId(userId)
                .typeOperacion(typeOperacion)
                .tableAfectada(esSession ? TABLA_SESIONES : TABLA_USUARIOS)
                .registrationId(registrationId)
                .detalles(detalles)
                .ipSource(ipSource)
                .dateTime(OffsetDateTime.now())
                .build();
        try {
            auditLogAuditRepository.save(event);
        } catch (org.springframework.dao.DataAccessException e) {
            // Bitácora best-effort: si falla no rompe el login; el evento queda en el log.
            log.error("No se pudo registrar evento de auditoría: tipo={} usuarioId={} ip={}",
                    typeOperacion, userId, ipSource, e);
        }
    }

    /**
     * Emite un JWT de acceso nuevo a partir de un token de refresco vigente, para extender la sesión
     * sin volver a pedir las credenciales al titular.
     *
     * @param refreshToken token de refresco previamente emitido por {@link #login} y aún vigente
     * @return par de tokens con el JWT de acceso renovado, el mismo refresco recibido y su vigencia en segundos
     * @throws RefreshTokenInvalidException si el refresco no es válido, expiró o su correo ya no existe
     */
    public TokenResponseDTO refresh(String refreshToken) {
        if (!jwtService.validateToken(refreshToken)) {
            throw new RefreshTokenInvalidException("Refresh token inválido o expirado. Inicie sesión nuevamente.");
        }

        String email = jwtService.extractEmail(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RefreshTokenInvalidException("Refresh token inválido o expirado. Inicie sesión nuevamente."));

        String freshAccessToken = jwtService.generateToken(user);

        return new TokenResponseDTO(freshAccessToken, refreshToken, expiresInSeconds());
    }

    private long expiresInSeconds() {
        return jwtService.getExpirationMs() / 1000;
    }

    private UserResponseDTO mapToUserResponseDTO(User user) {
        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .toList();
        return new UserResponseDTO(user.getId(), user.getName(), user.getEmail(), roles);
    }
}
