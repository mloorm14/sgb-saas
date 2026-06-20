package com.uteq.backend.service;

import com.uteq.backend.dto.LoginRequestDTO;
import com.uteq.backend.dto.RegistroRequestDTO;
import com.uteq.backend.dto.TokenResponseDTO;
import com.uteq.backend.dto.UsuarioResponseDTO;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.UsuarioRepository;
import com.uteq.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RedisTemplate<String, String> redisTemplate;

    public UsuarioResponseDTO registrar(RegistroRequestDTO dto) {
        usuarioRepository.findByCorreo(dto.correo()).ifPresent(usuario -> {
            throw new CorreoYaRegistradoException("El correo ya está registrado: " + dto.correo());
        });

        Instant ahora = Instant.now();
        Usuario usuario = Usuario.builder()
                .nombre(dto.nombre())
                .correo(dto.correo())
                .passwordHash(passwordEncoder.encode(dto.password()))
                .rol("ROLE_LECTOR")
                .activo(true)
                .creadoEn(ahora)
                .actualizadoEn(ahora)
                .build();

        Usuario guardado = usuarioRepository.save(usuario);

        return mapToUsuarioResponseDTO(guardado);
    }

    public TokenResponseDTO login(LoginRequestDTO dto) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.correo(), dto.password())
        );

        Usuario usuario = usuarioRepository.findByCorreo(dto.correo())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + dto.correo()));

        String accessToken = jwtService.generateToken(usuario);
        String refreshToken = jwtService.generateRefreshToken(usuario);

        return new TokenResponseDTO(accessToken, refreshToken, expiresInSeconds());
    }

    public void logout(String token) {
        String jti = jwtService.extractJti(token);
        Date expiration = jwtService.extractExpiration(token);

        long ttl = (expiration.getTime() - System.currentTimeMillis()) / 1000;

        if (ttl > 0) {
            redisTemplate.opsForValue().set("blacklist:" + jti, "revoked", ttl, TimeUnit.SECONDS);
        }
    }

    public TokenResponseDTO refresh(String refreshToken) {
        if (!jwtService.validateToken(refreshToken)) {
            throw new RuntimeException("Refresh token inválido");
        }

        String correo = jwtService.extractCorreo(refreshToken);
        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + correo));

        String nuevoAccessToken = jwtService.generateToken(usuario);

        return new TokenResponseDTO(nuevoAccessToken, refreshToken, expiresInSeconds());
    }

    private long expiresInSeconds() {
        return jwtService.getExpirationMs() / 1000;
    }

    private UsuarioResponseDTO mapToUsuarioResponseDTO(Usuario usuario) {
        return new UsuarioResponseDTO(usuario.getId(), usuario.getNombre(), usuario.getCorreo(), usuario.getRol());
    }
}
