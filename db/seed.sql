-- ============================================================================
-- SGB-SaaS — db/seed.sql
-- Datos iniciales para levantar un entorno de desarrollo desde cero junto
-- con db/schema.sql (montados ambos en docker-entrypoint-initdb.d/).
-- NO usar en producción tal cual: contiene una contraseña de desarrollo
-- documentada en texto plano en este mismo archivo (ver más abajo) y en
-- README.md → "Credenciales de desarrollo".
--
-- Catálogos de estados_prestamo, estados_multa y estados_reservacion
-- confirmados por el equipo (deben coincidir exactamente con los nombres
-- referenciados por los stored procedures de A.2 — sp_registrar_devolucion,
-- sp_pagar_multa, sp_anular_multa, etc. — cualquier cambio aquí debe
-- reflejarse también allá).
-- ============================================================================

-- ===== estados_usuario =====
INSERT INTO estados_usuario (nombre) VALUES
    ('ACTIVO'),
    ('BLOQUEADO_POR_MULTA'),
    ('INACTIVO'),
    ('PENDIENTE_VERIFICACION');

-- ===== roles =====
INSERT INTO roles (nombre, descripcion) VALUES
    ('LECTOR',        'Usuario final: consulta catálogo, reserva y solicita préstamos'),
    ('BIBLIOTECARIO',  'Gestiona préstamos, devoluciones, reservas y multas'),
    ('GERENTE',        'Gestiona catálogo, inventario y reportes'),
    ('ADMIN',          'Administración total del sistema, usuarios y roles');

-- ===== estados_libro =====
INSERT INTO estados_libro (nombre) VALUES
    ('ACTIVO'),
    ('DADO_DE_BAJA'),
    ('EN_REPARACION'),
    ('PERDIDO');

-- ===== estados_prestamo =====
INSERT INTO estados_prestamo (nombre) VALUES
    ('ACTIVO'),
    ('RENOVADO'),
    ('DEVUELTO'),
    ('VENCIDO');

-- ===== estados_multa =====
INSERT INTO estados_multa (nombre) VALUES
    ('PENDIENTE'),
    ('PAGADA'),
    ('ANULADA');

-- ===== estados_reservacion =====
INSERT INTO estados_reservacion (nombre) VALUES
    ('PENDIENTE'),
    ('LISTA_PARA_RETIRO'),
    ('RETIRADA'),
    ('EXPIRADA'),
    ('CANCELADA');

-- ===== tipos_notificacion =====
-- Catalogo creado por V6__notificaciones.sql. Solo se agrega 'COMPROBANTE_PAGO'
-- (V16__multas_pago_parcial.sql) porque es el unico valor que bloquea CI hoy
-- (fn_pagos_recientes.sql asume que existe). El catalogo completo tiene
-- ademas 'VENCIMIENTO'/'MULTA'/'RESERVA_CADUCADA' (V6) y 'DISPONIBLE' (V27),
-- ausentes de este snapshot -- deuda tecnica conocida, fuera de alcance de
-- este fix puntual.
INSERT INTO tipos_notificacion (nombre) VALUES
    ('COMPROBANTE_PAGO');

-- ===== configuracion_sistema =====
-- 'monto_multa_diaria' es requerido por db/procs/sp_registrar_devolucion.sql
-- (calcula multas.monto = dias_atraso * este valor). Placeholder razonable
-- para desarrollo — ajustar al valor real que defina la biblioteca.
-- 'max_tamano_portada_mb' es requerido por LibroService.validarPortada
-- (limite de subida de portadas, ver V13__portada_imagen.sql). El snapshot
-- debe ser autocontenido: no depender de que Flyway aplique V4+/V13.
-- Las tres siguientes ('minutos_reserva', 'dias_prestamo_default',
-- 'max_renovaciones_default') vienen de V4__configuracion_sistema_valores_
-- iniciales.sql, requeridas por ReservacionService/PrestamoService.
INSERT INTO configuracion_sistema (clave, valor) VALUES
    ('monto_multa_diaria', '0.50'),
    ('max_tamano_portada_mb', '2'),
    ('minutos_reserva', '1440'),
    ('dias_prestamo_default', '15'),
    ('max_renovaciones_default', '2');

