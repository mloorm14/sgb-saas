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
    private static final String ESTADO_INICIAL = "ACTIVO";
    private static final String TABLA_USUARIOS = "usuarios";

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final EstadoUsuarioRepository estadoUsuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RedisTemplate<String, String> redisTemplate;
    private final LoginRateLimiter loginRateLimiter;
    private final BitacoraAuditoriaRepository bitacoraAuditoriaRepository;

    public UsuarioResponseDTO registrar(RegistroRequestDTO dto) {
        usuarioRepository.findByCorreo(dto.correo()).ifPresent(usuario -> {
            throw new CorreoYaRegistradoException("El correo ya está registrado: " + dto.correo());
        });

        Rol rolLector = rolRepository.findByNombre(ROL_POR_DEFECTO)
                .orElseThrow(() -> new IllegalStateException("Catalogo roles sin fila '" + ROL_POR_DEFECTO + "'"));
        EstadoUsuario estadoActivo = estadoUsuarioRepository.findByNombre(ESTADO_INICIAL)
                .orElseThrow(() -> new IllegalStateException("Catalogo estados_usuario sin fila '" + ESTADO_INICIAL + "'"));

        Instant ahora = Instant.now();
        Set<Rol> roles = new HashSet<>();
        roles.add(rolLector);

        Usuario usuario = Usuario.builder()
                .nombre(dto.nombre())
                .apellido(dto.apellido())
                .correo(dto.correo())
                .passwordHash(passwordEncoder.encode(dto.password()))
                .estado(estadoActivo)
                .correoVerificado(false)
                .roles(roles)
                .fechaRegistro(ahora)
                .actualizadoEn(ahora)
                .build();

        Usuario guardado = usuarioRepository.save(usuario);

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
            redisTemplate.opsForValue().set("blacklist:" + jti, "revoked", ttl, TimeUnit.SECONDS);
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
    private void registrarAuditoria(Long usuarioId, String tipoOperacion, Long registroId,
                                     String detalles, String ipOrigen) {
        BitacoraAuditoria evento = BitacoraAuditoria.builder()
                .usuarioId(usuarioId)
                .tipoOperacion(tipoOperacion)
                .tablaAfectada(TABLA_USUARIOS)
                .registroId(registroId)
                .detalles(detalles)
                .ipOrigen(ipOrigen)
                .fechaHora(OffsetDateTime.now())
                .build();
        bitacoraAuditoriaRepository.save(evento);
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
