/*
 * SGB-SaaS — C4 Model (Structurizr DSL)
 * Levels 1 (system context), 2 (containers) and 3 (components,
 * "Spring Boot Backend" container).
 * Level 3 was completed once Cajas' 8 branches were closed (all
 * merged to main) -- no more risk of redoing it over changes in the
 * Loans backend, the reason it had been left pending. It was built
 * from the real inventory of backend controllers/services/security
 * (not from memory nor the aspirational design), grouped by domain
 * to keep the diagram readable (21 components, not one entry per
 * each of the ~30 real classes).
 *
 * ORIGIN: updates the Delivery 1A level 1/2 design, which existed
 * only as images (docs/diagramas/diagrama_c4_capa1.png and
 * diagrama_c4_capa2.jpeg), with no versioned source. This file is that
 * source, now in Structurizr DSL and updated against the REAL system
 * of the Third Delivery (not the original aspirational design) — see
 * the "Differences vs. Delivery 1A" section below for what changed
 * and why.
 *
 * This file was validated (syntax, not export) with:
 *   docker run --rm -v "$(pwd)/docs/arquitectura:/usr/local/structurizr" \
 *     structurizr/cli validate -workspace workspace.dsl
 * -> exit code 0, no errors.
 *
 * NOTE: structurizr/cli prints a deprecation notice ("will not receive
 * any further updates", recommends migrating to the consolidated
 * "Structurizr vNext" tool / structurizr/structurizr image, see
 * https://docs.structurizr.com/commands). It still works today
 * (validated above), but whoever integrates PNG export into the CI
 * pipeline should first check whether structurizr/structurizr already
 * replaces these commands.
 *
 * HOW TO EXPORT TO PNG (pending CI pipeline integration, Block B):
 *
 *   1) Export the DSL to PlantUML diagrams with Structurizr CLI:
 *
 *        docker run --rm -v "$(pwd)/docs/arquitectura:/usr/local/structurizr" \
 *          structurizr/cli export -workspace workspace.dsl -format plantuml
 *
 *      This generates one .puml per view (e.g. workspace-NivelContexto.puml,
 *      workspace-NivelContenedores.puml) in the same directory.
 *
 *   2) Render each .puml to PNG with PlantUML (requires Graphviz):
 *
 *        plantuml -tpng docs/arquitectura/*.puml
 *
 *   Alternative for local visual review (not CI-suitable, it is interactive):
 *
 *        docker run --rm -p 8080:8080 \
 *          -v "$(pwd)/docs/arquitectura:/usr/local/structurizr" \
 *          structurizr/lite
 *
 *      and open http://localhost:8080 — allows exporting each view to PNG
 *      from the UI itself to review before committing DSL changes.
 *
 * Differences vs. Delivery 1A (documented here, not only in the commit):
 *   - The "Administrator" actor (ADMIN role) is added, missing from the
 *     original Delivery 1A design — it emerged from the migration to
 *     normalized RBAC (roles/usuario_roles) in the Third Delivery.
 *   - The Redis container is added (JWT blacklist + catalog cache),
 *     absent from the Delivery 1A container diagram.
 *   - Gemini 3.5 Flash Lite API and Google Books API, present in the
 *     original Delivery 1A design as external systems, are REMOVED from
 *     this diagram: there is no integration with them in today's real
 *     backend or frontend code (verified by searching the source).
 *     Keeping them would depict capabilities the system lacks. If the
 *     team implements them later, they are reintroduced here at that
 *     time, not before.
 *   - The "anonymous visitor" is kept as an actor, but with its real
 *     scope narrowed: today it can only sign up or log in
 *     (`/api/auth/registro`, `/api/auth/login`, permitAll in
 *     SecurityConfig). Browsing the public catalog WITHOUT
 *     authenticating is designed at PostgreSQL role level
 *     (`rol_catalogo` in db/roles-privilegios.sql, DB Administration
 *     subject) but is NOT yet exposed as a `permitAll` endpoint in the
 *     real backend — `LibroController` requires LECTOR/BIBLIOTECARIO/
 *     GERENTE even to list the catalog. This is a real gap between
 *     design and current implementation, documented here instead of
 *     drawing a capability that does not exist yet.
 */

