package com.uteq.backend.security;

import com.uteq.backend.entity.Rol;
import com.uteq.backend.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    // Orden de mayor a menor privilegio, usado solo para elegir el claim
    // "rol" (singular) de retrocompatibilidad -- ver comentario en
    // buildToken(). No afecta autorización real: JwtAuthFilter reconstruye
    // las authorities en cada request vía UserDetailsServiceImpl (consulta
    // fresca a la BD), nunca lee claims de rol del propio JWT.
    private static final List<String> JERARQUIA_ROLES = List.of("ADMIN", "GERENTE", "BIBLIOTECARIO", "LECTOR");

    @Value("${security.jwt.secret}")
    private String secret;

    @Value("${security.jwt.expiration-ms}")
    private long expirationMs;

    @Value("${security.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    public long getExpirationMs() {
        return expirationMs;
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Usuario usuario) {
        return buildToken(usuario, expirationMs);
    }

    public String generateRefreshToken(Usuario usuario) {
        return buildToken(usuario, refreshExpirationMs);
    }

    private String buildToken(Usuario usuario, long ttlMs) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + ttlMs);

        List<String> roles = usuario.getRoles().stream()
                .map(Rol::getNombre)
                .toList();

        return Jwts.builder()
                .subject(String.valueOf(usuario.getId()))
                .claim("correo", usuario.getCorreo())
                // Claim real para autorización: arreglo completo de roles.
                .claim("roles", roles)
                // DECISIÓN TEMPORAL DE RETROCOMPATIBILIDAD (TAREA 4.1): se
                // conserva el claim singular "rol" (el de mayor privilegio
                // según JERARQUIA_ROLES) solo por si algún código frontend
                // ya existente lo lee. Eliminar este claim cuando el
                // frontend (Panama) migre a leer "roles" (array) en su
                // lugar -- no agregar más lógica que dependa de "rol"
                // mientras tanto.
                .claim("rol", rolPrincipal(roles))
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    private String rolPrincipal(List<String> roles) {
        return roles.stream()
                .min(Comparator.comparingInt(nombre -> {
                    int idx = JERARQUIA_ROLES.indexOf(nombre);
                    return idx == -1 ? Integer.MAX_VALUE : idx;
                }))
                .orElse(null);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String extractCorreo(String token) {
        return extractClaims(token).get("correo", String.class);
    }

    public String extractJti(String token) {
        return extractClaims(token).getId();
    }

    public Date extractExpiration(String token) {
        return extractClaims(token).getExpiration();
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
