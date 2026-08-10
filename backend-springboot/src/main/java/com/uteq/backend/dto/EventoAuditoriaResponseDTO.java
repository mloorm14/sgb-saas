package com.uteq.backend.dto;

import java.time.OffsetDateTime;

/**
 * Fila del listado paginado de {@code GET /api/v1/auditoria} (Módulo 6).
 * Mapea {@code bitacora_auditoria} a un formato legible para la vista de
 * GERENTE/ADMIN:
 * <ul>
 *   <li>{@code usuario}: correo del usuario resuelto a partir de
 *       {@code BitacoraAuditoria.usuarioId} (que es nullable -- p.ej. un
 *       LOGIN_FAIL antes de identificar al usuario -- ver
 *       {@code AuthService.registrarAuditoria}). Cuando no hay id,
 *       {@code usuario} viaja como {@code null} en vez de inventar un
 *       valor.</li>
 *   <li>{@code accion}: {@code tipoOperacion} tal cual (INSERT, UPDATE,
 *       DELETE, LOGIN_OK, LOGIN_FAIL, LOGOUT -- restringido por el CHECK
 *       de la tabla, ver {@code BitacoraAuditoria}).</li>
 *   <li>{@code modulo}: {@code tablaAfectada} (p.ej. "usuarios",
 *       "prestamos"), el mismo valor que ya escriben todos los puntos que
 *       auditan hoy (AuthService, y desde el Módulo 5, UsuarioAdminService).</li>
 *   <li>{@code detalle}: {@code detalles} tal cual.</li>
 * </ul>
 */
public record EventoAuditoriaResponseDTO(
        Long id,
        String usuario,
        String accion,
        OffsetDateTime fechaHora,
        String modulo,
        String detalle
) {
}
