/*
 * SGB-SaaS — Modelo C4 (Structurizr DSL)
 * Niveles 1 (Contexto del sistema) y 2 (Contenedores).
 * Nivel 3 (Componentes) queda pendiente de un prompt aparte: depende de
 * detalle fino del backend de Préstamos que aún puede cambiar según lo
 * que construya Cajas, y rehacerlo dos veces sería trabajo perdido.
 *
 * ORIGEN: actualiza el diseño C4 nivel 1/2 de la Entrega 1A, que existía
 * solo como imagen (docs/diagramas/diagrama_c4_capa1.png y
 * diagrama_c4_capa2.jpeg), sin fuente versionada. Este archivo es esa
 * fuente, ahora en Structurizr DSL y actualizada contra el sistema REAL
 * de la Tercera Entrega (no el diseño aspiracional original) — ver la
 * sección "Diferencias vs. Entrega 1A" más abajo para el detalle de qué
 * cambió y por qué.
 *
 * Este archivo se validó (sintaxis, no exportación) con:
 *   docker run --rm -v "$(pwd)/docs/arquitectura:/usr/local/structurizr" \
 *     structurizr/cli validate -workspace workspace.dsl
 * -> exit code 0, sin errores.
 *
 * NOTA: structurizr/cli imprime un aviso de deprecación ("will not receive
 * any further updates", recomienda migrar a la herramienta consolidada
 * "Structurizr vNext" / imagen structurizr/structurizr, ver
 * https://docs.structurizr.com/commands). Sigue funcionando hoy (validado
 * arriba), pero quien integre el export a PNG al pipeline de CI debería
 * revisar primero si structurizr/structurizr ya reemplaza estos comandos.
 *
 * CÓMO EXPORTAR A PNG (pendiente de integrar al pipeline de CI, Bloque B):
 *
 *   1) Exportar el DSL a diagramas PlantUML con Structurizr CLI:
 *
 *        docker run --rm -v "$(pwd)/docs/arquitectura:/usr/local/structurizr" \
 *          structurizr/cli export -workspace workspace.dsl -format plantuml
 *
 *      Esto genera un .puml por vista (ej. workspace-NivelContexto.puml,
 *      workspace-NivelContenedores.puml) en el mismo directorio.
 *
 *   2) Renderizar cada .puml a PNG con PlantUML (requiere Graphviz):
 *
 *        plantuml -tpng docs/arquitectura/*.puml
 *
 *   Alternativa para revisión visual local (no apta para CI, es interactiva):
 *
 *        docker run --rm -p 8080:8080 \
 *          -v "$(pwd)/docs/arquitectura:/usr/local/structurizr" \
 *          structurizr/lite
 *
 *      y abrir http://localhost:8080 — permite exportar cada vista a PNG
 *      desde la propia UI para revisarla antes de commitear cambios al DSL.
 *
 * Diferencias vs. Entrega 1A (documentadas aquí, no solo en el commit):
 *   - Se agrega el actor "Administrador" (rol ADMIN), inexistente en el
 *     diseño original de Entrega 1A — surgió de la migración a RBAC
 *     normalizado (roles/usuario_roles) en la Tercera Entrega.
 *   - Se agrega el contenedor Redis (blacklist de JWT + cache del
 *     catálogo), ausente del diagrama de contenedores de Entrega 1A.
 *   - Gemini 2.0 Flash API y Google Books API, presentes en el diseño
 *     original de Entrega 1A como sistemas externos, se RETIRAN de este
 *     diagrama: no existe ninguna integración con ellos en el código real
 *     del backend ni del frontend hoy (verificado por búsqueda en el
 *     código fuente). Mantenerlos habría representado capacidades que el
 *     sistema no tiene. Si el equipo decide implementarlos más adelante,
 *     se reintroducen aquí en ese momento, no antes.
 *   - El "Visitante anónimo" se conserva como actor, pero con su alcance
 *     real acotado: hoy solo puede registrarse o iniciar sesión
 *     (`/api/auth/registro`, `/api/auth/login`, permitAll en
 *     SecurityConfig). La navegación del catálogo público SIN
 *     autenticarse está diseñada a nivel de roles de PostgreSQL
 *     (`rol_catalogo` en db/roles-privilegios.sql, materia de
 *     Administración de BD) pero AÚN NO está expuesta como endpoint
 *     `permitAll` en el backend real — `LibroController` exige rol
 *     LECTOR/BIBLIOTECARIO/GERENTE incluso para listar el catálogo. Esta
 *     es una discrepancia real entre el diseño y la implementación
 *     actual, documentada aquí en vez de dibujar una capacidad que no
 *     existe todavía.
 */

