package com.uteq.backend.controller;

import com.uteq.backend.dto.HistorialPrestamoDTO;
import com.uteq.backend.dto.ReservaActivaDTO;
import com.uteq.backend.dto.UsuarioPrestamosGestionDTO;
import com.uteq.backend.dto.UsuarioSugerenciaDTO;
import com.uteq.backend.service.PrestamosGestionService;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lecturas de la ventanilla de préstamos (módulo "Préstamos" del sidebar
 * del BIBLIOTECARIO): encontrar al usuario por correo y armar la pantalla
 * -- tarjeta de identificación, reserva vigente e historial reciente.
 *
 * La CREACIÓN del préstamo no vive acá: se reutiliza POST /api/v1/prestamos
 * (PrestamoController.crear), que ahora acepta reservacionId opcional para
 * convertir una reserva en préstamo. Mismo criterio de roles que el resto
 * de operaciones de ventanilla: BIBLIOTECARIO/GERENTE.
 */
@RestController
@RequestMapping("/api/v1/prestamos/gestion")
@Validated
public class PrestamosGestionController {

    private final PrestamosGestionService prestamosGestionService;

    public PrestamosGestionController(PrestamosGestionService prestamosGestionService) {
        this.prestamosGestionService = prestamosGestionService;
    }

    // ── GET /api/v1/prestamos/gestion/buscar-usuario?correo= ──
    // Correo = usuarios.correo: es la identidad de login (UNIQUE), misma
    // columna que resuelve findByCorreo en el resto del sistema. 404 con
    // ProblemDetail si no hay coincidencia; el mensaje es el que muestra
    // la pantalla.
    @GetMapping("/buscar-usuario")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<UsuarioPrestamosGestionDTO> buscarUsuario(
            @RequestParam
            @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$",
                     message = "Ingresa un correo electrónico válido")
            String correo) {
        return ResponseEntity.ok(prestamosGestionService.buscarPorCorreo(correo));
    }

    // ── GET /api/v1/prestamos/gestion/sugerencias-usuarios?correo= ──
    // Autocompletado predictivo: retorna hasta 3 usuarios cuyo correo
    // contenga el texto ingresado (case-insensitive). El frontend lo usa
    // para el dropdown y el placeholder dinámico.
    @GetMapping("/sugerencias-usuarios")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<List<UsuarioSugerenciaDTO>> sugerenciasUsuarios(
            @RequestParam String correo) {
        return ResponseEntity.ok(prestamosGestionService.sugerenciasUsuarios(correo));
    }

    // ── GET /api/v1/prestamos/gestion/reserva-activa?usuarioId= ──
    // 404 cuando el usuario NO tiene reserva vigente -> el frontend cae al
    // Caso B (préstamo directo). No es un error para el usuario final.
    @GetMapping("/reserva-activa")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<ReservaActivaDTO> reservaActiva(@RequestParam Long usuarioId) {
        return ResponseEntity.ok(prestamosGestionService.reservaActiva(usuarioId));
    }

    // ── GET /api/v1/prestamos/gestion/historial?usuarioId= ────
    // Historial reciente (tope interno en el service) para la línea de
    // tiempo; lista vacía si el usuario no tiene préstamos.
    @GetMapping("/historial")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<List<HistorialPrestamoDTO>> historial(@RequestParam Long usuarioId) {
        return ResponseEntity.ok(prestamosGestionService.historial(usuarioId));
    }
}