workspace "SGB-SaaS" "Web library management system — catalog, loan, reservation and fine platform for a university library." {

    model {
        lector = person "Reader" "Registered community member or student: browses the catalog, reserves books, and views loan and fine history."
        bibliotecario = person "Librarian" "Registers loans, returns and fines at the desk; manages the catalog."
        gerente = person "Manager" "Supervises operations: manages staff accounts, master status catalogs, and audit reports."
        admin = person "Administrator" "Technical role (ADMIN): configures system parameters and base platform catalogs. Did not exist in the Delivery 1A design -- added with RBAC normalization."
        anonimo = person "Anonymous visitor" "No account yet. Today can only sign up or log in -- see public catalog note in this file's header comment."

        sgb = softwareSystem "SGB-SaaS" "Web library management platform: catalog, loans, reservations, fines and user administration." {

            spa = container "Angular Frontend" "Catalog SPA, reactive forms and session management (in-memory accessToken, never localStorage)." "TypeScript / Angular 17" "Frontend"

            backend = container "Spring Boot Backend" "REST API: JWT authentication (HttpOnly cookie for the refresh token), role-based RBAC authorization, loan/reservation/fine business logic via JPA + SQL procedures." "Java 21 / Spring Boot 4.0.6" {

                // Controllers, grouped by domain (not one per each of the
                // 16 real @RestController classes -- TestController is
                // excluded from the diagram: a smoke endpoint with no
                // business logic, /api/test/protegido).
                authApi = component "Auth API" "Sign-up, login, refresh (HttpOnly cookie), logout, email verification." "Spring MVC REST Controller"
                catalogoApi = component "Catalog API" "Books, authors, categories, favorites and acquisition suggestions." "Spring MVC REST Controller"
                prestamosApi = component "Loans API" "Loans, renewals, reservations, fines and their delinquency report." "Spring MVC REST Controller"
                notificacionesApi = component "Notifications API" "User notification lookup (upcoming due dates)." "Spring MVC REST Controller"
                credencialQrApi = component "QR Credential API" "User credential token lookup." "Spring MVC REST Controller"
                chatbotApi = component "Chatbot API" "Virtual assistant sessions and messages." "Spring MVC REST Controller"
                adminApi = component "Admin API" "User/role management, system configuration and audit log." "Spring MVC REST Controller"

                // Services, same domain grouping as the controllers.
                authService = component "Auth Service" "Login, sign-up, refresh/logout, authentication audit and email verification (single-use Redis code)." "Spring Service"
                catalogoService = component "Catalog Service" "Cached book listing, favorites and acquisition suggestions." "Spring Service"
                prestamosService = component "Loans Service" "Loan business rules (renewals, limits), reservations and fines." "Spring Service"
                reportesService = component "Reports Service" "Generates the delinquency report PDF (iText) from data already queried by Loans API." "Spring Service"
                notificacionesService = component "Notifications Service" "Generates upcoming-due notifications and sends emails (SMTP, spring-boot-starter-mail)." "Spring Service"
                credencialQrService = component "QR Credential Service" "Exposes the QR token generated by Postgres when inserting the user (uuid_generate_v4())." "Spring Service"
                chatbotService = component "Chatbot Service" "Orchestrates the conversation: stores messages, builds the prompt with real catalog/reservation grounding and asks Gemini for the answer." "Spring Service"
                adminService = component "Admin Service" "Account/role administration, system configuration and audit log lookup." "Spring Service"

                // Security -- JwtService, authentication filter and the two
                // rate limiters (OWASP A07, login and chatbot) backed by Redis.
                jwtService = component "JwtService" "Generates and validates access and refresh JWTs (jjwt)." "Spring Component"
                jwtAuthFilter = component "JwtAuthFilter" "Spring Security filter: validates each request's JWT and checks the Redis blacklist." "Spring Security Filter"
                userDetailsServiceImpl = component "UserDetailsServiceImpl" "Loads the user, roles and account status (locked/inactive) for Spring Security." "Spring Component"
                loginRateLimiter = component "LoginRateLimiter" "Failed login attempt limit per email+IP (OWASP A07)." "Spring Component"
                chatbotRateLimiter = component "ChatbotRateLimiter" "Chatbot message limit per user in the current window." "Spring Component"

                // External integrations.
                geminiClient = component "GeminiClient" "HTTP client to the external Gemini 3.5 Flash Lite REST API: simple retry on 429/timeout/5xx, never logs the URL with the API key." "Spring Component"
            }

            postgres = container "PostgreSQL" "Users, roles/permissions, catalog, loans, reservations, fines and audit log (44 tables)." "PostgreSQL 16" "Database"

            redis = container "Redis" "Revoked JWT token blacklist (logout/revocation) and book listing cache with externally configurable TTL." "Redis 7"
        }

        lector -> sgb "Browses catalog, reserves books, views history"
        bibliotecario -> sgb "Registers loans, returns and fines"
        gerente -> sgb "Manages staff accounts, master catalogs and reports"
        admin -> sgb "Configures system parameters and base catalogs"
        anonimo -> sgb "Signs up or logs in"

        lector -> spa "Uses" "HTTPS"
        bibliotecario -> spa "Uses" "HTTPS"
        gerente -> spa "Uses" "HTTPS"
        admin -> spa "Uses" "HTTPS"
        anonimo -> spa "Uses" "HTTPS"

        spa -> backend "REST requests (JSON) -- JWT in Authorization header; HttpOnly+Secure+SameSite=Strict refresh cookie for /api/auth/refresh" "HTTPS/JSON"
        backend -> postgres "Reads/writes via Spring Data JPA (CRUD) and 7 SQL procedures/functions (joins, aggregations, complex transactions)" "JDBC"
        backend -> redis "Checks revoked-token blacklist; reads/writes catalog cache (@Cacheable)" "Redis protocol"

        // Level 3 -- relationships between backend components (API -> Service,
        // and on to Postgres/Redis/GeminiClient per what each real class
        // uses; verified in code, not inferred).
        authApi -> authService "Uses"
        catalogoApi -> catalogoService "Uses"
        catalogoApi -> postgres "Direct read/write (AutorController/CategoriaController have no service layer of their own)" "JDBC"
        prestamosApi -> prestamosService "Uses"
        prestamosApi -> reportesService "Requests the delinquency report PDF"
        notificacionesApi -> notificacionesService "Uses"
        credencialQrApi -> credencialQrService "Uses"
        chatbotApi -> chatbotService "Uses"
        adminApi -> adminService "Uses"

        authService -> jwtService "Generates/validates access and refresh tokens"
        authService -> loginRateLimiter "Checks failed attempts before authenticating"
        authService -> notificacionesService "Sends the verification email (VerificacionCorreoService -> EmailService)"
        authService -> postgres "Reads/writes users and audit log" "JDBC"
        authService -> redis "Invalidates the token on logout (blacklist); email verification code" "Redis protocol"

        catalogoService -> postgres "Reads/writes books, favorites, acquisition suggestions" "JDBC"
        catalogoService -> redis "Book listing cache (@Cacheable, configurable TTL)" "Redis protocol"

        prestamosService -> postgres "Reads/writes loans, reservations and fines (JPA + SQL procedures)" "JDBC"

        notificacionesService -> postgres "Reads/writes notifications" "JDBC"

        credencialQrService -> postgres "Reads the user and QR token (Postgres-generated column)" "JDBC"

        chatbotService -> postgres "Reads/writes chat sessions and messages; queries the knowledge base" "JDBC"
        chatbotService -> geminiClient "Asks the model for the answer, with grounding prompt"
        chatbotService -> chatbotRateLimiter "Checks the user's message quota"
        chatbotService -> catalogoService "Queries real book availability (grounding, keeps the model from inventing it)"
        chatbotService -> prestamosService "Queries the user's reservations (grounding)"

        adminService -> postgres "Reads/writes users, roles, system configuration and audit log" "JDBC"

        jwtAuthFilter -> jwtService "Validates each incoming request's token"
        jwtAuthFilter -> redis "Checks the token against the blacklist" "Redis protocol"
        jwtAuthFilter -> userDetailsServiceImpl "Loads the authenticated user"
        userDetailsServiceImpl -> postgres "Loads user, roles and account status" "JDBC"
        loginRateLimiter -> redis "Counts failed attempts per email+IP" "Redis protocol"
        chatbotRateLimiter -> redis "Counts messages per user in the current window" "Redis protocol"
    }

    views {
        systemContext sgb "NivelContexto" "Level 1 C4 -- actors and the SGB-SaaS system as a black box." {
            include *
            autoLayout lr
        }

        container sgb "NivelContenedores" "Level 2 C4 -- Angular Frontend, Spring Boot Backend, PostgreSQL and Redis, per real docker-compose.yml." {
            include *
            autoLayout lr
        }

        component backend "NivelComponentes" "Level 3 C4 -- main Spring Boot backend components." {
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