-- ===== editoriales =====
INSERT INTO editoriales (nombre, pais_origen) VALUES
    ('Prentice Hall',    'Estados Unidos'),
    ('Addison-Wesley',   'Estados Unidos'),
    ('Debolsillo',       'España'),
    ('Debate',           'España'),
    ('Plaza & Janés',    'España');

-- ===== idiomas =====
INSERT INTO idiomas (nombre, codigo_iso) VALUES
    ('Español', 'es'),
    ('Inglés',  'en');

-- ===== categorias =====
INSERT INTO categorias (nombre) VALUES
    ('Ficción'),
    ('Tecnología'),
    ('Historia');

-- ===== autores =====
INSERT INTO autores (nombre) VALUES
    ('Robert C. Martin'),
    ('Martin Fowler'),
    ('Gabriel García Márquez'),
    ('Isabel Allende'),
    ('Yuval Noah Harari');

-- ============================================================================
-- USUARIO ADMINISTRADOR DE DESARROLLO
-- Contraseña en texto plano: Admin123!
-- Hash BCrypt (costo 12, generado con org.springframework.security.crypto
-- .bcrypt.BCryptPasswordEncoder(12), el mismo encoder usado por
-- SecurityConfig.passwordEncoder()):
--   $2a$12$FIh2GQfhmm1nmqybVIIquuoL0xsLlbcL1oBQ74b6P0QXOwJQ34B8y
-- Ver también README.md → "Credenciales de desarrollo". NO usar esta
-- contraseña ni este hash en un entorno real.
-- ============================================================================
INSERT INTO usuarios (nombre, apellido, correo, password_hash, estado_id, correo_verificado)
VALUES (
    'Admin',
    'SGB',
    'admin@sgb-saas.local',
    '$2a$12$FIh2GQfhmm1nmqybVIIquuoL0xsLlbcL1oBQ74b6P0QXOwJQ34B8y',
    (SELECT id FROM estados_usuario WHERE nombre = 'ACTIVO'),
    TRUE
);

INSERT INTO usuario_roles (usuario_id, rol_id)
VALUES (
    (SELECT id FROM usuarios WHERE correo = 'admin@sgb-saas.local'),
    (SELECT id FROM roles WHERE nombre = 'ADMIN')
);

-- ============================================================================
-- USUARIO DEMO DE EVALUACIÓN (espejo de V11__seed_usuario_demo.sql)
-- Contraseña en texto plano: usuario1
-- Hash BCrypt (costo 12, $2a$ — mismo formato que el admin arriba):
--   $2a$12$h1.Cc0mZA1T/L13tFiq61OA7rrdLjDMZGiO36IDqZoRgjIoxSMeFe
-- Rol limitado LECTOR (sin permisos administrativos), separado del admin
-- real. Credenciales públicas documentadas en README.md → "Cuenta demo".
-- Este bloque es el reflejo local de la migración V11 Flyway (y su fix
-- V12 para cuentas preexistentes): si se edita uno, hay que reflejar el
-- cambio en el otro a mano (convención de V10__seed_catalogos_y_admin.sql).
-- ============================================================================
INSERT INTO usuarios (nombre, apellido, correo, password_hash, estado_id, correo_verificado)
VALUES (
    'Usuario',
    'Demo',
    'u@uteq.edu.ec',
    '$2a$12$h1.Cc0mZA1T/L13tFiq61OA7rrdLjDMZGiO36IDqZoRgjIoxSMeFe',
    (SELECT id FROM estados_usuario WHERE nombre = 'ACTIVO'),
    TRUE
);

INSERT INTO usuario_roles (usuario_id, rol_id)
VALUES (
    (SELECT id FROM usuarios WHERE correo = 'u@uteq.edu.ec'),
    (SELECT id FROM roles WHERE nombre = 'LECTOR')
);

