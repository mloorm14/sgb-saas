package com.uteq.backend.security;

import com.uteq.backend.entity.Role;
import com.uteq.backend.entity.User;
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

    /**
     * Retrieves expiration ms.
     *
     * @return generated identifier of the affected record
     */

    public long getExpirationMs() {
        return expirationMs;
    }

    /**
     * Retrieves refresh expiration ms.
     *
     * @return generated identifier of the affected record
     */

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates JWT.
     *
     * @param user user used to scope this JWT
     * @return resulting text payload
     */

    public String generateToken(User user) {
        return buildToken(user, expirationMs);
    }

    /**
     * Generates JWT.
     *
     * @param user user used to scope this JWT
     * @return resulting text payload
     */

    public String generateRefreshToken(User user) {
        return buildToken(user, refreshExpirationMs);
    }

    private String buildToken(User user, long ttlMs) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + ttlMs);

        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .toList();

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("correo", user.getEmail())
                // Claim real para autorización: arreglo completo de roles.
                .claim("roles", roles)
                // DECISIÓN TEMPORAL DE RETROCOMPATIBILIDAD (TAREA 4.1): se
                // conserva el claim singular "rol" (el de mayor privilegio
                // según JERARQUIA_ROLES) solo por si algún código frontend
                // ya existente lo lee. Eliminar este claim cuando el
                // frontend (Panama) migre a leer "roles" (array) en su
                // lugar -- no agregar más lógica que dependa de "rol"
                // mientras tanto.
                .claim("rol", rolePrincipal(roles))
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    private String rolePrincipal(List<String> roles) {
        return roles.stream()
                .min(Comparator.comparingInt(name -> {
                    int idx = JERARQUIA_ROLES.indexOf(name);
                    return idx == -1 ? Integer.MAX_VALUE : idx;
                }))
                .orElse(null);
    }

    /**
     * Validates JWT.
     *
     * @param token text value used to scope this JWT
     * @return true when the check succeeds
     */

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

    /**
     * Handles extract email address.
     *
     * @param token text value used to scope this extract email address
     * @return resulting text payload
     */

    public String extractEmail(String token) {
        return extractClaims(token).get("correo", String.class);
    }

    /**
     * Handles extract Jti.
     *
     * @param token text value used to scope this extract Jti
     * @return resulting text payload
     */

    public String extractJti(String token) {
        return extractClaims(token).getId();
    }

    /**
     * Handles extract Expiration.
     *
     * @param token text value used to scope this extract Expiration
     * @return Date reflecting the state after the operation
     */

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
