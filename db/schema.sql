-- ============================================================================
-- SGB-SaaS - db/schema.sql
-- Snapshot consolidado del estado objetivo del esquema (31 tablas) para
-- reproducibilidad desde cero via docker-entrypoint-initdb.d/. Generado con
-- pg_dump --schema-only desde una base con las migraciones V1..V13 aplicadas
-- (database/migrations/) -- ver docs/adr/adr-006-estrategia-schema-reproducible.md.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;

COMMENT ON EXTENSION pg_trgm IS 'text similarity measurement and index searching based on trigrams';

CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;

COMMENT ON EXTENSION "uuid-ossp" IS 'generate universally unique identifiers (UUIDs)';

CREATE FUNCTION public.set_actualizado_en() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.actualizado_en = NOW();
    RETURN NEW;
END;
$$;

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

CREATE SEQUENCE public.categorias_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.categorias_id_seq OWNED BY public.categorias.id;

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
    fecha_registro timestamp with time zone DEFAULT now() CONSTRAINT libros_creado_en_not_null NOT NULL,
    actualizado_en timestamp with time zone DEFAULT now() NOT NULL,
    ubicacion_fisica character varying(50),
    portada_imagen bytea,
    portada_nombre character varying(255),
    portada_tipo character varying(100),
    portada_tamanio integer,
    CONSTRAINT chk_anio_publicacion CHECK (((anio_publicacion >= 1000) AND (anio_publicacion <= 2100))),
    CONSTRAINT chk_stock_disponible CHECK (((stock_disponible >= 0) AND (stock_disponible <= stock_total))),
    CONSTRAINT chk_stock_total CHECK ((stock_total >= 0))
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
    monto_pagado numeric(8,2) NOT NULL DEFAULT 0,
    estado_multa_id integer NOT NULL,
    fecha_generada timestamp with time zone DEFAULT now() NOT NULL,
    fecha_pagada timestamp with time zone,
    observaciones character varying(255),
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
    CONSTRAINT sugerencias_adquisicion_estado_check CHECK (((estado)::text = ANY ((ARRAY['PENDIENTE'::character varying, 'APROBADA'::character varying, 'RECHAZADA'::character varying])::text[])))
);

CREATE SEQUENCE public.sugerencias_adquisicion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.sugerencias_adquisicion_id_seq OWNED BY public.sugerencias_adquisicion.id;

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
    fecha_registro timestamp with time zone DEFAULT now() CONSTRAINT usuarios_creado_en_not_null NOT NULL,
    actualizado_en timestamp with time zone DEFAULT now() NOT NULL,
    apellido character varying(100) NOT NULL,
    identificacion_usuario character varying(20),
    correo_verificado boolean DEFAULT false NOT NULL,
    estado_id integer NOT NULL,
    credencial_qr_token uuid DEFAULT public.uuid_generate_v4() NOT NULL
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

ALTER TABLE ONLY public.base_conocimiento ALTER COLUMN id SET DEFAULT nextval('public.base_conocimiento_id_seq'::regclass);

ALTER TABLE ONLY public.bitacora_auditoria ALTER COLUMN id SET DEFAULT nextval('public.bitacora_auditoria_id_seq'::regclass);

ALTER TABLE ONLY public.categorias ALTER COLUMN id SET DEFAULT nextval('public.categorias_id_seq'::regclass);

ALTER TABLE ONLY public.editoriales ALTER COLUMN id SET DEFAULT nextval('public.editoriales_id_seq'::regclass);

ALTER TABLE ONLY public.estados_libro ALTER COLUMN id SET DEFAULT nextval('public.estados_libro_id_seq'::regclass);

ALTER TABLE ONLY public.estados_multa ALTER COLUMN id SET DEFAULT nextval('public.estados_multa_id_seq'::regclass);

ALTER TABLE ONLY public.estados_prestamo ALTER COLUMN id SET DEFAULT nextval('public.estados_prestamo_id_seq'::regclass);

ALTER TABLE ONLY public.estados_reservacion ALTER COLUMN id SET DEFAULT nextval('public.estados_reservacion_id_seq'::regclass);

ALTER TABLE ONLY public.estados_usuario ALTER COLUMN id SET DEFAULT nextval('public.estados_usuario_id_seq'::regclass);

ALTER TABLE ONLY public.idiomas ALTER COLUMN id SET DEFAULT nextval('public.idiomas_id_seq'::regclass);

ALTER TABLE ONLY public.libros ALTER COLUMN id SET DEFAULT nextval('public.libros_id_seq'::regclass);

ALTER TABLE ONLY public.mensajes_chat ALTER COLUMN id SET DEFAULT nextval('public.mensajes_chat_id_seq'::regclass);