workspace "SGB-SaaS" "Sistema de Gestión Bibliotecaria Web — plataforma de catálogo, préstamos, reservas y multas para una biblioteca universitaria." {

    model {
        lector = person "Lector" "Estudiante o miembro de la comunidad con cuenta registrada: consulta el catálogo, reserva libros, y ve su historial de préstamos y multas."
        bibliotecario = person "Bibliotecario" "Registra préstamos, devoluciones y multas desde el mostrador; gestiona el catálogo."
        gerente = person "Gerente" "Supervisa la operación: gestiona cuentas de personal, catálogos maestros de estado, y reportes de auditoría."
        admin = person "Administrador" "Rol técnico (ADMIN): configura parámetros del sistema y catálogos base de la plataforma. No existía en el diseño de Entrega 1A -- se agregó con la normalización RBAC."
        anonimo = person "Visitante anónimo" "Sin cuenta todavía. Hoy solo puede registrarse o iniciar sesión -- ver nota sobre el catálogo público en el comentario de cabecera de este archivo."

        sgb = softwareSystem "SGB-SaaS" "Plataforma web de gestión bibliotecaria: catálogo, préstamos, reservas, multas y administración de usuarios." {

            spa = container "Frontend Angular" "SPA de catálogo, formularios reactivos y gestión de sesión (accessToken en memoria, nunca localStorage)." "TypeScript / Angular 17" "Frontend"

            backend = container "Backend Spring Boot" "API REST: autenticación JWT (cookie HttpOnly para el refresh token), autorización RBAC por roles, lógica de negocio de préstamos/reservas/multas vía JPA + procedimientos SQL." "Java 21 / Spring Boot 4.0.6"

            postgres = container "PostgreSQL" "Usuarios, roles/permisos, catálogo, préstamos, reservas, multas y bitácora de auditoría (26 tablas)." "PostgreSQL 16" "Database"

            redis = container "Redis" "Blacklist de tokens JWT revocados (logout/revocación) y cache del listado de libros con TTL configurable externamente." "Redis 7"
        }

        lector -> sgb "Consulta catálogo, reserva libros, ve su historial"
        bibliotecario -> sgb "Registra préstamos, devoluciones y multas"
        gerente -> sgb "Administra cuentas de personal, catálogos maestros y reportes"
        admin -> sgb "Configura parámetros del sistema y catálogos base"
        anonimo -> sgb "Se registra o inicia sesión"

        lector -> spa "Usa" "HTTPS"
        bibliotecario -> spa "Usa" "HTTPS"
        gerente -> spa "Usa" "HTTPS"
        admin -> spa "Usa" "HTTPS"
        anonimo -> spa "Usa" "HTTPS"

        spa -> backend "Peticiones REST (JSON) -- JWT en header Authorization; cookie refreshToken HttpOnly+Secure+SameSite=Strict para /api/auth/refresh" "HTTPS/JSON"
        backend -> postgres "Lee/escribe vía Spring Data JPA (CRUD) y 7 procedimientos/funciones SQL (joins, agregaciones, transacciones complejas)" "JDBC"
        backend -> redis "Consulta blacklist de tokens revocados; lee/escribe cache del catálogo (@Cacheable)" "Redis protocol"
    }

    views {
        systemContext sgb "NivelContexto" "Nivel 1 C4 -- actores y el sistema SGB-SaaS como caja negra." {
            include *
            autoLayout lr
        }

        container sgb "NivelContenedores" "Nivel 2 C4 -- Frontend Angular, Backend Spring Boot, PostgreSQL y Redis, según docker-compose.yml real." {
            include *
            autoLayout lr
        }

        styles {
            element "Person" {
                shape person
                background #08427b
                color #ffffff
            }
            element "Software System" {
                background #1168bd
                color #ffffff
            }
            element "Container" {
                background #438dd5
                color #ffffff
            }
            element "Frontend" {
                background #85bbf0
                color #000000
            }
            element "Database" {
                shape cylinder
            }
        }
    }
}
