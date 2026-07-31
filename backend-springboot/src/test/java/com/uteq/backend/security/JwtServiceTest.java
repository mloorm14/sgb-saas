package com.uteq.backend.security;

import com.uteq.backend.entity.EstadoUsuario;
import com.uteq.backend.entity.Rol;
import com.uteq.backend.entity.Usuario;
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

    private Usuario usuarioConRoles(String... nombresRoles) {
        EstadoUsuario activo = new EstadoUsuario();
        activo.setId(1);
        activo.setNombre("ACTIVO");

        Set<Rol> roles = new java.util.HashSet<>();
        int idSecuencia = 1;
        for (String nombreRol : nombresRoles) {
            Rol rol = new Rol();
            rol.setId(idSecuencia++);
            rol.setNombre(nombreRol);
            roles.add(rol);
        }

        return Usuario.builder()
                .id(42L)
                .nombre("Usuario")
                .apellido("De Prueba")
                .correo("jwt-test@uteq.edu.ec")
                .passwordHash("hash")
                .estado(activo)
                .correoVerificado(true)
                .roles(roles)
                .fechaRegistro(Instant.now())
                .actualizadoEn(Instant.now())
                .build();
    }

    private Claims parsearClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    @Test
    void generateToken_contieneClaimsCorrectos() {
        Usuario usuario = usuarioConRoles("LECTOR");

        String token = jwtService.generateToken(usuario);
        Claims claims = parsearClaims(token);

        assertEquals("42", claims.getSubject());
        assertEquals("jwt-test@uteq.edu.ec", claims.get("correo", String.class));
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
        Usuario usuario = usuarioConRoles("LECTOR");

        String refreshToken = jwtService.generateRefreshToken(usuario);
        Claims claims = parsearClaims(refreshToken);

        long diferenciaMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertTrue(Math.abs(diferenciaMs - REFRESH_EXPIRATION_MS) <= 1000,
                "exp - iat debe ser ~refreshExpirationMs, fue " + diferenciaMs);
    }

    // JERARQUIA_ROLES (ADMIN > GERENTE > BIBLIOTECARIO > LECTOR): el claim
    // singular "rol" debe elegir el de mayor privilegio, sin importar el
    // orden de iteracion del Set de roles del usuario.
    @Test
    void generateToken_conVariosRoles_claimRolEligeElDeMayorPrivilegio() {
        Usuario usuario = usuarioConRoles("LECTOR", "BIBLIOTECARIO");

        Claims claims = parsearClaims(jwtService.generateToken(usuario));

        assertEquals("BIBLIOTECARIO", claims.get("rol", String.class));
    }

    @Test
    void validateToken_conTokenValido_devuelveTrue() {
        String token = jwtService.generateToken(usuarioConRoles("LECTOR"));

        assertTrue(jwtService.validateToken(token));
    }

    @Test
    void validateToken_conTokenExpirado_devuelveFalse() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date haceDosHoras = new Date(System.currentTimeMillis() - 7_200_000L);
        Date haceUnaHora = new Date(System.currentTimeMillis() - 3_600_000L);

        String tokenExpirado = Jwts.builder()
                .subject("42")
                .issuedAt(haceDosHoras)
                .expiration(haceUnaHora)
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertFalse(jwtService.validateToken(tokenExpirado));
    }

    @Test
    void validateToken_conFirmaAlterada_devuelveFalse() {
        String token = jwtService.generateToken(usuarioConRoles("LECTOR"));
        // Altera el ultimo caracter de la firma (tercer segmento del JWT)
        // para simular un token manipulado sin volver a firmarlo.
        String tokenAlterado = token.substring(0, token.length() - 1)
                + (token.charAt(token.length() - 1) == 'a' ? 'b' : 'a');

        assertFalse(jwtService.validateToken(tokenAlterado));
    }

    @Test
    void validateToken_firmadoConOtraClave_devuelveFalse() {
        SecretKey otraClave = Keys.hmacShaKeyFor(
                "otra-clave-completamente-distinta-256-bits-minimo".getBytes(StandardCharsets.UTF_8));

        String tokenConOtraFirma = Jwts.builder()
                .subject("42")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(otraClave, Jwts.SIG.HS256)
                .compact();

        assertFalse(jwtService.validateToken(tokenConOtraFirma));
    }

    @Test
    void extractCorreo_extractJti_extractExpiration_devuelvenValoresDelToken() {
        Usuario usuario = usuarioConRoles("LECTOR");
        String token = jwtService.generateToken(usuario);
        Claims claimsEsperados = parsearClaims(token);

        assertEquals("jwt-test@uteq.edu.ec", jwtService.extractCorreo(token));
        assertEquals(claimsEsperados.getId(), jwtService.extractJti(token));
        assertEquals(claimsEsperados.getExpiration(), jwtService.extractExpiration(token));
    }
}
