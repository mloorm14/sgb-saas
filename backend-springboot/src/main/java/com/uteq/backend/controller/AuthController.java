package com.uteq.backend.controller;

import com.uteq.backend.dto.CodigoVerificacionRequestDTO;
import com.uteq.backend.dto.LoginRequestDTO;
import com.uteq.backend.dto.ReenviarCodigoRequestDTO;
import com.uteq.backend.dto.RegistroRequestDTO;
import com.uteq.backend.dto.TokenResponseDTO;
import com.uteq.backend.dto.UsuarioResponseDTO;
import com.uteq.backend.security.JwtService;
import com.uteq.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String REFRESH_COOKIE_NAME = "refreshToken";

    private final AuthService authService;
    private final JwtService jwtService;

    @PostMapping("/registro")
    public ResponseEntity<UsuarioResponseDTO> registro(@Valid @RequestBody RegistroRequestDTO dto) {
        UsuarioResponseDTO usuario = authService.registrar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(usuario);
    }

    // Sin JWT: el usuario aún no puede loguearse (PENDIENTE_VERIFICACION).
    // Regenera el código de 6 dígitos en Redis cuando el anterior expiró
    // (TTL 10 min) y el usuario quedó bloqueado sin intervención de ADMIN.
    @PostMapping("/reenviar-codigo")
    public ResponseEntity<Void> reenviarCodigo(@Valid @RequestBody ReenviarCodigoRequestDTO dto) {
        authService.reenviarCodigo(dto.correo());
        return ResponseEntity.noContent().build();
    }

    // Módulo 9.5: sin @PreAuthorize / sin JWT -- el usuario recién
    // registrado todavía no puede loguearse (ver AuthService.ESTADO_INICIAL),
    // así que no hay token que exigir aquí. La identidad se prueba con el
    // código de un solo uso, no con autenticación.
    @PostMapping("/verificar-correo")
    public ResponseEntity<UsuarioResponseDTO> verificarCorreo(
            @Valid @RequestBody CodigoVerificacionRequestDTO dto, HttpServletRequest request) {
        UsuarioResponseDTO usuario = authService.verificarCorreo(dto.correo(), dto.codigo(), obtenerIpOrigen(request));
        return ResponseEntity.ok(usuario);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto, HttpServletRequest request) {
        TokenResponseDTO tokens = authService.login(dto, obtenerIpOrigen(request));
        ResponseCookie cookie = buildRefreshCookie(tokens.refreshToken(), jwtService.getRefreshExpirationMs());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(tokens);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader, HttpServletRequest request) {
        String token = authHeader.substring(BEARER_PREFIX.length());
        authService.logout(token, obtenerIpOrigen(request));
        ResponseCookie cookieLimpia = buildRefreshCookie("", 0);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieLimpia.toString())
                .build();
    }

    // Bloque C.2 (OWASP A07/A09): IP real del cliente, usada para el rate
    // limiter de login y el logging de eventos de autenticación. Se lee
    // directo de request.getRemoteAddr() -- NO de X-Forwarded-For, que
    // cualquier cliente puede falsificar libremente; este entorno no tiene
    // un proxy reverso de confianza delante que lo sobreescriba con un
    // valor fiable. Si en el futuro se agrega uno, este método es el único
    // punto a ajustar (validar contra una lista de proxies de confianza
    // antes de usarlo, no confiar en el header a ciegas).
    private String obtenerIpOrigen(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    // El refresh token viaja SOLO en la cookie HttpOnly (nunca en el body):
    // así JS del frontend no puede leerlo ni reenviarlo manualmente, que es
    // precisamente el punto de HttpOnly. required=false + validación manual
    // (en vez de required=true) para que la ausencia de cookie caiga en el
    // handler ya existente de IllegalArgumentException (400, RFC 7807) en
    // vez de en el mecanismo de error por defecto de Spring MVC.
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponseDTO> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshTokenCookie) {
        if (refreshTokenCookie == null || refreshTokenCookie.isBlank()) {
            throw new IllegalArgumentException("Falta la cookie " + REFRESH_COOKIE_NAME);
        }

        TokenResponseDTO tokens = authService.refresh(refreshTokenCookie);
        ResponseCookie cookie = buildRefreshCookie(tokens.refreshToken(), jwtService.getRefreshExpirationMs());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(tokens);
    }

    // path="/api/auth" limita el envío de la cookie a los endpoints de
    // autenticación (nunca viaja en llamadas a /api/v1/**). Ver
    // docs/adr/adr-007-cookies-jwt.md para el resto de decisiones de diseño
    // (por qué solo el refreshToken migra a cookie, no el accessToken).
    private ResponseCookie buildRefreshCookie(String valor, long maxAgeMs) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, valor)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/api/auth")
                .maxAge(Duration.ofMillis(maxAgeMs))
                .build();
    }
}
