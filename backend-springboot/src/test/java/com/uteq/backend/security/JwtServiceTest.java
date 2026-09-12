package com.uteq.backend.security;

import com.uteq.backend.entity.StatusUser;
import com.uteq.backend.entity.Role;
import com.uteq.backend.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Bloque C.4: JwtService es logica pura (firma/parseo de JWT), sin
// dependencias de Spring context -- se instancia directamente y se
// inyectan los @Value via ReflectionTestUtils, igual que production los
// resuelve desde application.yml. Sin esto, JwtService quedaba con 4.8%
// de cobertura real porque todos los tests que lo usan (AuthServiceTest,
// LibroControllerSecurityTest) lo mockean.
class JwtServiceTest {

    // Mismo valor por defecto que application.yml (security.jwt.secret) --
    // ya validado como suficiente para HS256 (>=256 bits) en produccion.
    private static final String SECRET = "CAMBIAR_EN_PRODUCCION_MIN_256_BITS";
    private static final long EXPIRATION_MS = 3_600_000L;
    private static final long REFRESH_EXPIRATION_MS = 604_800_000L;

    private JwtService jwtService;

    @BeforeEach
    void construirJwtService() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", SECRET);
        ReflectionTestUtils.setField(jwtService, "expirationMs", EXPIRATION_MS);
        ReflectionTestUtils.setField(jwtService, "refreshExpirationMs", REFRESH_EXPIRATION_MS);
    }

    private User userWithRoles(String... nombresRoles) {
        StatusUser active = new StatusUser();
        active.setId(1);
        active.setName("ACTIVO");

        Set<Role> roles = new java.util.HashSet<>();
        int idSecuencia = 1;
        for (String nameRole : nombresRoles) {
            Role role = new Role();
            role.setId(idSecuencia++);
            role.setName(nameRole);
            roles.add(role);
        }

        return User.builder()
                .id(42L)
                .name("Usuario")
                .lastName("De Prueba")
                .email("jwt-test@correo.com")
                .passwordHash("hash")
                .status(active)
                .emailVerified(true)
                .roles(roles)
                .dateRegistration(Instant.now())
                .updated(Instant.now())
                .build();
    }

    private Claims parsearClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    @Test
    void generateToken_contieneClaimsCorrectos() {
        User user = userWithRoles("LECTOR");

        String token = jwtService.generateToken(user);
        Claims claims = parsearClaims(token);

        assertEquals("42", claims.getSubject());
        assertEquals("jwt-test@correo.com", claims.get("correo", String.class));
        assertEquals(List.of("LECTOR"), claims.get("roles", List.class));
        assertEquals("LECTOR", claims.get("rol", String.class));
        assertNotNull(claims.getId());
        assertTrue(claims.getId().matches("^[0-9a-f-]{36}$"), "jti debe tener formato UUID");
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());

        long diferenciaMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertTrue(Math.abs(diferenciaMs - EXPIRATION_MS) <= 1000,
                "exp - iat debe ser ~expirationMs, fue " + diferenciaMs);
    }

    @Test
    void generateRefreshToken_usaRefreshExpirationMs() {
        User user = userWithRoles("LECTOR");

        String refreshToken = jwtService.generateRefreshToken(user);
        Claims claims = parsearClaims(refreshToken);

        long diferenciaMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertTrue(Math.abs(diferenciaMs - REFRESH_EXPIRATION_MS) <= 1000,
                "exp - iat debe ser ~refreshExpirationMs, fue " + diferenciaMs);
    }

    // JERARQUIA_ROLES (ADMIN > GERENTE > BIBLIOTECARIO > LECTOR): el claim
    // singular "rol" debe elegir el de mayor privilegio, sin importar el
    // orden de iteracion del Set de roles del usuario.
    @Test
    void generateToken_withVariosRoles_claimRoleEligeMayorPrivilegio() {
        User user = userWithRoles("LECTOR", "BIBLIOTECARIO");

        Claims claims = parsearClaims(jwtService.generateToken(user));

        assertEquals("BIBLIOTECARIO", claims.get("rol", String.class));
    }

    @Test
    void validateToken_withTokenValid_devuelveTrue() {
        String token = jwtService.generateToken(userWithRoles("LECTOR"));

        assertTrue(jwtService.validateToken(token));
    }

    @Test
    void validateToken_withTokenExpirado_devuelveFalse() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date haceDosTimes = new Date(System.currentTimeMillis() - 7_200_000L);
        Date haceUnaTime = new Date(System.currentTimeMillis() - 3_600_000L);

        String tokenExpirado = Jwts.builder()
                .subject("42")
                .issuedAt(haceDosTimes)
                .expiration(haceUnaTime)
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertFalse(jwtService.validateToken(tokenExpirado));
    }

    // Test originalmente flaky (verificado: falla ~1/5 corridas): alteraba el
    // ÚLTIMO carácter del token, que es el carácter de padding del tercer
    // segmento (una firma HMAC-SHA256 de 32 bytes ocupa 43 chars base64url
    // y el char 43 solo aporta 4 bits de firma + 2 bits de relleno sin
    // datos, dropeados al decodificar). Según qué nibble quedara de
    // relleno, el token alterado decodificaba a LOS MISMOS bytes de firma
    // y validateToken() devolvía true -- "expected: false but was: true".
    // Un carácter del MEDIO de la firma siempre altera 6 bits con datos
    // reales (no hay padding en chars intermedios), así que el fallo del
    // chequeo de firma es determinista.
    @Test
    void validateToken_withSignatureAlterada_devuelveFalse() {
        String token = jwtService.generateToken(userWithRoles("LECTOR"));
        // Altera un carácter central del tercer segmento (firma) para
        // simular un token manipulado sin volver a firmarlo.
        String[] partes = token.split("\\.");
        String signature = partes[2];
        int mitad = signature.length() / 2;
        char original = signature.charAt(mitad);
        String signatureAlterada = signature.substring(0, mitad)
                + (original == 'a' ? 'b' : 'a')
                + signature.substring(mitad + 1);
        String tokenAlterado = partes[0] + "." + partes[1] + "." + signatureAlterada;

        assertFalse(jwtService.validateToken(tokenAlterado));
    }

    @Test
    void validateToken_firmadoWithOtraKey_devuelveFalse() {
        SecretKey otraKey = Keys.hmacShaKeyFor(
                "otra-clave-completamente-distinta-256-bits-minimo".getBytes(StandardCharsets.UTF_8));

        String tokenWithOtraSignature = Jwts.builder()
                .subject("42")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(otraKey, Jwts.SIG.HS256)
                .compact();

        assertFalse(jwtService.validateToken(tokenWithOtraSignature));
    }

    @Test
    void extractEmail_extractJti_extractExpiration_devuelvenValuesToken() {
        User user = userWithRoles("LECTOR");
        String token = jwtService.generateToken(user);
        Claims claimsEsperados = parsearClaims(token);

        assertEquals("jwt-test@correo.com", jwtService.extractEmail(token));
        assertEquals(claimsEsperados.getId(), jwtService.extractJti(token));
        assertEquals(claimsEsperados.getExpiration(), jwtService.extractExpiration(token));
    }
}
