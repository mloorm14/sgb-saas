# Sistema de Gestión Bibliotecaria Web (SGB - SaaS) 📚

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.21712467.svg)](https://doi.org/10.5281/zenodo.21712467)

Plataforma 100% web diseñada para la modernización de bibliotecas institucionales y municipales, desarrollada como Proyecto Fin de Curso para la asignatura de Aplicaciones Web (2026-2027).

## 🎬 Demo

Video demo del sistema (2-3 min): [ver en Google Drive](https://drive.google.com/file/d/19s7Ls2Ixz7wJ7RWzfJ-F2U18LknC7O_r/view?usp=drive_link)

> ⚠️ **Pendiente de confirmación manual**: no es posible verificar desde aquí que el permiso de este enlace de Drive esté en modo "Cualquier usuario con el enlace puede ver". Antes de la entrega final, confirmar manualmente en Drive (botón "Compartir" → "Acceso general") que el enlace es público; si está restringido a cuentas específicas, un evaluador externo no podrá reproducirlo.

## 👥 Equipo de Desarrollo

- Cajas Ibarra Irvin Marcelo — Backend (CRUD)
- Loor Medranda Marlon Taylor — Tech Lead / DevOps / Seguridad
- Panama Murillo Moises Antonio — Frontend

## 🚀 Tecnologías

- **Frontend:** Angular 17 / Tailwind CSS
- **Backend:** Spring Boot 4.0.6 / Java 21 / Spring Security 7.x
- **Base de datos:** PostgreSQL 16
- **Caché y Auth:** Redis 7 (lista negra de tokens JWT)
- **Migraciones:** Flyway 9
- **Documentación API:** springdoc-openapi (Swagger UI)
- **IA Integrada:** Gemini 2.0 Flash API (Entrega 2)

---
# Flujo MVC en Spring Boot 3 — SGB-SaaS

Diagrama de secuencia UML del ciclo de vida de una petición autenticada, trazado sobre el endpoint `GET /api/v1/reservaciones/usuario/{usuarioId}` con las clases reales del proyecto.

📎 **Diagrama:** [`docs/diagramas/flujo-mvc-springboot.png`](docs/diagramas/flujo-mvc-springboot.png)

## Descripción de cada paso del flujo

1. **Angular → JwtAuthFilter:** `ReservacionesComponent.cargarPagina()` envía `GET /api/v1/reservaciones/usuario/{usuarioId}?page&size` con el header `Authorization: Bearer <token>` (Tomcat recibe la conexión TCP y arma el `HttpServletRequest` de forma automática).
2. **JwtAuthFilter (interno):** `JwtAuthFilter.doFilterInternal()` valida firma/expiración del JWT con `jwtService.validateToken(token)`.
3. **JwtAuthFilter (interno):** revisa la blacklist en Redis con `redisTemplate.hasKey("blacklist:" + jti)`.
4. **JwtAuthFilter → SecurityContext:** carga el usuario con `loadUserByUsername(correo)` y llama `SecurityContextHolder.getContext().setAuthentication(authToken)`.
5. **JwtAuthFilter → DispatcherServlet:** `filterChain.doFilter()` continúa la cadena y entrega el control a `DispatcherServlet.doDispatch()`.
6. **DispatcherServlet → HandlerMapping:** `getHandler(request)` resuelve que el método destino es `ReservacionController.listarPorUsuario()` (`RequestMappingHandlerMapping`).
7. **DispatcherServlet → ReservacionController:** invoca `listarPorUsuario(usuarioId, Authentication, Pageable)`.
8. **ReservacionController → ReservacionService:** llama `reservacionService.listarPorUsuario(usuarioId, authentication, pageable)`, anotado con `@Transactional(readOnly = true)` (Spring abre la transacción con `TransactionInterceptor`/`CglibAopProxy` antes de ejecutar el cuerpo del método).
9. **ReservacionService (interno):** ejecuta `validarAccesoUsuario(usuarioId, authentication)`; si el usuario tiene rol `LECTOR`, solo puede consultar sus propias reservaciones (compara el id de la URL contra su propio id resuelto por correo).
10. **ReservacionService → ReservacionRepository:** llama `findByUsuarioId(usuarioId, pageable)`.
11. **ReservacionRepository → PostgreSQL:** Hibernate genera y ejecuta el `SELECT ... FROM reservaciones WHERE usuario_id = ? LIMIT ? OFFSET ?`.
12. **PostgreSQL → ReservacionRepository:** la base de datos devuelve el `ResultSet` con las filas encontradas.
13. **ReservacionRepository → ReservacionService:** Spring Data JPA mapea el resultado a `Page<Reservacion>` y lo retorna al servicio.
14. **ReservacionService (interno):** convierte cada `Reservacion` de la página a `ReservacionResponseDTO` mediante `toDTO()`.
15. **ReservacionService → ReservacionController:** retorna `Page<ReservacionResponseDTO>`.
16. **ReservacionController → DispatcherServlet:** retorna `ResponseEntity<Page<ReservacionResponseDTO>>` con código `200 OK`.
17. **DispatcherServlet (interno):** serializa el body a JSON con `MappingJackson2HttpMessageConverter.write()` dentro de `doDispatch()`.
18. **DispatcherServlet → JwtAuthFilter:** `filterChain.doFilter()` retorna (desenrollado de pila, sin acción adicional del filtro sobre la respuesta).
19. **JwtAuthFilter → Angular:** la respuesta `HTTP 200 + JSON` llega al cliente, que actualiza el listado de reservaciones en pantalla.
---

## 🔐 Autenticación

Autenticación stateless basada en **JWT (HS256)**: `accessToken` de corta duración (1h) + `refreshToken` (7 días, cookie `HttpOnly`). Los tokens revocados se almacenan en Redis (blacklist por `jti`) para permitir invalidación de sesión antes de su expiración natural.

## ⚙️ Instrucciones de instalación local

### Requisitos previos

- Docker y Docker Compose
- Node.js v18+ (solo si se desea ejecutar el frontend fuera de Docker)
- Java 21+ (solo si se desea ejecutar el backend fuera de Docker)

### Pasos (con Docker Compose — recomendado)

1. Clonar este repositorio:
   
   ```bash
   git clone https://github.com/mloorm14/sgb-saas.git
   cd sgb-saas
   ```
1. Copiar las variables de entorno:
   
   ```bash
   cp .env.example .env
   ```
   
   Editar `.env` y definir `JWT_SECRET` (mínimo 256 bits, generado con `openssl rand -hex 32`).
1. Levantar todos los servicios:
   
   ```bash
   docker compose up --build -d
   ```
1. Verificar que todos los servicios estén en estado `healthy`:
   
   ```bash
   docker compose ps
   ```
1. Ejecutar las pruebas unitarias (sin Docker, requiere Java 21):
   
   ```bash
   cd backend-springboot
   ./mvnw test
   ```
   
   NOTA: en Windows usar `mvnw.cmd` en lugar de `./mvnw`

### Acceso a la aplicación

|Servicio          |URL                                        |
|------------------|-------------------------------------------|
|Frontend (Angular)|<http://localhost:4200>                    |
|API Backend       |<http://localhost:8080>                    |
|Swagger UI        |<http://localhost:8080/swagger-ui.html>    |
|Actuator health   |<http://localhost:8080/actuator/health>    |
|PostgreSQL        |localhost:5432                             |
|Redis             |localhost:6379                             |

## 📂 Estructura del repositorio

```
sgb-saas/
├── backend-springboot/   # API REST: Spring Boot 4.0.6 + JPA + Security + JWT
├── frontend-angular/      # SPA Angular 17
├── database/
│   └── migrations/        # Scripts versionados de Flyway (V1__, V2__, ...)
├── docs/                   # Diagramas, ADRs, informes técnicos
├── docker-compose.yml      # Orquestación de servicios
└── .env.example            # Plantilla de variables de entorno
```

## 🌳 Flujo de trabajo Git

- `main`: código estable de entregas cerradas (solo via Pull Request).
- `develop`: rama de integración continua.
- `feature/*`: ramas de trabajo individuales, integradas mediante Pull Request hacia `develop`.

Convención de commits: [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`).

## 🔗 URL del sistema desplegado

*En desarrollo (se actualizará en la Entrega 2).*

## 🔑 Credenciales de desarrollo

Al inicializar la base de datos con `db/schema.sql` + `db/seed.sql` (montados
en `docker-entrypoint-initdb.d/`), se crea un usuario administrador de
desarrollo:

| Campo      | Valor                     |
|------------|---------------------------|
| Correo     | `admin@sgb-saas.local`    |
| Contraseña | `Admin123!`               |
| Rol        | `ADMIN`                   |

⚠️ Solo para entornos locales de desarrollo. Nunca usar estas credenciales
en un entorno con datos reales o accesible públicamente.

## 📄 Estado del proyecto

**Entrega 1B (Junio 2026):** módulo de autenticación JWT + CRUD de `Libro` con Spring Data JPA, Flyway, Redis y Docker Compose. Ver `docs/` para el informe técnico completo.