-- ============================================================================
-- LIBROS DE EJEMPLO
-- ============================================================================
INSERT INTO libros (isbn, titulo, resumen, anio_publicacion, editorial_id, idioma_id, estado_id, stock_total, stock_disponible)
VALUES
    ('9780132350884', 'Clean Code',
     'Guía de prácticas para escribir código legible y mantenible.',
     2008,
     (SELECT id FROM editoriales WHERE nombre = 'Prentice Hall'),
     (SELECT id FROM idiomas WHERE codigo_iso = 'en'),
     (SELECT id FROM estados_libro WHERE nombre = 'ACTIVO'),
     3, 3),
    ('9780134757599', 'Refactoring: Improving the Design of Existing Code',
     'Catálogo de técnicas para mejorar la estructura interna del código sin alterar su comportamiento.',
     2018,
     (SELECT id FROM editoriales WHERE nombre = 'Addison-Wesley'),
     (SELECT id FROM idiomas WHERE codigo_iso = 'en'),
     (SELECT id FROM estados_libro WHERE nombre = 'ACTIVO'),
     2, 2),
    ('9780307474728', 'Cien años de soledad',
     'Novela emblemática del realismo mágico latinoamericano.',
     1967,
     (SELECT id FROM editoriales WHERE nombre = 'Debolsillo'),
     (SELECT id FROM idiomas WHERE codigo_iso = 'es'),
     (SELECT id FROM estados_libro WHERE nombre = 'ACTIVO'),
     4, 4),
    ('9788499926223', 'Sapiens: De animales a dioses',
     'Recorrido por la historia de la humanidad desde la Edad de Piedra hasta la actualidad.',
     2014,
     (SELECT id FROM editoriales WHERE nombre = 'Debate'),
     (SELECT id FROM idiomas WHERE codigo_iso = 'es'),
     (SELECT id FROM estados_libro WHERE nombre = 'ACTIVO'),
     2, 2),
    ('9788401352836', 'La casa de los espíritus',
     'Saga familiar que combina historia política y elementos fantásticos.',
     1982,
     (SELECT id FROM editoriales WHERE nombre = 'Plaza & Janés'),
     (SELECT id FROM idiomas WHERE codigo_iso = 'es'),
     (SELECT id FROM estados_libro WHERE nombre = 'ACTIVO'),
     3, 3);

INSERT INTO libro_autores (libro_id, autor_id) VALUES
    ((SELECT id FROM libros WHERE isbn = '9780132350884'), (SELECT id FROM autores WHERE nombre = 'Robert C. Martin')),
    ((SELECT id FROM libros WHERE isbn = '9780134757599'), (SELECT id FROM autores WHERE nombre = 'Martin Fowler')),
    ((SELECT id FROM libros WHERE isbn = '9780307474728'), (SELECT id FROM autores WHERE nombre = 'Gabriel García Márquez')),
    ((SELECT id FROM libros WHERE isbn = '9788499926223'), (SELECT id FROM autores WHERE nombre = 'Yuval Noah Harari')),
    ((SELECT id FROM libros WHERE isbn = '9788401352836'), (SELECT id FROM autores WHERE nombre = 'Isabel Allende'));

INSERT INTO libro_categorias (libro_id, categoria_id) VALUES
    ((SELECT id FROM libros WHERE isbn = '9780132350884'), (SELECT id FROM categorias WHERE nombre = 'Tecnología')),
    ((SELECT id FROM libros WHERE isbn = '9780134757599'), (SELECT id FROM categorias WHERE nombre = 'Tecnología')),
    ((SELECT id FROM libros WHERE isbn = '9780307474728'), (SELECT id FROM categorias WHERE nombre = 'Ficción')),
    ((SELECT id FROM libros WHERE isbn = '9788499926223'), (SELECT id FROM categorias WHERE nombre = 'Historia')),
    ((SELECT id FROM libros WHERE isbn = '9788401352836'), (SELECT id FROM categorias WHERE nombre = 'Ficción'));
