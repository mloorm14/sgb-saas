-- ============================================================================
-- SGB-SaaS - db/schema.sql
-- Snapshot consolidado del estado objetivo del esquema (44 tablas) para
-- reproducibilidad desde cero via docker-entrypoint-initdb.d/. Generado con
-- pg_dump --schema-only --no-owner --no-privileges desde una base con TODAS
-- las migraciones aplicadas (database/migrations/ V1..V48 + R__stored_procedures.sql,
-- via Flyway 10) -- ver docs/adr/adr-013-estrategia-schema-reproducible.md.
--
-- Regenerado el 2026-09-08 (el snapshot anterior databa de V13 / 2026-09-01
-- y se habia quedado en 35 CREATE TABLE, desactualizado desde entonces).
-- No incluye la tabla flyway_schema_history (bookkeeping propio de Flyway,
-- no forma parte del modelo de datos de la aplicacion).
--
-- NOTA: este dump incluye, ademas de las 44 tablas, TODAS las funciones
-- creadas por las migraciones (triggers genericos como fn_auditoria_generica
-- y set_actualizado_en, y tambien los procedimientos/funciones de negocio
-- de db/procs/*.sql -- p.ej. sp_crear_prestamo, sp_registrar_devolucion,
-- los fn_reporte_*). Esto duplica intencionalmente el CREATE OR REPLACE
-- FUNCTION que ya vive en db/procs/*.sql: es redundante pero inofensivo
-- (los 11 archivos de db/procs usan CREATE OR REPLACE, no chocan), ya que
-- scripts/build-init-sql.sh concatena schema.sql + procs/*.sql + seed.sql
-- y ambas definiciones son identicas. Ver docs/observaciones/OBSERVACIONES.md
-- (auditoria de la rubrica ADB, criterio 1) para el detalle de esta
-- decision -- no se filtro el dump para no arriesgar romper una dependencia
-- de trigger no evidente.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;

COMMENT ON EXTENSION pg_trgm IS 'text similarity measurement and index searching based on trigrams';

CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;

COMMENT ON EXTENSION "uuid-ossp" IS 'generate universally unique identifiers (UUIDs)';

CREATE TABLE public.autores (
    id bigint NOT NULL,
    nombre character varying(150) NOT NULL
);

CREATE SEQUENCE public.autores_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.autores_id_seq OWNED BY public.autores.id;

CREATE TABLE public.backup_programacion (
    id bigint NOT NULL,
    creado_por bigint NOT NULL,
    cada_horas integer,
    cada_dias integer,
    formato character varying(10) DEFAULT 'sql'::character varying NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    ultima_ejecucion timestamp with time zone,
    CONSTRAINT backup_programacion_cada_dias_check CHECK (((cada_dias IS NULL) OR ((cada_dias >= 1) AND (cada_dias <= 30)))),
    CONSTRAINT backup_programacion_cada_horas_check CHECK (((cada_horas IS NULL) OR ((cada_horas >= 1) AND (cada_horas <= 23)))),
    CONSTRAINT backup_programacion_check CHECK (((((cada_horas IS NOT NULL))::integer + ((cada_dias IS NOT NULL))::integer) = 1)),
    CONSTRAINT backup_programacion_creado_por_check CHECK ((creado_por IS NOT NULL))
);

COMMENT ON COLUMN public.backup_programacion.ultima_ejecucion IS 'Fecha/hora de la última ejecución automática programada. NULL si nunca ejecutó.';

CREATE SEQUENCE public.backup_programacion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.backup_programacion_id_seq OWNED BY public.backup_programacion.id;

CREATE TABLE public.backup_programacion_tablas (
    programacion_id bigint NOT NULL,
    tabla character varying(50) NOT NULL
);

CREATE TABLE public.backups (
    id bigint NOT NULL,
    creado_por bigint NOT NULL,
    desde timestamp with time zone NOT NULL,
    hasta timestamp with time zone NOT NULL,
    formato character varying(10) DEFAULT 'sql'::character varying NOT NULL,
    estado character varying(20) DEFAULT 'COMPLETADO'::character varying NOT NULL,
    tamano_bytes bigint,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    ruta character varying(500) NOT NULL,
    tipo character varying(20) NOT NULL,
    CONSTRAINT backups_check CHECK ((desde < hasta)),
    CONSTRAINT backups_hasta_check CHECK ((hasta <= now())),
    CONSTRAINT backups_tamano_bytes_check CHECK (((tamano_bytes IS NULL) OR (tamano_bytes >= 0)))
);

CREATE SEQUENCE public.backups_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.backups_id_seq OWNED BY public.backups.id;

CREATE TABLE public.backups_tablas (
    backup_id bigint NOT NULL,
    tabla character varying(50) NOT NULL
);

CREATE TABLE public.base_conocimiento (
    id integer NOT NULL,
    categoria character varying(40) NOT NULL,
    pregunta_ejemplo text NOT NULL,
    respuesta text NOT NULL,
    activo boolean DEFAULT true NOT NULL
);

CREATE SEQUENCE public.base_conocimiento_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.base_conocimiento_id_seq OWNED BY public.base_conocimiento.id;

CREATE TABLE public.bitacora_auditoria (
    id bigint NOT NULL,
    usuario_id bigint,
    tipo_operacion character varying(20) NOT NULL,
    tabla_afectada character varying(50) NOT NULL,
    registro_id bigint,
    detalles text NOT NULL,
    ip_origen character varying(45),
    fecha_hora timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT bitacora_auditoria_tipo_operacion_check CHECK (((tipo_operacion)::text = ANY ((ARRAY['INSERT'::character varying, 'UPDATE'::character varying, 'DELETE'::character varying, 'LOGIN_OK'::character varying, 'LOGIN_FAIL'::character varying, 'LOGOUT'::character varying, 'CORREO_VERIFICADO'::character varying])::text[])))
);

CREATE SEQUENCE public.bitacora_auditoria_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.bitacora_auditoria_id_seq OWNED BY public.bitacora_auditoria.id;

CREATE TABLE public.categorias (
    id integer NOT NULL,
    nombre character varying(80) NOT NULL
);

CREATE TABLE public.categorias_dano (
    id integer NOT NULL,
    nombre character varying(50) NOT NULL,
    activo boolean DEFAULT true NOT NULL
);

CREATE SEQUENCE public.categorias_dano_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.categorias_dano_id_seq OWNED BY public.categorias_dano.id;

CREATE SEQUENCE public.categorias_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.categorias_id_seq OWNED BY public.categorias.id;

CREATE TABLE public.configuracion_respaldo (
    id bigint NOT NULL,
    habilitado boolean DEFAULT true,
    frecuencia_horas integer DEFAULT 6 NOT NULL,
    dias_retencion integer DEFAULT 14 NOT NULL,
    ultima_ejecucion timestamp with time zone,
    proxima_ejecucion timestamp with time zone,
    actualizado_por bigint,
    actualizado_en timestamp with time zone DEFAULT now()
);

CREATE SEQUENCE public.configuracion_respaldo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.configuracion_respaldo_id_seq OWNED BY public.configuracion_respaldo.id;

CREATE TABLE public.configuracion_sistema (
    clave character varying(50) NOT NULL,
    valor character varying(200) NOT NULL
);

CREATE TABLE public.editoriales (
    id integer NOT NULL,
    nombre character varying(150) NOT NULL,
    pais_origen character varying(80)
);

CREATE SEQUENCE public.editoriales_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.editoriales_id_seq OWNED BY public.editoriales.id;

CREATE TABLE public.estados_libro (
    id integer NOT NULL,
    nombre character varying(30) NOT NULL
);

CREATE SEQUENCE public.estados_libro_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.estados_libro_id_seq OWNED BY public.estados_libro.id;

CREATE TABLE public.estados_multa (
    id integer NOT NULL,
    nombre character varying(30) NOT NULL
);

CREATE SEQUENCE public.estados_multa_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.estados_multa_id_seq OWNED BY public.estados_multa.id;

CREATE TABLE public.estados_prestamo (
    id integer NOT NULL,
    nombre character varying(30) NOT NULL
);

CREATE SEQUENCE public.estados_prestamo_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.estados_prestamo_id_seq OWNED BY public.estados_prestamo.id;

CREATE TABLE public.estados_reservacion (
    id integer NOT NULL,
    nombre character varying(30) NOT NULL
);

CREATE SEQUENCE public.estados_reservacion_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.estados_reservacion_id_seq OWNED BY public.estados_reservacion.id;

CREATE TABLE public.estados_usuario (
    id integer NOT NULL,
    nombre character varying(30) NOT NULL
);

CREATE SEQUENCE public.estados_usuario_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.estados_usuario_id_seq OWNED BY public.estados_usuario.id;

CREATE TABLE public.evidencia_dano (
    id bigint NOT NULL,
    registro_dano_id bigint NOT NULL,
    archivo_nombre character varying(255) NOT NULL,
    archivo_tipo character varying(100) NOT NULL,
    archivo_bytes bytea NOT NULL,
    subido_en timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE public.evidencia_dano_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.evidencia_dano_id_seq OWNED BY public.evidencia_dano.id;

CREATE TABLE public.favoritos (
    usuario_id bigint NOT NULL,
    libro_id bigint NOT NULL,
    agregado_en timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.idiomas (
    id integer NOT NULL,
    nombre character varying(50) NOT NULL,
    codigo_iso character varying(5) NOT NULL
);

CREATE SEQUENCE public.idiomas_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.idiomas_id_seq OWNED BY public.idiomas.id;

CREATE TABLE public.libro_autores (
    libro_id bigint NOT NULL,
    autor_id bigint NOT NULL
);

CREATE TABLE public.libro_categorias (
    libro_id bigint NOT NULL,
    categoria_id integer NOT NULL
);

CREATE TABLE public.libros (
    id bigint NOT NULL,
    isbn character varying(13) NOT NULL,
    titulo character varying(255) NOT NULL,
    resumen text,
    portada_url character varying(1000),
    anio_publicacion smallint NOT NULL,
    editorial_id integer NOT NULL,
    idioma_id integer NOT NULL,
    estado_id integer NOT NULL,
    stock_total smallint DEFAULT 1 NOT NULL,
    stock_disponible smallint DEFAULT 1 NOT NULL,
    fecha_registro timestamp with time zone DEFAULT now() NOT NULL,
    actualizado_en timestamp with time zone DEFAULT now() NOT NULL,
    ubicacion_fisica character varying(50),
    portada_imagen bytea,
    portada_nombre character varying(255),
    portada_tipo character varying(100),
    portada_tamanio integer,
    numero_paginas smallint,
    precio_base numeric(10,2),
    proveedor_id integer,
    CONSTRAINT chk_anio_publicacion CHECK (((anio_publicacion >= 1000) AND (anio_publicacion <= 2100))),
    CONSTRAINT chk_stock_disponible CHECK (((stock_disponible >= 0) AND (stock_disponible <= stock_total))),
    CONSTRAINT chk_stock_total CHECK ((stock_total >= 0)),
    CONSTRAINT libros_numero_paginas_check CHECK ((numero_paginas > 0)),
    CONSTRAINT libros_precio_base_check CHECK ((precio_base >= (0)::numeric))
);

CREATE SEQUENCE public.libros_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.libros_id_seq OWNED BY public.libros.id;

CREATE TABLE public.mensajes_chat (
    id bigint NOT NULL,
    sesion_id uuid NOT NULL,
    rol character varying(10) NOT NULL,
    contenido text NOT NULL,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT mensajes_chat_rol_check CHECK (((rol)::text = ANY ((ARRAY['USUARIO'::character varying, 'ASISTENTE'::character varying])::text[])))
);

CREATE SEQUENCE public.mensajes_chat_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.mensajes_chat_id_seq OWNED BY public.mensajes_chat.id;

CREATE TABLE public.multas (
    id bigint NOT NULL,
    prestamo_id bigint NOT NULL,
    monto numeric(8,2) NOT NULL,
    estado_multa_id integer NOT NULL,
    fecha_generada timestamp with time zone DEFAULT now() NOT NULL,
    fecha_pagada timestamp with time zone,
    observaciones character varying(255),
    registro_dano_id bigint,
    monto_pagado numeric(8,2) DEFAULT 0 NOT NULL,
    CONSTRAINT multas_monto_check CHECK ((monto > (0)::numeric))
);

CREATE SEQUENCE public.multas_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.multas_id_seq OWNED BY public.multas.id;

CREATE TABLE public.notificaciones (
    id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    prestamo_id bigint,
    tipo_notificacion_id integer NOT NULL,
    mensaje text NOT NULL,
    fecha_envio timestamp with time zone,
    enviado_ok boolean DEFAULT false NOT NULL,
    error_envio character varying(255),
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE public.notificaciones_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.notificaciones_id_seq OWNED BY public.notificaciones.id;

CREATE TABLE public.permisos (
    id integer NOT NULL,
    codigo character varying(60) NOT NULL
);

CREATE SEQUENCE public.permisos_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.permisos_id_seq OWNED BY public.permisos.id;

CREATE TABLE public.prestamos (
    id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    libro_id bigint NOT NULL,
    bibliotecario_id bigint NOT NULL,
    reservacion_id bigint,
    fecha_prestamo timestamp with time zone DEFAULT now() NOT NULL,
    fecha_devolucion_estimada timestamp with time zone NOT NULL,
    fecha_devolucion_real timestamp with time zone,
    renovaciones_realizadas smallint DEFAULT 0 NOT NULL,
    estado_prestamo_id integer NOT NULL,
    CONSTRAINT prestamos_renovaciones_realizadas_check CHECK ((renovaciones_realizadas >= 0))
);

CREATE SEQUENCE public.prestamos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.prestamos_id_seq OWNED BY public.prestamos.id;

CREATE TABLE public.proveedores (
    id integer NOT NULL,
    nombre character varying(150) NOT NULL,
    ruc character varying(20),
    direccion character varying(255),
    telefono character varying(30),
    email character varying(150),
    persona_contacto character varying(150),
    activo boolean DEFAULT true NOT NULL,
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE public.proveedores_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.proveedores_id_seq OWNED BY public.proveedores.id;

CREATE TABLE public.registro_dano_detalle (
    id bigint NOT NULL,
    registro_dano_id bigint NOT NULL,
    tipo_dano_id integer,
    nombre_custom character varying(100),
    precio_cobrado numeric(8,2) NOT NULL,
    CONSTRAINT registro_dano_detalle_check CHECK (((tipo_dano_id IS NOT NULL) OR (nombre_custom IS NOT NULL))),
    CONSTRAINT registro_dano_detalle_precio_cobrado_check CHECK ((precio_cobrado >= (0)::numeric))
);

CREATE SEQUENCE public.registro_dano_detalle_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.registro_dano_detalle_id_seq OWNED BY public.registro_dano_detalle.id;

CREATE TABLE public.registro_danos (
    id bigint NOT NULL,
    prestamo_id bigint NOT NULL,
    estado_devolucion character varying(20) NOT NULL,
    descripcion text,
    bibliotecario_id bigint NOT NULL,
    fecha_registro timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT registro_danos_estado_devolucion_check CHECK (((estado_devolucion)::text = ANY ((ARRAY['BUEN_ESTADO'::character varying, 'CON_DANO'::character varying, 'PERDIDO'::character varying])::text[])))
);

CREATE SEQUENCE public.registro_danos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.registro_danos_id_seq OWNED BY public.registro_danos.id;

CREATE TABLE public.registros_respaldo (
    id bigint NOT NULL,
    tipo character varying(20) NOT NULL,
    estado character varying(20) NOT NULL,
    nombre_archivo character varying(255),
    tamano_archivo_bytes bigint,
    ruta_r2 text,
    mensaje_error text,
    ejecutado_por bigint,
    iniciado_en timestamp with time zone DEFAULT now(),
    finalizado_en timestamp with time zone
);

CREATE SEQUENCE public.registros_respaldo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.registros_respaldo_id_seq OWNED BY public.registros_respaldo.id;

CREATE TABLE public.reservaciones (
    id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    libro_id bigint NOT NULL,
    estado_reservacion_id integer NOT NULL,
    fecha_reserva timestamp with time zone DEFAULT now() NOT NULL,
    fecha_limite_retiro timestamp with time zone NOT NULL
);

CREATE SEQUENCE public.reservaciones_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.reservaciones_id_seq OWNED BY public.reservaciones.id;

CREATE TABLE public.rol_permisos (
    rol_id integer NOT NULL,
    permiso_id integer NOT NULL
);

CREATE TABLE public.roles (
    id integer NOT NULL,
    nombre character varying(30) NOT NULL,
    descripcion character varying(200)
);

CREATE SEQUENCE public.roles_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.roles_id_seq OWNED BY public.roles.id;

CREATE TABLE public.sesiones_chat (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    usuario_id bigint NOT NULL,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    ultima_actividad timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.sugerencias_adquisicion (
    id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    titulo character varying(255) NOT NULL,
    autor character varying(150),
    isbn character varying(13),
    justificacion text,
    estado character varying(20) DEFAULT 'PENDIENTE'::character varying NOT NULL,
    revisado_por bigint,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    proveedor_id integer,
    CONSTRAINT sugerencias_adquisicion_estado_check CHECK (((estado)::text = ANY ((ARRAY['PENDIENTE'::character varying, 'APROBADA'::character varying, 'RECHAZADA'::character varying])::text[])))
);

CREATE SEQUENCE public.sugerencias_adquisicion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.sugerencias_adquisicion_id_seq OWNED BY public.sugerencias_adquisicion.id;

CREATE TABLE public.suscripciones_disponibilidad (
    id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    libro_id bigint NOT NULL,
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE public.suscripciones_disponibilidad_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.suscripciones_disponibilidad_id_seq OWNED BY public.suscripciones_disponibilidad.id;

CREATE TABLE public.tipos_dano (
    id integer NOT NULL,
    nombre character varying(50) NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    categoria_id integer NOT NULL,
    tipo_costo character varying(10) NOT NULL,
    valor numeric(10,2) NOT NULL,
    CONSTRAINT chk_tipos_dano_porcentaje CHECK ((((tipo_costo)::text = 'FIJO'::text) OR ((valor >= (0)::numeric) AND (valor <= (100)::numeric)))),
    CONSTRAINT tipos_dano_tipo_costo_check CHECK (((tipo_costo)::text = ANY ((ARRAY['FIJO'::character varying, 'PORCENTAJE'::character varying])::text[]))),
    CONSTRAINT tipos_dano_valor_check CHECK ((valor >= (0)::numeric))
);

CREATE SEQUENCE public.tipos_dano_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.tipos_dano_id_seq OWNED BY public.tipos_dano.id;

CREATE TABLE public.tipos_notificacion (
    id integer NOT NULL,
    nombre character varying(30) NOT NULL
);

CREATE SEQUENCE public.tipos_notificacion_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.tipos_notificacion_id_seq OWNED BY public.tipos_notificacion.id;

CREATE TABLE public.tokens_invalidos (
    id bigint NOT NULL,
    jti character varying(100) NOT NULL,
    usuario_id bigint NOT NULL,
    expira_en timestamp with time zone NOT NULL,
    invalidado_en timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE public.tokens_invalidos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.tokens_invalidos_id_seq OWNED BY public.tokens_invalidos.id;

CREATE TABLE public.usuario_roles (
    usuario_id bigint NOT NULL,
    rol_id integer NOT NULL,
    asignado_en timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.usuarios (
    id bigint NOT NULL,
    nombre character varying(100) NOT NULL,
    correo character varying(150) NOT NULL,
    password_hash character varying(255) NOT NULL,
    fecha_registro timestamp with time zone DEFAULT now() NOT NULL,
    actualizado_en timestamp with time zone DEFAULT now() NOT NULL,
    apellido character varying(100) NOT NULL,
    identificacion_usuario character varying(20),
    correo_verificado boolean DEFAULT false NOT NULL,
    estado_id integer NOT NULL,
    credencial_qr_token uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    creado_por bigint
);

CREATE SEQUENCE public.usuarios_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.usuarios_id_seq OWNED BY public.usuarios.id;

CREATE TABLE public.verificaciones_correo (
    id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    token uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    expira_en timestamp with time zone NOT NULL,
    usado boolean DEFAULT false NOT NULL,
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE public.verificaciones_correo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.verificaciones_correo_id_seq OWNED BY public.verificaciones_correo.id;

ALTER TABLE ONLY public.autores ALTER COLUMN id SET DEFAULT nextval('public.autores_id_seq'::regclass);

ALTER TABLE ONLY public.backup_programacion ALTER COLUMN id SET DEFAULT nextval('public.backup_programacion_id_seq'::regclass);

ALTER TABLE ONLY public.backups ALTER COLUMN id SET DEFAULT nextval('public.backups_id_seq'::regclass);

ALTER TABLE ONLY public.base_conocimiento ALTER COLUMN id SET DEFAULT nextval('public.base_conocimiento_id_seq'::regclass);

ALTER TABLE ONLY public.bitacora_auditoria ALTER COLUMN id SET DEFAULT nextval('public.bitacora_auditoria_id_seq'::regclass);

ALTER TABLE ONLY public.categorias ALTER COLUMN id SET DEFAULT nextval('public.categorias_id_seq'::regclass);

ALTER TABLE ONLY public.categorias_dano ALTER COLUMN id SET DEFAULT nextval('public.categorias_dano_id_seq'::regclass);

ALTER TABLE ONLY public.configuracion_respaldo ALTER COLUMN id SET DEFAULT nextval('public.configuracion_respaldo_id_seq'::regclass);

ALTER TABLE ONLY public.editoriales ALTER COLUMN id SET DEFAULT nextval('public.editoriales_id_seq'::regclass);

ALTER TABLE ONLY public.estados_libro ALTER COLUMN id SET DEFAULT nextval('public.estados_libro_id_seq'::regclass);

ALTER TABLE ONLY public.estados_multa ALTER COLUMN id SET DEFAULT nextval('public.estados_multa_id_seq'::regclass);

ALTER TABLE ONLY public.estados_prestamo ALTER COLUMN id SET DEFAULT nextval('public.estados_prestamo_id_seq'::regclass);

ALTER TABLE ONLY public.estados_reservacion ALTER COLUMN id SET DEFAULT nextval('public.estados_reservacion_id_seq'::regclass);

ALTER TABLE ONLY public.estados_usuario ALTER COLUMN id SET DEFAULT nextval('public.estados_usuario_id_seq'::regclass);

ALTER TABLE ONLY public.evidencia_dano ALTER COLUMN id SET DEFAULT nextval('public.evidencia_dano_id_seq'::regclass);

ALTER TABLE ONLY public.idiomas ALTER COLUMN id SET DEFAULT nextval('public.idiomas_id_seq'::regclass);

ALTER TABLE ONLY public.libros ALTER COLUMN id SET DEFAULT nextval('public.libros_id_seq'::regclass);

ALTER TABLE ONLY public.mensajes_chat ALTER COLUMN id SET DEFAULT nextval('public.mensajes_chat_id_seq'::regclass);

ALTER TABLE ONLY public.multas ALTER COLUMN id SET DEFAULT nextval('public.multas_id_seq'::regclass);

ALTER TABLE ONLY public.notificaciones ALTER COLUMN id SET DEFAULT nextval('public.notificaciones_id_seq'::regclass);

ALTER TABLE ONLY public.permisos ALTER COLUMN id SET DEFAULT nextval('public.permisos_id_seq'::regclass);

ALTER TABLE ONLY public.prestamos ALTER COLUMN id SET DEFAULT nextval('public.prestamos_id_seq'::regclass);

ALTER TABLE ONLY public.proveedores ALTER COLUMN id SET DEFAULT nextval('public.proveedores_id_seq'::regclass);

ALTER TABLE ONLY public.registro_dano_detalle ALTER COLUMN id SET DEFAULT nextval('public.registro_dano_detalle_id_seq'::regclass);

ALTER TABLE ONLY public.registro_danos ALTER COLUMN id SET DEFAULT nextval('public.registro_danos_id_seq'::regclass);

ALTER TABLE ONLY public.registros_respaldo ALTER COLUMN id SET DEFAULT nextval('public.registros_respaldo_id_seq'::regclass);

ALTER TABLE ONLY public.reservaciones ALTER COLUMN id SET DEFAULT nextval('public.reservaciones_id_seq'::regclass);

ALTER TABLE ONLY public.roles ALTER COLUMN id SET DEFAULT nextval('public.roles_id_seq'::regclass);

ALTER TABLE ONLY public.sugerencias_adquisicion ALTER COLUMN id SET DEFAULT nextval('public.sugerencias_adquisicion_id_seq'::regclass);

ALTER TABLE ONLY public.suscripciones_disponibilidad ALTER COLUMN id SET DEFAULT nextval('public.suscripciones_disponibilidad_id_seq'::regclass);

ALTER TABLE ONLY public.tipos_dano ALTER COLUMN id SET DEFAULT nextval('public.tipos_dano_id_seq'::regclass);

ALTER TABLE ONLY public.tipos_notificacion ALTER COLUMN id SET DEFAULT nextval('public.tipos_notificacion_id_seq'::regclass);

ALTER TABLE ONLY public.tokens_invalidos ALTER COLUMN id SET DEFAULT nextval('public.tokens_invalidos_id_seq'::regclass);

ALTER TABLE ONLY public.usuarios ALTER COLUMN id SET DEFAULT nextval('public.usuarios_id_seq'::regclass);

ALTER TABLE ONLY public.verificaciones_correo ALTER COLUMN id SET DEFAULT nextval('public.verificaciones_correo_id_seq'::regclass);

ALTER TABLE ONLY public.autores
    ADD CONSTRAINT autores_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.backup_programacion
    ADD CONSTRAINT backup_programacion_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.backups
    ADD CONSTRAINT backups_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.base_conocimiento
    ADD CONSTRAINT base_conocimiento_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.bitacora_auditoria
    ADD CONSTRAINT bitacora_auditoria_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.categorias_dano
    ADD CONSTRAINT categorias_dano_nombre_key UNIQUE (nombre);

ALTER TABLE ONLY public.categorias_dano
    ADD CONSTRAINT categorias_dano_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.categorias
    ADD CONSTRAINT categorias_nombre_key UNIQUE (nombre);

ALTER TABLE ONLY public.categorias
    ADD CONSTRAINT categorias_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.configuracion_respaldo
    ADD CONSTRAINT configuracion_respaldo_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.configuracion_sistema
    ADD CONSTRAINT configuracion_sistema_pkey PRIMARY KEY (clave);

ALTER TABLE ONLY public.editoriales
    ADD CONSTRAINT editoriales_nombre_key UNIQUE (nombre);

ALTER TABLE ONLY public.editoriales
    ADD CONSTRAINT editoriales_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.estados_libro
    ADD CONSTRAINT estados_libro_nombre_key UNIQUE (nombre);

ALTER TABLE ONLY public.estados_libro
    ADD CONSTRAINT estados_libro_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.estados_multa
    ADD CONSTRAINT estados_multa_nombre_key UNIQUE (nombre);

ALTER TABLE ONLY public.estados_multa
    ADD CONSTRAINT estados_multa_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.estados_prestamo
    ADD CONSTRAINT estados_prestamo_nombre_key UNIQUE (nombre);

ALTER TABLE ONLY public.estados_prestamo
    ADD CONSTRAINT estados_prestamo_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.estados_reservacion
    ADD CONSTRAINT estados_reservacion_nombre_key UNIQUE (nombre);

ALTER TABLE ONLY public.estados_reservacion
    ADD CONSTRAINT estados_reservacion_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.estados_usuario
    ADD CONSTRAINT estados_usuario_nombre_key UNIQUE (nombre);

ALTER TABLE ONLY public.estados_usuario
    ADD CONSTRAINT estados_usuario_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.evidencia_dano
    ADD CONSTRAINT evidencia_dano_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.favoritos
    ADD CONSTRAINT favoritos_pkey PRIMARY KEY (usuario_id, libro_id);

ALTER TABLE ONLY public.idiomas
    ADD CONSTRAINT idiomas_codigo_iso_key UNIQUE (codigo_iso);

ALTER TABLE ONLY public.idiomas
    ADD CONSTRAINT idiomas_nombre_key UNIQUE (nombre);

ALTER TABLE ONLY public.idiomas
    ADD CONSTRAINT idiomas_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.libro_autores
    ADD CONSTRAINT libro_autores_pkey PRIMARY KEY (libro_id, autor_id);

ALTER TABLE ONLY public.libro_categorias
    ADD CONSTRAINT libro_categorias_pkey PRIMARY KEY (libro_id, categoria_id);

ALTER TABLE ONLY public.libros
    ADD CONSTRAINT libros_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.mensajes_chat
    ADD CONSTRAINT mensajes_chat_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.multas
    ADD CONSTRAINT multas_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.notificaciones
    ADD CONSTRAINT notificaciones_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.permisos
    ADD CONSTRAINT permisos_codigo_key UNIQUE (codigo);

ALTER TABLE ONLY public.permisos
    ADD CONSTRAINT permisos_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.prestamos
    ADD CONSTRAINT prestamos_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.proveedores
    ADD CONSTRAINT proveedores_nombre_key UNIQUE (nombre);

ALTER TABLE ONLY public.proveedores
    ADD CONSTRAINT proveedores_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.proveedores
    ADD CONSTRAINT proveedores_ruc_key UNIQUE (ruc);

ALTER TABLE ONLY public.registro_dano_detalle
    ADD CONSTRAINT registro_dano_detalle_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.registro_danos
    ADD CONSTRAINT registro_danos_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.registros_respaldo
    ADD CONSTRAINT registros_respaldo_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.reservaciones
    ADD CONSTRAINT reservaciones_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.rol_permisos
    ADD CONSTRAINT rol_permisos_pkey PRIMARY KEY (rol_id, permiso_id);

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_nombre_key UNIQUE (nombre);

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.sesiones_chat
    ADD CONSTRAINT sesiones_chat_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.sugerencias_adquisicion
    ADD CONSTRAINT sugerencias_adquisicion_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.suscripciones_disponibilidad
    ADD CONSTRAINT suscripciones_disponibilidad_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.suscripciones_disponibilidad
    ADD CONSTRAINT suscripciones_disponibilidad_usuario_id_libro_id_key UNIQUE (usuario_id, libro_id);

ALTER TABLE ONLY public.tipos_dano
    ADD CONSTRAINT tipos_dano_nombre_key UNIQUE (nombre);

ALTER TABLE ONLY public.tipos_dano
    ADD CONSTRAINT tipos_dano_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.tipos_notificacion
    ADD CONSTRAINT tipos_notificacion_nombre_key UNIQUE (nombre);

ALTER TABLE ONLY public.tipos_notificacion
    ADD CONSTRAINT tipos_notificacion_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.tokens_invalidos
    ADD CONSTRAINT tokens_invalidos_jti_key UNIQUE (jti);

ALTER TABLE ONLY public.tokens_invalidos
    ADD CONSTRAINT tokens_invalidos_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.usuario_roles
    ADD CONSTRAINT usuario_roles_pkey PRIMARY KEY (usuario_id, rol_id);

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT usuarios_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.verificaciones_correo
    ADD CONSTRAINT verificaciones_correo_pkey PRIMARY KEY (id);

CREATE INDEX idx_backup_programacion_activo ON public.backup_programacion USING btree (activo, creado_en DESC);

CREATE INDEX idx_backup_programacion_creado_en ON public.backup_programacion USING btree (creado_en DESC);

CREATE INDEX idx_backup_programacion_formato ON public.backup_programacion USING btree (formato);

CREATE INDEX idx_backup_programacion_tablas_prog ON public.backup_programacion_tablas USING btree (programacion_id);

CREATE INDEX idx_backups_creado_en ON public.backups USING btree (creado_en DESC);

CREATE INDEX idx_backups_creadopor ON public.backups USING btree (creado_por);

CREATE INDEX idx_backups_desde_hasta ON public.backups USING btree (desde, hasta);

CREATE INDEX idx_backups_estado ON public.backups USING btree (estado);

CREATE INDEX idx_backups_tablas_backup ON public.backups_tablas USING btree (backup_id);

CREATE INDEX idx_bitacora_fecha_hora ON public.bitacora_auditoria USING btree (fecha_hora DESC);

CREATE INDEX idx_libros_anio ON public.libros USING btree (anio_publicacion);

CREATE INDEX idx_libros_editorial_stock ON public.libros USING btree (editorial_id, stock_disponible);

CREATE UNIQUE INDEX idx_libros_isbn ON public.libros USING btree (isbn);

CREATE INDEX idx_libros_proveedor ON public.libros USING btree (proveedor_id);

CREATE INDEX idx_libros_titulo_trgm ON public.libros USING gin (titulo public.gin_trgm_ops);

CREATE INDEX idx_libros_ubicacion_trgm ON public.libros USING gin (ubicacion_fisica public.gin_trgm_ops);

CREATE INDEX idx_mensajes_chat_sesion ON public.mensajes_chat USING btree (sesion_id);

CREATE INDEX idx_notificaciones_usuario ON public.notificaciones USING btree (usuario_id);

CREATE INDEX idx_prestamos_estado_fecha_devolucion ON public.prestamos USING btree (estado_prestamo_id, fecha_devolucion_estimada);

CREATE INDEX idx_prestamos_estado_fecha_estimada ON public.prestamos USING btree (estado_prestamo_id, fecha_devolucion_estimada) WHERE (fecha_devolucion_real IS NULL);

CREATE INDEX idx_prestamos_fecha_prestamo ON public.prestamos USING btree (fecha_prestamo);

CREATE INDEX idx_prestamos_usuario_id ON public.prestamos USING btree (usuario_id);

CREATE INDEX idx_proveedores_activo ON public.proveedores USING btree (activo);

CREATE INDEX idx_proveedores_nombre ON public.proveedores USING btree (nombre);

CREATE INDEX idx_registros_respaldo_iniciado_en ON public.registros_respaldo USING btree (iniciado_en DESC);

CREATE INDEX idx_registros_respaldo_tipo ON public.registros_respaldo USING btree (tipo);

CREATE INDEX idx_sugerencia_proveedor ON public.sugerencias_adquisicion USING btree (proveedor_id);

CREATE INDEX idx_susc_libro ON public.suscripciones_disponibilidad USING btree (libro_id);

CREATE INDEX idx_susc_usuario ON public.suscripciones_disponibilidad USING btree (usuario_id);

CREATE UNIQUE INDEX idx_usuarios_correo ON public.usuarios USING btree (correo);

CREATE INDEX idx_usuarios_creado_por ON public.usuarios USING btree (creado_por);

CREATE UNIQUE INDEX idx_usuarios_credencial_qr_token ON public.usuarios USING btree (credencial_qr_token);

CREATE UNIQUE INDEX uq_registro_danos_prestamo ON public.registro_danos USING btree (prestamo_id);

CREATE FUNCTION public.fn_auditoria_generica() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
    v_usuario_id  BIGINT;
    v_registro_id BIGINT;
    v_old_data    JSONB;
    v_new_data    JSONB;
    v_detalles    JSONB;
BEGIN
    -- Usuario de sesión: mismo patrón defensivo que las políticas RLS de
    -- db/roles-privilegios.sql (sección 7) — current_setting(..., true)
    -- devuelve NULL en vez de lanzar error cuando la variable no fue
    -- fijada con SET LOCAL, en lugar de reinventar el manejo de NULL aquí.
    -- Con una conexión directa del DBA (sin paso por el backend) esta
    -- variable nunca existe, así que usuario_id queda NULL a propósito
    -- (ver verificación al final de este archivo) en vez de fallar el
    -- INSERT/UPDATE/DELETE original.
    -- FIX: NULLIF evita "invalid input syntax for type bigint: \"\""
    -- cuando la variable existe pero está vacía (p.ej. verificación de correo
    -- sin usuario autenticado donde AuditoriaAspect no llegó a setearla).
    v_usuario_id := NULLIF(current_setting('app.current_user_id', true), '')::BIGINT;

    -- registro_id: la inmensa mayoría de las tablas tiene una PK simple
    -- `id`. usuario_roles y rol_permisos son la excepción (PK compuesta,
    -- ver V2__rbac_normalizado.sql y db/schema.sql — no existe columna
    -- `id`). En vez de listar por nombre qué tablas tienen `id` y cuáles
    -- no (frágil ante tablas nuevas), se extrae el campo desde la
    -- representación JSON de la fila: to_jsonb(...)->>'id' devuelve NULL
    -- sin error cuando la clave no existe, que es exactamente lo que
    -- queremos para esas dos tablas de PK compuesta (el detalle completo
    -- de la fila, incluida la clave compuesta, igual queda en `detalles`).
    IF TG_OP = 'DELETE' THEN
        v_registro_id := (to_jsonb(OLD) ->> 'id')::BIGINT;
    ELSE
        v_registro_id := (to_jsonb(NEW) ->> 'id')::BIGINT;
    END IF;

    -- Serialización de la fila. Para UPDATE se guardan AMBAS versiones
    -- (antes/después) para que el diff sea reconstruible; guardar solo la
    -- fila nueva perdería qué cambió exactamente, que es el punto central
    -- de una bitácora de auditoría.
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        v_new_data := to_jsonb(NEW);
    END IF;
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        v_old_data := to_jsonb(OLD);
    END IF;

    -- REGLA DURA DE ESTE PROYECTO (ver db/roles-privilegios.sql, GRANT/
    -- REVOKE de password_hash en toda la sección 5): password_hash nunca
    -- se expone a ningún rol ni salida, ni siquiera de lectura interna.
    -- Este trigger corre con los privilegios del dueño de la función
    -- (normalmente el owner de la tabla / el DBA), por lo que SÍ tiene
    -- acceso crudo a password_hash vía NEW/OLD — se elimina explícitamente
    -- del JSON antes de guardarlo, para que la bitácora tampoco se
    -- convierta en una fuga lateral del hash.
    IF TG_TABLE_NAME = 'usuarios' THEN
        v_old_data := v_old_data - 'password_hash';
        v_new_data := v_new_data - 'password_hash';
    END IF;

    IF TG_OP = 'INSERT' THEN
        v_detalles := v_new_data;
    ELSIF TG_OP = 'UPDATE' THEN
        v_detalles := jsonb_build_object('antes', v_old_data, 'despues', v_new_data);
    ELSE -- DELETE
        v_detalles := v_old_data;
    END IF;

    -- TG_OP ya viene como 'INSERT'/'UPDATE'/'DELETE', los mismos literales
    -- que acepta el CHECK de tipo_operacion (V2__rbac_normalizado.sql /
    -- V7__ampliar_tipos_bitacora_auditoria.sql) — no hace falta traducirlo.
    -- ip_origen queda NULL: un trigger de BD no tiene acceso a la petición
    -- HTTP que originó el cambio. Ese dato solo lo captura el backend
    -- (AuthService, en los eventos LOGIN_OK/LOGIN_FAIL/LOGOUT que inserta
    -- manualmente) — este trigger es el complemento a nivel de BD para el
    -- resto de operaciones DML, no un reemplazo de esa captura.
    INSERT INTO bitacora_auditoria
        (usuario_id, tipo_operacion, tabla_afectada, registro_id, detalles, ip_origen)
    VALUES
        (v_usuario_id, TG_OP, TG_TABLE_NAME, v_registro_id, v_detalles::TEXT, NULL);

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION public.fn_listar_prestamos_activos_por_usuario(p_usuario_id bigint) RETURNS TABLE(prestamo_id bigint, libro_titulo character varying, libro_isbn character varying, fecha_prestamo timestamp with time zone, fecha_devolucion_estimada timestamp with time zone, dias_restantes integer, estado_nombre character varying)
    LANGUAGE sql STABLE
    AS $$
    SELECT
        p.id,
        l.titulo,
        l.isbn,
        p.fecha_prestamo,
        p.fecha_devolucion_estimada,
        (p.fecha_devolucion_estimada::date - NOW()::date)::INTEGER,
        ep.nombre
    FROM prestamos p
    JOIN libros l ON l.id = p.libro_id
    JOIN estados_prestamo ep ON ep.id = p.estado_prestamo_id
    WHERE p.usuario_id = p_usuario_id
      AND ep.nombre <> 'DEVUELTO'
    ORDER BY p.fecha_devolucion_estimada ASC;
$$;

CREATE FUNCTION public.fn_pagos_recientes(p_limit integer DEFAULT 5) RETURNS TABLE(multa_id bigint, monto_pagado numeric, fecha_pagada timestamp with time zone, usuario_correo character varying, usuario_nombre character varying, libro_titulo character varying)
    LANGUAGE sql STABLE
    AS $$
    SELECT
        m.id AS multa_id,
        m.monto_pagado,
        m.fecha_pagada,
        u.correo AS usuario_correo,
        u.nombre || ' ' || u.apellido AS usuario_nombre,
        l.titulo AS libro_titulo
    FROM multas m
    JOIN prestamos p ON p.id = m.prestamo_id
    JOIN usuarios u ON u.id = p.usuario_id
    JOIN libros l ON l.id = p.libro_id
    WHERE m.estado_multa_id = (SELECT id FROM estados_multa WHERE nombre = 'PAGADA')
    ORDER BY m.fecha_pagada DESC
    LIMIT p_limit;
$$;

CREATE FUNCTION public.fn_reporte_categorias_demandadas(p_limite integer DEFAULT NULL::integer, p_desde timestamp with time zone DEFAULT NULL::timestamp with time zone, p_hasta timestamp with time zone DEFAULT NULL::timestamp with time zone) RETURNS TABLE(categoria_id integer, categoria_nombre character varying, total_prestamos bigint, porcentaje numeric)
    LANGUAGE sql STABLE
    AS $$
    WITH total_general AS (SELECT COUNT(*) AS total FROM prestamos pr WHERE (p_desde IS NULL OR pr.fecha_prestamo >= p_desde) AND (p_hasta IS NULL OR pr.fecha_prestamo <= p_hasta)),
    prestamos_por_categoria AS (
        SELECT c.id AS categoria_id, c.nombre AS categoria_nombre, COUNT(*) AS total_prestamos
        FROM prestamos pr JOIN libros l ON l.id=pr.libro_id JOIN libro_categorias lc ON lc.libro_id=l.id JOIN categorias c ON c.id=lc.categoria_id
        WHERE (p_desde IS NULL OR pr.fecha_prestamo >= p_desde) AND (p_hasta IS NULL OR pr.fecha_prestamo <= p_hasta)
        GROUP BY c.id, c.nombre)
    SELECT ppc.categoria_id, ppc.categoria_nombre, ppc.total_prestamos,
           ROUND(ppc.total_prestamos * 100.0 / NULLIF(tg.total,0),1) AS porcentaje
    FROM prestamos_por_categoria ppc CROSS JOIN total_general tg
    ORDER BY ppc.total_prestamos DESC;
$$;

CREATE FUNCTION public.fn_reporte_indice_morosidad(p_limite integer DEFAULT 10) RETURNS TABLE(usuario_id bigint, nombre character varying, apellido character varying, correo character varying, monto_total_adeudado numeric, cantidad_multas_pendientes bigint, dias_atraso_promedio numeric)
    LANGUAGE sql STABLE
    AS $$
    SELECT
        u.id,
        u.nombre,
        u.apellido,
        u.correo,
        SUM(m.monto)::NUMERIC(10,2) AS monto_total_adeudado,
        COUNT(m.id) AS cantidad_multas_pendientes,
        ROUND(
            AVG(
                GREATEST(
                    0,
                    EXTRACT(DAY FROM (COALESCE(p.fecha_devolucion_real, NOW()) - p.fecha_devolucion_estimada))
                )
            )::NUMERIC,
            1
        ) AS dias_atraso_promedio
    FROM multas m
    JOIN estados_multa em ON em.id = m.estado_multa_id
    JOIN prestamos p ON p.id = m.prestamo_id
    JOIN usuarios u ON u.id = p.usuario_id
    WHERE em.nombre = 'PENDIENTE'
    GROUP BY u.id, u.nombre, u.apellido, u.correo
    ORDER BY monto_total_adeudado DESC
    LIMIT COALESCE(p_limite, 10);
$$;

CREATE FUNCTION public.fn_reporte_inventario(p_categoria_id integer DEFAULT NULL::integer, p_estado_stock text DEFAULT NULL::text, p_busqueda text DEFAULT NULL::text) RETURNS TABLE(libro_id bigint, titulo character varying, isbn character varying, autor_nombre text, categoria_nombre text, stock_total smallint, stock_disponible smallint, estado_disponibilidad text)
    LANGUAGE sql STABLE
    AS $$
    SELECT
        l.id AS libro_id,
        l.titulo,
        l.isbn,
        COALESCE(string_agg(DISTINCT a.nombre, ', ' ORDER BY a.nombre), 'Sin autor') AS autor_nombre,
        COALESCE(string_agg(DISTINCT c.nombre, ', ' ORDER BY c.nombre), 'Sin categoría') AS categoria_nombre,
        l.stock_total,
        l.stock_disponible,
        CASE
            WHEN l.stock_disponible = 0 THEN 'Agotado'
            WHEN l.stock_disponible = 1 THEN 'Baja disponibilidad'
            ELSE 'Disponible'
        END AS estado_disponibilidad
    FROM libros l
    LEFT JOIN libro_autores la ON la.libro_id = l.id
    LEFT JOIN autores a ON a.id = la.autor_id
    LEFT JOIN libro_categorias lc ON lc.libro_id = l.id
    LEFT JOIN categorias c ON c.id = lc.categoria_id
    WHERE (p_categoria_id IS NULL OR lc.categoria_id = p_categoria_id)
      AND (p_busqueda IS NULL OR l.titulo ILIKE '%' || p_busqueda || '%' OR l.isbn ILIKE '%' || p_busqueda || '%'
           OR a.nombre ILIKE '%' || p_busqueda || '%')
    GROUP BY l.id, l.titulo, l.isbn, l.stock_total, l.stock_disponible
    HAVING (p_estado_stock IS NULL
            OR (p_estado_stock = 'agotado' AND l.stock_disponible = 0)
            OR (p_estado_stock = 'baja' AND l.stock_disponible = 1 AND l.stock_disponible > 0)
            OR (p_estado_stock = 'disponible' AND l.stock_disponible > 1))
    ORDER BY l.stock_disponible ASC, l.titulo ASC;
$$;

CREATE FUNCTION public.fn_reporte_inventario(p_categoria_id integer DEFAULT NULL::integer, p_estado_stock text DEFAULT NULL::text, p_busqueda text DEFAULT NULL::text, p_editorial_id integer DEFAULT NULL::integer, p_proveedor_id integer DEFAULT NULL::integer, p_estado_libro_id integer DEFAULT NULL::integer, p_idioma_id integer DEFAULT NULL::integer, p_anio_desde smallint DEFAULT NULL::smallint, p_anio_hasta smallint DEFAULT NULL::smallint, p_stock_total_min smallint DEFAULT NULL::smallint, p_stock_total_max smallint DEFAULT NULL::smallint, p_stock_disp_min smallint DEFAULT NULL::smallint, p_stock_disp_max smallint DEFAULT NULL::smallint, p_ubicacion text DEFAULT NULL::text) RETURNS TABLE(libro_id bigint, titulo character varying, isbn character varying, autor_nombre text, categoria_nombre text, editorial_nombre text, proveedor_nombre text, idioma_nombre text, estado_libro_nombre text, anio_publicacion smallint, ubicacion_fisica character varying, stock_total smallint, stock_disponible smallint, estado_disponibilidad text)
    LANGUAGE sql STABLE
    AS $$
    SELECT l.id, l.titulo, l.isbn,
           COALESCE(string_agg(DISTINCT a.nombre, ', ' ORDER BY a.nombre), 'Sin autor'),
           COALESCE(string_agg(DISTINCT c.nombre, ', ' ORDER BY c.nombre), 'Sin categoría'),
           e.nombre, prov.nombre, idi.nombre, el.nombre,
           l.anio_publicacion, l.ubicacion_fisica,
           l.stock_total, l.stock_disponible,
           CASE WHEN l.stock_disponible=0 THEN 'Agotado' WHEN l.stock_disponible=1 THEN 'Baja disponibilidad' ELSE 'Disponible' END
    FROM libros l
    LEFT JOIN libro_autores la ON la.libro_id=l.id LEFT JOIN autores a ON a.id=la.autor_id
    LEFT JOIN libro_categorias lc ON lc.libro_id=l.id LEFT JOIN categorias c ON c.id=lc.categoria_id
    LEFT JOIN editoriales e ON e.id=l.editorial_id
    LEFT JOIN proveedores prov ON prov.id=l.proveedor_id
    LEFT JOIN idiomas idi ON idi.id=l.idioma_id
    LEFT JOIN estados_libro el ON el.id=l.estado_id
    WHERE (p_categoria_id IS NULL OR lc.categoria_id=p_categoria_id)
      AND (p_editorial_id IS NULL OR l.editorial_id=p_editorial_id)
      AND (p_proveedor_id IS NULL OR l.proveedor_id=p_proveedor_id)
      AND (p_estado_libro_id IS NULL OR l.estado_id=p_estado_libro_id)
      AND (p_idioma_id IS NULL OR l.idioma_id=p_idioma_id)
      AND (p_anio_desde IS NULL OR l.anio_publicacion >= p_anio_desde)
      AND (p_anio_hasta IS NULL OR l.anio_publicacion <= p_anio_hasta)
      AND (p_stock_total_min IS NULL OR l.stock_total >= p_stock_total_min)
      AND (p_stock_total_max IS NULL OR l.stock_total <= p_stock_total_max)
      AND (p_stock_disp_min IS NULL OR l.stock_disponible >= p_stock_disp_min)
      AND (p_stock_disp_max IS NULL OR l.stock_disponible <= p_stock_disp_max)
      AND (p_ubicacion IS NULL OR l.ubicacion_fisica ILIKE '%' || p_ubicacion || '%')
      AND (p_busqueda IS NULL OR l.titulo ILIKE '%' || p_busqueda || '%'
           OR l.isbn ILIKE '%' || p_busqueda || '%' OR a.nombre ILIKE '%' || p_busqueda || '%'
           OR e.nombre ILIKE '%' || p_busqueda || '%' OR prov.nombre ILIKE '%' || p_busqueda || '%')
    GROUP BY l.id, l.titulo, l.isbn, l.stock_total, l.stock_disponible,
             e.nombre, prov.nombre, idi.nombre, el.nombre, l.anio_publicacion, l.ubicacion_fisica
    HAVING (p_estado_stock IS NULL
            OR (p_estado_stock='agotado' AND l.stock_disponible=0)
            OR (p_estado_stock='baja' AND l.stock_disponible=1)
            OR (p_estado_stock='disponible' AND l.stock_disponible>1))
    ORDER BY l.stock_disponible ASC, l.titulo ASC;
$$;

CREATE FUNCTION public.fn_reporte_libros_mas_prestados(p_limite integer DEFAULT 10, p_desde timestamp with time zone DEFAULT NULL::timestamp with time zone, p_hasta timestamp with time zone DEFAULT NULL::timestamp with time zone) RETURNS TABLE(libro_id bigint, titulo character varying, isbn character varying, total_prestamos bigint)
    LANGUAGE sql STABLE
    AS $$
    SELECT
        l.id,
        l.titulo,
        l.isbn,
        COUNT(*) AS total_prestamos
    FROM prestamos p
    JOIN libros l ON l.id = p.libro_id
    WHERE (p_desde IS NULL OR p.fecha_prestamo >= p_desde)
      AND (p_hasta IS NULL OR p.fecha_prestamo <= p_hasta)
    GROUP BY l.id, l.titulo, l.isbn
    ORDER BY total_prestamos DESC
    LIMIT p_limite;
$$;

CREATE FUNCTION public.fn_reporte_libros_mas_prestados_detallado(p_limite integer DEFAULT NULL::integer, p_desde timestamp with time zone DEFAULT NULL::timestamp with time zone, p_hasta timestamp with time zone DEFAULT NULL::timestamp with time zone, p_categoria_id integer DEFAULT NULL::integer) RETURNS TABLE(libro_id bigint, titulo character varying, isbn character varying, autor_nombre text, categoria_nombre text, total_prestamos bigint, porcentaje numeric)
    LANGUAGE sql STABLE
    AS $$
    WITH total_general AS (SELECT COUNT(*) AS total FROM prestamos pr WHERE (p_desde IS NULL OR pr.fecha_prestamo >= p_desde) AND (p_hasta IS NULL OR pr.fecha_prestamo <= p_hasta) AND (p_categoria_id IS NULL OR EXISTS (SELECT 1 FROM libro_categorias lc WHERE lc.libro_id=pr.libro_id AND lc.categoria_id=p_categoria_id))),
    conteo AS (
        SELECT l.id AS libro_id, l.titulo, l.isbn,
               COALESCE(string_agg(DISTINCT a.nombre, ', ' ORDER BY a.nombre), 'Sin autor') AS autor_nombre,
               COALESCE(string_agg(DISTINCT c.nombre, ', ' ORDER BY c.nombre), 'Sin categoria') AS categoria_nombre,
               COUNT(*) AS total_prestamos
        FROM prestamos pr JOIN libros l ON l.id=pr.libro_id
        LEFT JOIN libro_autores la ON la.libro_id=l.id LEFT JOIN autores a ON a.id=la.autor_id
        LEFT JOIN libro_categorias lc ON lc.libro_id=l.id LEFT JOIN categorias c ON c.id=lc.categoria_id
        WHERE (p_desde IS NULL OR pr.fecha_prestamo >= p_desde) AND (p_hasta IS NULL OR pr.fecha_prestamo <= p_hasta)
          AND (p_categoria_id IS NULL OR EXISTS (SELECT 1 FROM libro_categorias lc2 WHERE lc2.libro_id=pr.libro_id AND lc2.categoria_id=p_categoria_id))
        GROUP BY l.id, l.titulo, l.isbn)
    SELECT c.libro_id, c.titulo, c.isbn, c.autor_nombre, c.categoria_nombre, c.total_prestamos,
           ROUND(c.total_prestamos * 100.0 / NULLIF(tg.total,0),1) AS porcentaje
    FROM conteo c CROSS JOIN total_general tg ORDER BY c.total_prestamos DESC;
$$;

CREATE FUNCTION public.fn_reporte_prestamos_vencidos(p_dias_atraso_min integer DEFAULT NULL::integer, p_busqueda text DEFAULT NULL::text, p_dias_atraso_max integer DEFAULT NULL::integer) RETURNS TABLE(prestamo_id bigint, usuario_nombre text, usuario_correo character varying, libro_titulo character varying, libro_isbn character varying, fecha_devolucion_estimada timestamp with time zone, dias_atraso bigint, monto_multa_estimada numeric)
    LANGUAGE sql STABLE
    AS $$
    SELECT p.id, u.nombre || ' ' || u.apellido, u.correo, l.titulo, l.isbn, p.fecha_devolucion_estimada,
           EXTRACT(DAY FROM NOW() - p.fecha_devolucion_estimada)::BIGINT AS dias_atraso,
           (EXTRACT(DAY FROM NOW() - p.fecha_devolucion_estimada) * COALESCE((SELECT valor::NUMERIC FROM configuracion_sistema WHERE clave='monto_multa_diaria'), 1))::NUMERIC AS monto_multa_estimada
    FROM prestamos p JOIN usuarios u ON u.id=p.usuario_id JOIN libros l ON l.id=p.libro_id
    JOIN estados_prestamo ep ON ep.id=p.estado_prestamo_id
    WHERE ep.nombre IN ('ACTIVO','RENOVADO') AND p.fecha_devolucion_estimada < NOW()
      AND (p_dias_atraso_min IS NULL OR EXTRACT(DAY FROM NOW() - p.fecha_devolucion_estimada) >= p_dias_atraso_min)
      AND (p_dias_atraso_max IS NULL OR EXTRACT(DAY FROM NOW() - p.fecha_devolucion_estimada) <= p_dias_atraso_max)
      AND (p_busqueda IS NULL OR u.nombre ILIKE '%' || p_busqueda || '%' OR u.correo ILIKE '%' || p_busqueda || '%' OR l.titulo ILIKE '%' || p_busqueda || '%' OR l.isbn ILIKE '%' || p_busqueda || '%')
    ORDER BY dias_atraso DESC;
$$;

CREATE FUNCTION public.fn_reporte_resumen_financiero_multas(p_desde timestamp with time zone DEFAULT NULL::timestamp with time zone, p_hasta timestamp with time zone DEFAULT NULL::timestamp with time zone) RETURNS TABLE(total_recaudado numeric, total_pendiente numeric)
    LANGUAGE sql STABLE
    AS $$
    SELECT
        COALESCE(SUM(m.monto) FILTER (WHERE em.nombre = 'PAGADA'), 0)::NUMERIC(12,2)
            AS total_recaudado,
        COALESCE(SUM(m.monto) FILTER (WHERE em.nombre = 'PENDIENTE'), 0)::NUMERIC(12,2)
            AS total_pendiente
    FROM multas m
    JOIN estados_multa em ON em.id = m.estado_multa_id
    WHERE (p_desde IS NULL OR m.fecha_generada >= p_desde)
      AND (p_hasta IS NULL OR m.fecha_generada <= p_hasta);
$$;

CREATE FUNCTION public.fn_reporte_uso_por_periodo(p_granularidad text DEFAULT 'dia'::text, p_desde timestamp with time zone DEFAULT NULL::timestamp with time zone, p_hasta timestamp with time zone DEFAULT NULL::timestamp with time zone) RETURNS TABLE(periodo timestamp with time zone, total_prestamos bigint, total_devoluciones bigint)
    LANGUAGE sql STABLE
    AS $$
    WITH campo AS (
        SELECT CASE p_granularidad
                   WHEN 'semana' THEN 'week'
                   WHEN 'mes'    THEN 'month'
                   ELSE 'day'
               END AS valor
    ),
    prestamos_agg AS (
        SELECT
            date_trunc((SELECT valor FROM campo), p.fecha_prestamo) AS periodo,
            COUNT(*) AS total
        FROM prestamos p
        WHERE (p_desde IS NULL OR p.fecha_prestamo >= p_desde)
          AND (p_hasta IS NULL OR p.fecha_prestamo <= p_hasta)
        GROUP BY 1
    ),
    devoluciones_agg AS (
        SELECT
            date_trunc((SELECT valor FROM campo), p.fecha_devolucion_real) AS periodo,
            COUNT(*) AS total
        FROM prestamos p
        WHERE p.fecha_devolucion_real IS NOT NULL
          AND (p_desde IS NULL OR p.fecha_devolucion_real >= p_desde)
          AND (p_hasta IS NULL OR p.fecha_devolucion_real <= p_hasta)
        GROUP BY 1
    )
    SELECT
        COALESCE(pa.periodo, da.periodo) AS periodo,
        COALESCE(pa.total, 0) AS total_prestamos,
        COALESCE(da.total, 0) AS total_devoluciones
    FROM prestamos_agg pa
    FULL OUTER JOIN devoluciones_agg da ON da.periodo = pa.periodo
    ORDER BY periodo;
$$;

CREATE FUNCTION public.set_actualizado_en() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.actualizado_en = NOW();
    RETURN NEW;
END;
$$;

CREATE FUNCTION public.sp_anular_multa(p_multa_id bigint, p_motivo character varying, p_rol_ejecutor character varying, OUT o_multa_id bigint, OUT o_usuario_desbloqueado boolean) RETURNS record
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_estado_multa_id           INTEGER;
    v_estado_pendiente_id       INTEGER;
    v_estado_anulada_id         INTEGER;
    v_estado_activo_usuario_id  INTEGER;
    v_usuario_id                BIGINT;
    v_otras_pendientes          INTEGER;
BEGIN
    IF p_rol_ejecutor <> 'GERENTE' AND p_rol_ejecutor <> 'ADMIN' THEN
        RAISE EXCEPTION 'Solo GERENTE o ADMIN puede anular multas' USING ERRCODE = 'LB422';
    END IF;

    SELECT m.estado_multa_id, p.usuario_id
      INTO v_estado_multa_id, v_usuario_id
      FROM multas m
      JOIN prestamos p ON p.id = m.prestamo_id
     WHERE m.id = p_multa_id
     FOR UPDATE OF m;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'La multa % no existe', p_multa_id USING ERRCODE = 'LB404';
    END IF;

    SELECT id INTO v_estado_pendiente_id FROM estados_multa WHERE nombre = 'PENDIENTE';

    IF v_estado_multa_id <> v_estado_pendiente_id THEN
        RAISE EXCEPTION 'La multa % no esta pendiente, no se puede anular', p_multa_id USING ERRCODE = 'LB409';
    END IF;

    SELECT id INTO v_estado_anulada_id FROM estados_multa WHERE nombre = 'ANULADA';

    UPDATE multas
       SET estado_multa_id = v_estado_anulada_id,
           observaciones = p_motivo
     WHERE id = p_multa_id;

    o_multa_id := p_multa_id;

    SELECT count(*) INTO v_otras_pendientes
      FROM multas m2
      JOIN prestamos p2 ON p2.id = m2.prestamo_id
     WHERE p2.usuario_id = v_usuario_id
       AND m2.estado_multa_id = v_estado_pendiente_id
       AND m2.id <> p_multa_id;

    IF v_otras_pendientes = 0 THEN
        SELECT id INTO v_estado_activo_usuario_id FROM estados_usuario WHERE nombre = 'ACTIVO';
        UPDATE usuarios SET estado_id = v_estado_activo_usuario_id WHERE id = v_usuario_id;
        o_usuario_desbloqueado := TRUE;
    ELSE
        o_usuario_desbloqueado := FALSE;
    END IF;

    INSERT INTO bitacora_auditoria (usuario_id, tipo_operacion, tabla_afectada, registro_id, detalles)
    VALUES (NULL, 'UPDATE', 'multas', p_multa_id, 'Multa anulada: ' || p_motivo);
END;
$$;

CREATE FUNCTION public.sp_crear_prestamo(p_usuario_id bigint, p_libro_id bigint, p_bibliotecario_id bigint, p_dias_prestamo integer) RETURNS bigint
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_estado_usuario_id     INTEGER;
    v_estado_bloqueado_id   INTEGER;
    v_stock_disponible      SMALLINT;
    v_estado_activo_prestamo_id INTEGER;
    v_prestamo_id           BIGINT;
BEGIN
    SELECT id INTO v_estado_bloqueado_id
      FROM estados_usuario
     WHERE nombre = 'BLOQUEADO_POR_MULTA';

    SELECT estado_id INTO v_estado_usuario_id
      FROM usuarios
     WHERE id = p_usuario_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'El usuario % no existe', p_usuario_id USING ERRCODE = 'LB404';
    END IF;

    IF v_estado_usuario_id = v_estado_bloqueado_id THEN
        RAISE EXCEPTION 'El usuario % esta bloqueado por multas pendientes y no puede solicitar prestamos', p_usuario_id
            USING ERRCODE = 'LB422';
    END IF;

    SELECT stock_disponible INTO v_stock_disponible
      FROM libros
     WHERE id = p_libro_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'El libro % no existe', p_libro_id USING ERRCODE = 'LB404';
    END IF;

    IF v_stock_disponible <= 0 THEN
        RAISE EXCEPTION 'El libro % no tiene stock disponible', p_libro_id USING ERRCODE = 'LB422';
    END IF;

    SELECT id INTO v_estado_activo_prestamo_id
      FROM estados_prestamo
     WHERE nombre = 'ACTIVO';

    UPDATE libros
       SET stock_disponible = stock_disponible - 1
     WHERE id = p_libro_id;

    INSERT INTO prestamos (
        usuario_id, libro_id, bibliotecario_id,
        fecha_prestamo, fecha_devolucion_estimada, estado_prestamo_id
    ) VALUES (
        p_usuario_id, p_libro_id, p_bibliotecario_id,
        NOW(), NOW() + make_interval(days => p_dias_prestamo), v_estado_activo_prestamo_id
    )
    RETURNING id INTO v_prestamo_id;

    RETURN v_prestamo_id;
END;
$$;

CREATE FUNCTION public.sp_expirar_reservaciones_vencidas(p_ahora timestamp with time zone DEFAULT now()) RETURNS integer
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_estado_pendiente_id   INTEGER;
    v_estado_lista_id       INTEGER;
    v_estado_expirada_id    INTEGER;
    v_filas_afectadas       INTEGER;
BEGIN
    SELECT id INTO v_estado_pendiente_id FROM estados_reservacion WHERE nombre = 'PENDIENTE';
    SELECT id INTO v_estado_lista_id     FROM estados_reservacion WHERE nombre = 'LISTA_PARA_RETIRO';
    SELECT id INTO v_estado_expirada_id  FROM estados_reservacion WHERE nombre = 'EXPIRADA';

    UPDATE reservaciones
       SET estado_reservacion_id = v_estado_expirada_id
     WHERE estado_reservacion_id IN (v_estado_pendiente_id, v_estado_lista_id)
       AND fecha_limite_retiro < p_ahora;

    GET DIAGNOSTICS v_filas_afectadas = ROW_COUNT;

    RETURN v_filas_afectadas;
END;
$$;

CREATE FUNCTION public.sp_pagar_multa(p_multa_id bigint, OUT o_multa_id bigint, OUT o_usuario_desbloqueado boolean) RETURNS record
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_estado_multa_id           INTEGER;
    v_estado_pendiente_id       INTEGER;
    v_estado_pagada_id          INTEGER;
    v_estado_activo_usuario_id  INTEGER;
    v_usuario_id                BIGINT;
    v_otras_pendientes          INTEGER;
    v_ahora                     TIMESTAMPTZ := NOW();
BEGIN
    SELECT m.estado_multa_id, p.usuario_id
      INTO v_estado_multa_id, v_usuario_id
      FROM multas m
      JOIN prestamos p ON p.id = m.prestamo_id
     WHERE m.id = p_multa_id
     FOR UPDATE OF m;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'La multa % no existe', p_multa_id USING ERRCODE = 'LB404';
    END IF;

    SELECT id INTO v_estado_pendiente_id FROM estados_multa WHERE nombre = 'PENDIENTE';

    IF v_estado_multa_id <> v_estado_pendiente_id THEN
        RAISE EXCEPTION 'La multa % no esta pendiente de pago', p_multa_id USING ERRCODE = 'LB409';
    END IF;

    SELECT id INTO v_estado_pagada_id FROM estados_multa WHERE nombre = 'PAGADA';

    UPDATE multas
       SET estado_multa_id = v_estado_pagada_id,
           fecha_pagada = v_ahora
     WHERE id = p_multa_id;

    o_multa_id := p_multa_id;

    SELECT count(*) INTO v_otras_pendientes
      FROM multas m2
      JOIN prestamos p2 ON p2.id = m2.prestamo_id
     WHERE p2.usuario_id = v_usuario_id
       AND m2.estado_multa_id = v_estado_pendiente_id
       AND m2.id <> p_multa_id;

    IF v_otras_pendientes = 0 THEN
        SELECT id INTO v_estado_activo_usuario_id FROM estados_usuario WHERE nombre = 'ACTIVO';
        UPDATE usuarios SET estado_id = v_estado_activo_usuario_id WHERE id = v_usuario_id;
        o_usuario_desbloqueado := TRUE;
    ELSE
        o_usuario_desbloqueado := FALSE;
    END IF;
END;
$$;

CREATE FUNCTION public.sp_pago_parcial_multa(p_multa_id bigint, p_monto_pagado numeric, OUT o_multa_id bigint, OUT o_estado character varying, OUT o_saldo_restante numeric, OUT o_usuario_desbloqueado boolean) RETURNS record
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_estado_multa_id           INTEGER;
    v_estado_pendiente_id       INTEGER;
    v_estado_pagada_id          INTEGER;
    v_estado_activo_usuario_id  INTEGER;
    v_monto_total               NUMERIC(8,2);
    v_monto_pagado_actual       NUMERIC(8,2);
    v_usuario_id                BIGINT;
    v_nuevo_pagado              NUMERIC(8,2);
    v_otras_pendientes          INTEGER;
    v_ahora                     TIMESTAMPTZ := NOW();
BEGIN
    SELECT m.estado_multa_id, m.monto, m.monto_pagado, p.usuario_id
      INTO v_estado_multa_id, v_monto_total, v_monto_pagado_actual, v_usuario_id
      FROM multas m
      JOIN prestamos p ON p.id = m.prestamo_id
     WHERE m.id = p_multa_id
       FOR UPDATE OF m;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'La multa % no existe', p_multa_id USING ERRCODE = 'LB404';
    END IF;

    SELECT id INTO v_estado_pendiente_id FROM estados_multa WHERE nombre = 'PENDIENTE';

    IF v_estado_multa_id <> v_estado_pendiente_id THEN
        RAISE EXCEPTION 'La multa % no esta pendiente de pago', p_multa_id USING ERRCODE = 'LB409';
    END IF;

    IF p_monto_pagado IS NULL OR p_monto_pagado <= 0 THEN
        RAISE EXCEPTION 'El monto a pagar debe ser mayor a cero' USING ERRCODE = 'LB400';
    END IF;

    v_nuevo_pagado := v_monto_pagado_actual + p_monto_pagado;

    SELECT id INTO v_estado_pagada_id FROM estados_multa WHERE nombre = 'PAGADA';

    IF v_nuevo_pagado >= v_monto_total THEN
        UPDATE multas
           SET monto_pagado = v_monto_total,
               estado_multa_id = v_estado_pagada_id,
               fecha_pagada = v_ahora
         WHERE id = p_multa_id;
        o_estado := 'PAGADA';
        o_saldo_restante := 0;
    ELSE
        UPDATE multas
           SET monto_pagado = v_nuevo_pagado
         WHERE id = p_multa_id;
        o_estado := 'PENDIENTE';
        o_saldo_restante := v_monto_total - v_nuevo_pagado;
    END IF;

    o_multa_id := p_multa_id;

    SELECT count(*) INTO v_otras_pendientes
      FROM multas m2
      JOIN prestamos p2 ON p2.id = m2.prestamo_id
     WHERE p2.usuario_id = v_usuario_id
       AND m2.estado_multa_id = v_estado_pendiente_id
       AND m2.id <> p_multa_id;

    IF o_estado = 'PAGADA' AND v_otras_pendientes = 0 THEN
        SELECT id INTO v_estado_activo_usuario_id FROM estados_usuario WHERE nombre = 'ACTIVO';
        UPDATE usuarios SET estado_id = v_estado_activo_usuario_id WHERE id = v_usuario_id;
        o_usuario_desbloqueado := TRUE;
    ELSE
        o_usuario_desbloqueado := FALSE;
    END IF;
END;
$$;

CREATE FUNCTION public.sp_registrar_devolucion(p_prestamo_id bigint, OUT o_prestamo_id bigint, OUT o_hubo_multa boolean, OUT o_monto_multa numeric) RETURNS record
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_libro_id                    BIGINT;
    v_usuario_id                  BIGINT;
    v_estado_prestamo_id          INTEGER;
    v_estado_devuelto_id          INTEGER;
    v_fecha_devolucion_estimada   TIMESTAMPTZ;
    v_dias_atraso                 INTEGER;
    v_valor_multa_diaria          NUMERIC(8,2);
    v_estado_pendiente_multa_id   INTEGER;
    v_estado_bloqueado_id         INTEGER;
    v_ahora                       TIMESTAMPTZ := NOW();
BEGIN
    SELECT libro_id, usuario_id, estado_prestamo_id, fecha_devolucion_estimada
      INTO v_libro_id, v_usuario_id, v_estado_prestamo_id, v_fecha_devolucion_estimada
      FROM prestamos
     WHERE id = p_prestamo_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'El prestamo % no existe', p_prestamo_id USING ERRCODE = 'LB404';
    END IF;

    SELECT id INTO v_estado_devuelto_id FROM estados_prestamo WHERE nombre = 'DEVUELTO';

    IF v_estado_prestamo_id = v_estado_devuelto_id THEN
        RAISE EXCEPTION 'El prestamo % ya fue devuelto', p_prestamo_id USING ERRCODE = 'LB409';
    END IF;

    UPDATE prestamos
       SET fecha_devolucion_real = v_ahora,
           estado_prestamo_id = v_estado_devuelto_id
     WHERE id = p_prestamo_id;

    UPDATE libros
       SET stock_disponible = stock_disponible + 1
     WHERE id = v_libro_id;

    o_prestamo_id := p_prestamo_id;
    o_hubo_multa := FALSE;
    o_monto_multa := NULL;

    IF v_ahora > v_fecha_devolucion_estimada THEN
        -- Cualquier atraso, aunque sea de horas, cuenta como mínimo 1 día.
        v_dias_atraso := CEIL(EXTRACT(EPOCH FROM (v_ahora - v_fecha_devolucion_estimada)) / 86400.0)::INTEGER;

        SELECT valor::NUMERIC INTO v_valor_multa_diaria
          FROM configuracion_sistema
         WHERE clave = 'monto_multa_diaria';

        IF v_valor_multa_diaria IS NULL THEN
            RAISE EXCEPTION 'Falta configurar monto_multa_diaria en configuracion_sistema'
                USING ERRCODE = 'LB422';
        END IF;

        o_monto_multa := v_dias_atraso * v_valor_multa_diaria;
        o_hubo_multa := TRUE;

        SELECT id INTO v_estado_pendiente_multa_id FROM estados_multa WHERE nombre = 'PENDIENTE';

        INSERT INTO multas (prestamo_id, monto, estado_multa_id, fecha_generada)
        VALUES (p_prestamo_id, o_monto_multa, v_estado_pendiente_multa_id, v_ahora);

        SELECT id INTO v_estado_bloqueado_id FROM estados_usuario WHERE nombre = 'BLOQUEADO_POR_MULTA';

        UPDATE usuarios SET estado_id = v_estado_bloqueado_id WHERE id = v_usuario_id;
    END IF;
END;
$$;

CREATE TRIGGER trg_auditoria_backup_programacion AFTER INSERT OR DELETE OR UPDATE ON public.backup_programacion FOR EACH ROW EXECUTE FUNCTION public.fn_auditoria_generica();

CREATE TRIGGER trg_auditoria_backups AFTER INSERT OR DELETE OR UPDATE ON public.backups FOR EACH ROW EXECUTE FUNCTION public.fn_auditoria_generica();

CREATE TRIGGER trg_auditoria_backups_tablas AFTER INSERT OR DELETE OR UPDATE ON public.backups_tablas FOR EACH ROW EXECUTE FUNCTION public.fn_auditoria_generica();

CREATE TRIGGER trg_auditoria_categorias_dano AFTER INSERT OR DELETE OR UPDATE ON public.categorias_dano FOR EACH ROW EXECUTE FUNCTION public.fn_auditoria_generica();

CREATE TRIGGER trg_auditoria_configuracion_respaldo AFTER INSERT OR DELETE OR UPDATE ON public.configuracion_respaldo FOR EACH ROW EXECUTE FUNCTION public.fn_auditoria_generica();

CREATE TRIGGER trg_auditoria_configuracion_sistema AFTER INSERT OR DELETE OR UPDATE ON public.configuracion_sistema FOR EACH ROW EXECUTE FUNCTION public.fn_auditoria_generica();

CREATE TRIGGER trg_auditoria_proveedores AFTER INSERT OR DELETE OR UPDATE ON public.proveedores FOR EACH ROW EXECUTE FUNCTION public.fn_auditoria_generica();

CREATE TRIGGER trg_auditoria_registros_respaldo AFTER INSERT OR DELETE OR UPDATE ON public.registros_respaldo FOR EACH ROW EXECUTE FUNCTION public.fn_auditoria_generica();

CREATE TRIGGER trg_auditoria_tipos_dano AFTER INSERT OR DELETE OR UPDATE ON public.tipos_dano FOR EACH ROW EXECUTE FUNCTION public.fn_auditoria_generica();

CREATE TRIGGER trg_libros_actualizado_en BEFORE UPDATE ON public.libros FOR EACH ROW EXECUTE FUNCTION public.set_actualizado_en();

CREATE TRIGGER trg_usuarios_actualizado_en BEFORE UPDATE ON public.usuarios FOR EACH ROW EXECUTE FUNCTION public.set_actualizado_en();

ALTER TABLE ONLY public.backup_programacion
    ADD CONSTRAINT backup_programacion_creado_por_fkey FOREIGN KEY (creado_por) REFERENCES public.usuarios(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.backup_programacion_tablas
    ADD CONSTRAINT backup_programacion_tablas_programacion_id_fkey FOREIGN KEY (programacion_id) REFERENCES public.backup_programacion(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.backups
    ADD CONSTRAINT backups_creado_por_fkey FOREIGN KEY (creado_por) REFERENCES public.usuarios(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.backups_tablas
    ADD CONSTRAINT backups_tablas_backup_id_fkey FOREIGN KEY (backup_id) REFERENCES public.backups(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.bitacora_auditoria
    ADD CONSTRAINT bitacora_auditoria_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.configuracion_respaldo
    ADD CONSTRAINT configuracion_respaldo_actualizado_por_fkey FOREIGN KEY (actualizado_por) REFERENCES public.usuarios(id);

ALTER TABLE ONLY public.evidencia_dano
    ADD CONSTRAINT evidencia_dano_registro_dano_id_fkey FOREIGN KEY (registro_dano_id) REFERENCES public.registro_danos(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.favoritos
    ADD CONSTRAINT favoritos_libro_id_fkey FOREIGN KEY (libro_id) REFERENCES public.libros(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.favoritos
    ADD CONSTRAINT favoritos_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.libro_autores
    ADD CONSTRAINT libro_autores_autor_id_fkey FOREIGN KEY (autor_id) REFERENCES public.autores(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.libro_autores
    ADD CONSTRAINT libro_autores_libro_id_fkey FOREIGN KEY (libro_id) REFERENCES public.libros(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.libro_categorias
    ADD CONSTRAINT libro_categorias_categoria_id_fkey FOREIGN KEY (categoria_id) REFERENCES public.categorias(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.libro_categorias
    ADD CONSTRAINT libro_categorias_libro_id_fkey FOREIGN KEY (libro_id) REFERENCES public.libros(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.libros
    ADD CONSTRAINT libros_editorial_id_fkey FOREIGN KEY (editorial_id) REFERENCES public.editoriales(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.libros
    ADD CONSTRAINT libros_estado_id_fkey FOREIGN KEY (estado_id) REFERENCES public.estados_libro(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.libros
    ADD CONSTRAINT libros_idioma_id_fkey FOREIGN KEY (idioma_id) REFERENCES public.idiomas(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.libros
    ADD CONSTRAINT libros_proveedor_id_fkey FOREIGN KEY (proveedor_id) REFERENCES public.proveedores(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.mensajes_chat
    ADD CONSTRAINT mensajes_chat_sesion_id_fkey FOREIGN KEY (sesion_id) REFERENCES public.sesiones_chat(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.multas
    ADD CONSTRAINT multas_estado_multa_id_fkey FOREIGN KEY (estado_multa_id) REFERENCES public.estados_multa(id);

ALTER TABLE ONLY public.multas
    ADD CONSTRAINT multas_prestamo_id_fkey FOREIGN KEY (prestamo_id) REFERENCES public.prestamos(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.multas
    ADD CONSTRAINT multas_registro_dano_id_fkey FOREIGN KEY (registro_dano_id) REFERENCES public.registro_danos(id);

ALTER TABLE ONLY public.notificaciones
    ADD CONSTRAINT notificaciones_prestamo_id_fkey FOREIGN KEY (prestamo_id) REFERENCES public.prestamos(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.notificaciones
    ADD CONSTRAINT notificaciones_tipo_notificacion_id_fkey FOREIGN KEY (tipo_notificacion_id) REFERENCES public.tipos_notificacion(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.notificaciones
    ADD CONSTRAINT notificaciones_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.prestamos
    ADD CONSTRAINT prestamos_bibliotecario_id_fkey FOREIGN KEY (bibliotecario_id) REFERENCES public.usuarios(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.prestamos
    ADD CONSTRAINT prestamos_estado_prestamo_id_fkey FOREIGN KEY (estado_prestamo_id) REFERENCES public.estados_prestamo(id);

ALTER TABLE ONLY public.prestamos
    ADD CONSTRAINT prestamos_libro_id_fkey FOREIGN KEY (libro_id) REFERENCES public.libros(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.prestamos
    ADD CONSTRAINT prestamos_reservacion_id_fkey FOREIGN KEY (reservacion_id) REFERENCES public.reservaciones(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.prestamos
    ADD CONSTRAINT prestamos_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.registro_dano_detalle
    ADD CONSTRAINT registro_dano_detalle_registro_dano_id_fkey FOREIGN KEY (registro_dano_id) REFERENCES public.registro_danos(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.registro_dano_detalle
    ADD CONSTRAINT registro_dano_detalle_tipo_dano_id_fkey FOREIGN KEY (tipo_dano_id) REFERENCES public.tipos_dano(id);

ALTER TABLE ONLY public.registro_danos
    ADD CONSTRAINT registro_danos_bibliotecario_id_fkey FOREIGN KEY (bibliotecario_id) REFERENCES public.usuarios(id);

ALTER TABLE ONLY public.registro_danos
    ADD CONSTRAINT registro_danos_prestamo_id_fkey FOREIGN KEY (prestamo_id) REFERENCES public.prestamos(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.registros_respaldo
    ADD CONSTRAINT registros_respaldo_ejecutado_por_fkey FOREIGN KEY (ejecutado_por) REFERENCES public.usuarios(id);

ALTER TABLE ONLY public.reservaciones
    ADD CONSTRAINT reservaciones_estado_reservacion_id_fkey FOREIGN KEY (estado_reservacion_id) REFERENCES public.estados_reservacion(id);

ALTER TABLE ONLY public.reservaciones
    ADD CONSTRAINT reservaciones_libro_id_fkey FOREIGN KEY (libro_id) REFERENCES public.libros(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.reservaciones
    ADD CONSTRAINT reservaciones_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.rol_permisos
    ADD CONSTRAINT rol_permisos_permiso_id_fkey FOREIGN KEY (permiso_id) REFERENCES public.permisos(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.rol_permisos
    ADD CONSTRAINT rol_permisos_rol_id_fkey FOREIGN KEY (rol_id) REFERENCES public.roles(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.sesiones_chat
    ADD CONSTRAINT sesiones_chat_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id);

ALTER TABLE ONLY public.sugerencias_adquisicion
    ADD CONSTRAINT sugerencias_adquisicion_proveedor_id_fkey FOREIGN KEY (proveedor_id) REFERENCES public.proveedores(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.sugerencias_adquisicion
    ADD CONSTRAINT sugerencias_adquisicion_revisado_por_fkey FOREIGN KEY (revisado_por) REFERENCES public.usuarios(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.sugerencias_adquisicion
    ADD CONSTRAINT sugerencias_adquisicion_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.suscripciones_disponibilidad
    ADD CONSTRAINT suscripciones_disponibilidad_libro_id_fkey FOREIGN KEY (libro_id) REFERENCES public.libros(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.suscripciones_disponibilidad
    ADD CONSTRAINT suscripciones_disponibilidad_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.tipos_dano
    ADD CONSTRAINT tipos_dano_categoria_id_fkey FOREIGN KEY (categoria_id) REFERENCES public.categorias_dano(id);

ALTER TABLE ONLY public.tokens_invalidos
    ADD CONSTRAINT tokens_invalidos_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.usuario_roles
    ADD CONSTRAINT usuario_roles_rol_id_fkey FOREIGN KEY (rol_id) REFERENCES public.roles(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.usuario_roles
    ADD CONSTRAINT usuario_roles_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT usuarios_creado_por_fkey FOREIGN KEY (creado_por) REFERENCES public.usuarios(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT usuarios_estado_id_fkey FOREIGN KEY (estado_id) REFERENCES public.estados_usuario(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.verificaciones_correo
    ADD CONSTRAINT verificaciones_correo_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE CASCADE;