ALTER TABLE ONLY public.multas ALTER COLUMN id SET DEFAULT nextval('public.multas_id_seq'::regclass);

ALTER TABLE ONLY public.notificaciones ALTER COLUMN id SET DEFAULT nextval('public.notificaciones_id_seq'::regclass);

ALTER TABLE ONLY public.permisos ALTER COLUMN id SET DEFAULT nextval('public.permisos_id_seq'::regclass);

ALTER TABLE ONLY public.prestamos ALTER COLUMN id SET DEFAULT nextval('public.prestamos_id_seq'::regclass);

ALTER TABLE ONLY public.reservaciones ALTER COLUMN id SET DEFAULT nextval('public.reservaciones_id_seq'::regclass);

ALTER TABLE ONLY public.roles ALTER COLUMN id SET DEFAULT nextval('public.roles_id_seq'::regclass);

ALTER TABLE ONLY public.sugerencias_adquisicion ALTER COLUMN id SET DEFAULT nextval('public.sugerencias_adquisicion_id_seq'::regclass);

ALTER TABLE ONLY public.tipos_notificacion ALTER COLUMN id SET DEFAULT nextval('public.tipos_notificacion_id_seq'::regclass);

ALTER TABLE ONLY public.tokens_invalidos ALTER COLUMN id SET DEFAULT nextval('public.tokens_invalidos_id_seq'::regclass);

ALTER TABLE ONLY public.usuarios ALTER COLUMN id SET DEFAULT nextval('public.usuarios_id_seq'::regclass);

ALTER TABLE ONLY public.verificaciones_correo ALTER COLUMN id SET DEFAULT nextval('public.verificaciones_correo_id_seq'::regclass);

ALTER TABLE ONLY public.autores
    ADD CONSTRAINT autores_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.base_conocimiento
    ADD CONSTRAINT base_conocimiento_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.bitacora_auditoria
    ADD CONSTRAINT bitacora_auditoria_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.categorias
    ADD CONSTRAINT categorias_nombre_key UNIQUE (nombre);

ALTER TABLE ONLY public.categorias
    ADD CONSTRAINT categorias_pkey PRIMARY KEY (id);

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

CREATE UNIQUE INDEX idx_libros_isbn ON public.libros USING btree (isbn);

CREATE INDEX idx_libros_titulo_trgm ON public.libros USING gin (titulo public.gin_trgm_ops);

CREATE INDEX idx_mensajes_chat_sesion ON public.mensajes_chat USING btree (sesion_id);

CREATE INDEX idx_notificaciones_usuario ON public.notificaciones USING btree (usuario_id);

CREATE UNIQUE INDEX idx_usuarios_correo ON public.usuarios USING btree (correo);

CREATE UNIQUE INDEX idx_usuarios_credencial_qr_token ON public.usuarios USING btree (credencial_qr_token);

CREATE TRIGGER trg_libros_actualizado_en BEFORE UPDATE ON public.libros FOR EACH ROW EXECUTE FUNCTION public.set_actualizado_en();

CREATE TRIGGER trg_usuarios_actualizado_en BEFORE UPDATE ON public.usuarios FOR EACH ROW EXECUTE FUNCTION public.set_actualizado_en();

ALTER TABLE ONLY public.bitacora_auditoria
    ADD CONSTRAINT bitacora_auditoria_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE SET NULL;

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

