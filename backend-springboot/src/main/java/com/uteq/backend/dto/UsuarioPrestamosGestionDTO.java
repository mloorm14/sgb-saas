package com.uteq.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tarjeta de identificación del usuario que el bibliotecario encuentra por
 * cédula en la ventanilla de préstamos (GET
 * /api/v1/prestamos/gestion/buscar-usuario?cedula=).
 *
 * Mapeo con la especificación del módulo (los nombres de campo del enunciado
 * se resuelven sobre el modelo real, sin duplicar columnas):
 * - "cedula"          -> usuarios.identificacion_usuario
 * - "nombre_completo" -> usuarios.nombre + apellido (concatenado acá)
 * - "tipo_usuario"    -> nombres de los roles del usuario (tabla roles)
 * - "estado_cuenta"   -> estados_usuario.nombre (ACTIVO/BLOQUEADO_POR_MULTA/...)
 * - "multas_pendientes" -> suma de multas en estado PENDIENTE (calculada,
 *                        no es columna)
 * diasPrestamoSugerido viene de configuracion_sistema
 * ('dias_prestamo_default') para prellenar el formulario.
 */
public record UsuarioPrestamosGestionDTO(
        Long id,
        String nombreCompleto,
        String cedula,
        String correo,
        List<String> tiposUsuario,
        String estadoCuenta,
        BigDecimal montoMultasPendientes,
        Long cantidadMultasPendientes,
        Integer diasPrestamoSugerido
) {}
