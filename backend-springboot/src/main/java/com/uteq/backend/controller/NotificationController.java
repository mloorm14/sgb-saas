package com.uteq.backend.controller;

import com.uteq.backend.dto.NotificationResponseDTO;
import com.uteq.backend.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notificaciones")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // ── GET /api/v1/notificaciones/usuario/{usuarioId} ────
    // Mismo patrón de autorización que MultaController: un LECTOR solo ve
    // las suyas (validado en NotificacionService), el resto de roles puede
    // consultar cualquiera.
    /**
     * Consulta list by user usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param userId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @param pageable configuracion de pagina, tamano y orden usada para limitar la consulta
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    @GetMapping("/usuario/{usuarioId}")
    @PreAuthorize("hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<Page<NotificationResponseDTO>> listByUser(
            @PathVariable("usuarioId") Long userId,
            Authentication authentication,
            @PageableDefault(size = 10, sort = "created") Pageable pageable) {
        return ResponseEntity.ok(notificationService.listByUser(userId, authentication, pageable));
    }
}