ALTER TABLE ONLY public.mensajes_chat
    ADD CONSTRAINT mensajes_chat_sesion_id_fkey FOREIGN KEY (sesion_id) REFERENCES public.sesiones_chat(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.multas
    ADD CONSTRAINT multas_estado_multa_id_fkey FOREIGN KEY (estado_multa_id) REFERENCES public.estados_multa(id);

ALTER TABLE ONLY public.multas
    ADD CONSTRAINT multas_prestamo_id_fkey FOREIGN KEY (prestamo_id) REFERENCES public.prestamos(id) ON DELETE RESTRICT;

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
    ADD CONSTRAINT sugerencias_adquisicion_revisado_por_fkey FOREIGN KEY (revisado_por) REFERENCES public.usuarios(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.sugerencias_adquisicion
    ADD CONSTRAINT sugerencias_adquisicion_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.tokens_invalidos
    ADD CONSTRAINT tokens_invalidos_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.usuario_roles
    ADD CONSTRAINT usuario_roles_rol_id_fkey FOREIGN KEY (rol_id) REFERENCES public.roles(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.usuario_roles
    ADD CONSTRAINT usuario_roles_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT usuarios_estado_id_fkey FOREIGN KEY (estado_id) REFERENCES public.estados_usuario(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.verificaciones_correo
    ADD CONSTRAINT verificaciones_correo_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE CASCADE;

-- ============================================================================
-- V14: Devoluciones con registro de daños
-- ============================================================================

CREATE TABLE public.tipos_dano (
    id integer NOT NULL,
    nombre character varying(50) NOT NULL,
    precio numeric(8,2) NOT NULL CHECK (precio >= 0),
    activo boolean DEFAULT true NOT NULL
);

CREATE SEQUENCE public.tipos_dano_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.tipos_dano_id_seq OWNED BY public.tipos_dano.id;

ALTER TABLE ONLY public.tipos_dano ALTER COLUMN id SET DEFAULT nextval('public.tipos_dano_id_seq'::regclass);

ALTER TABLE ONLY public.tipos_dano
    ADD CONSTRAINT tipos_dano_nombre_key UNIQUE (nombre);

ALTER TABLE ONLY public.tipos_dano
    ADD CONSTRAINT tipos_dano_pkey PRIMARY KEY (id);

INSERT INTO public.tipos_dano (nombre, precio) VALUES
    ('Paginas rotas', 3.00),
    ('Manchas', 5.00),
    ('Portada/Lomo', 7.00),
    ('Humedad', 10.00),
    ('Rayon', 4.00)
ON CONFLICT (nombre) DO NOTHING;

CREATE TABLE public.registro_danos (
    id bigint NOT NULL,
    prestamo_id bigint NOT NULL,
    estado_devolucion character varying(20) NOT NULL CHECK (estado_devolucion IN ('BUEN_ESTADO', 'CON_DANO', 'PERDIDO')),
    descripcion text,
    bibliotecario_id bigint NOT NULL,
    fecha_registro timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE public.registro_danos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.registro_danos_id_seq OWNED BY public.registro_danos.id;

ALTER TABLE ONLY public.registro_danos ALTER COLUMN id SET DEFAULT nextval('public.registro_danos_id_seq'::regclass);

ALTER TABLE ONLY public.registro_danos
    ADD CONSTRAINT registro_danos_pkey PRIMARY KEY (id);

CREATE UNIQUE INDEX uq_registro_danos_prestamo ON public.registro_danos(prestamo_id);

CREATE TABLE public.registro_dano_detalle (
    id bigint NOT NULL,
    registro_dano_id bigint NOT NULL,
    tipo_dano_id integer,
    nombre_custom character varying(100),
    precio_cobrado numeric(8,2) NOT NULL CHECK (precio_cobrado >= 0),
    CHECK (tipo_dano_id IS NOT NULL OR nombre_custom IS NOT NULL)
);

CREATE SEQUENCE public.registro_dano_detalle_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.registro_dano_detalle_id_seq OWNED BY public.registro_dano_detalle.id;

ALTER TABLE ONLY public.registro_dano_detalle ALTER COLUMN id SET DEFAULT nextval('public.registro_dano_detalle_id_seq'::regclass);

ALTER TABLE ONLY public.registro_dano_detalle
    ADD CONSTRAINT registro_dano_detalle_pkey PRIMARY KEY (id);

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

ALTER TABLE ONLY public.evidencia_dano ALTER COLUMN id SET DEFAULT nextval('public.evidencia_dano_id_seq'::regclass);

ALTER TABLE ONLY public.evidencia_dano
    ADD CONSTRAINT evidencia_dano_pkey PRIMARY KEY (id);

ALTER TABLE public.multas ADD COLUMN registro_dano_id bigint REFERENCES public.registro_danos(id);

ALTER TABLE ONLY public.registro_danos
    ADD CONSTRAINT registro_danos_prestamo_id_fkey FOREIGN KEY (prestamo_id) REFERENCES public.prestamos(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.registro_danos
    ADD CONSTRAINT registro_danos_bibliotecario_id_fkey FOREIGN KEY (bibliotecario_id) REFERENCES public.usuarios(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.registro_dano_detalle
    ADD CONSTRAINT registro_dano_detalle_registro_dano_id_fkey FOREIGN KEY (registro_dano_id) REFERENCES public.registro_danos(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.registro_dano_detalle
    ADD CONSTRAINT registro_dano_detalle_tipo_dano_id_fkey FOREIGN KEY (tipo_dano_id) REFERENCES public.tipos_dano(id);

ALTER TABLE ONLY public.evidencia_dano
    ADD CONSTRAINT evidencia_dano_registro_dano_id_fkey FOREIGN KEY (registro_dano_id) REFERENCES public.registro_danos(id) ON DELETE CASCADE;

