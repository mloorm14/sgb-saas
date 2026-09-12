package com.uteq.backend.controller;

import com.uteq.backend.dto.CodeVerificationRequestDTO;
import com.uteq.backend.dto.LoginRequestDTO;
import com.uteq.backend.dto.ResendCodeRequestDTO;
import com.uteq.backend.dto.RegistrationRequestDTO;
import com.uteq.backend.dto.ResetPasswordRequestDTO;
import com.uteq.backend.dto.RequestResetRequestDTO;
import com.uteq.backend.dto.TokenResponseDTO;
import com.uteq.backend.dto.UserResponseDTO;
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
    /**
     * Handles record.
     *
     * @param dto record Request data transfer object used to scope this record
     * @return Response Entity&lt;Usuario Response DTO> reflecting the state after the operation
     */
    public ResponseEntity<UserResponseDTO> registration(@Valid @RequestBody RegistrationRequestDTO dto) {
        UserResponseDTO user = authService.register(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    // Sin JWT: el usuario aún no puede loguearse (PENDIENTE_VERIFICACION).
    // Regenera el código de 6 dígitos en Redis cuando el anterior expiró
    // (TTL 10 min) y el usuario quedó bloqueado sin intervención de ADMIN.
    @PostMapping("/reenviar-codigo")
    /**
     * Resends Response Entity&lt;Void>.
     *
     * @param dto Reenviar code Request data transfer object used to scope this Response Entity&lt;Void>
     * @return Response Entity&lt;Void> reflecting the state after the operation
     */
    public ResponseEntity<Void> resendCode(@Valid @RequestBody ResendCodeRequestDTO dto) {
        authService.resendCode(dto.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/solicitar-reset")
    /**
     * Requests Response Entity&lt;Void>.
     *
     * @param dto Solicitar Reset Request data transfer object used to scope this Response Entity&lt;Void>
     * @return Response Entity&lt;Void> reflecting the state after the operation
     */
    public ResponseEntity<Void> requestReset(@Valid @RequestBody RequestResetRequestDTO dto) {
        authService.requestReset(dto.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset")
    /**
     * Handles reset.
     *
     * @param dto Reset Password Request data transfer object used to scope this reset
     * @return Response Entity&lt;Void> reflecting the state after the operation
     */
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequestDTO dto) {
        authService.resetPassword(dto.email(), dto.code(), dto.freshPassword());
        return ResponseEntity.noContent().build();
    }

    // Sin JWT: el recién registrado aún no puede loguearse.
    // La identidad se prueba con el código de un solo uso.
    @PostMapping("/verificar-correo")
    /**
     * Verifies Response Entity&lt;Usuario Response DTO>.
     *
     * @param dto code Verificacion Request data transfer object used to scope this Response Entity&lt;Usuario Response DTO>
     * @param request incoming HTTP request used to scope this Response Entity&lt;Usuario Response DTO>
     * @return Response Entity&lt;Usuario Response DTO> reflecting the state after the operation
     */
    public ResponseEntity<UserResponseDTO> verifyEmail(
            @Valid @RequestBody CodeVerificationRequestDTO dto, HttpServletRequest request) {
        UserResponseDTO user = authService.verifyEmail(dto.email(), dto.code(), getIpSource(request));
        return ResponseEntity.ok(user);
    }

    @PostMapping("/login")
    /**
     * Handles login.
     *
     * @param dto Login Request data transfer object used to scope this login
     * @param request incoming HTTP request used to scope this login
     * @return Response Entity&lt;Token Response DTO> reflecting the state after the operation
     */
    public ResponseEntity<TokenResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto, HttpServletRequest request) {
        TokenResponseDTO tokens = authService.login(dto, getIpSource(request));
        ResponseCookie cookie = buildRefreshCookie(tokens.refreshToken(), jwtService.getRefreshExpirationMs());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(tokens);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader, HttpServletRequest request) {
        String token = authHeader.substring(BEARER_PREFIX.length());
        authService.logout(token, getIpSource(request));
        ResponseCookie cookieLimpia = buildRefreshCookie("", 0);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieLimpia.toString())
                .build();
    }

    // IP real del cliente para rate limit y auditoría. Lee getRemoteAddr();
    // no usa X-Forwarded-For por ser falsificable sin proxy de confianza.
    private String getIpSource(HttpServletRequest request) {
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
    private ResponseCookie buildRefreshCookie(String value, long maxAgeMs) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/api/auth")
                .maxAge(Duration.ofMillis(maxAgeMs))
                .build();
    }
}
