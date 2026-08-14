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
## 🔄 Flujo MVC — Ciclo de vida de una petición autenticada

![Diagrama de secuencia UML — Flujo MVC SGB](docs/diagramas/flujo-mvc-springboot.png)

[Ver diagrama en detalle](docs/diagramas/flujo-mvc-springboot.md)

| # | Componente | Descripción |
|---|---|---|
| 1 | Angular `HttpClient` | Envía `GET /api/v1/libros` con `Authorization: Bearer [token]` |
| 2 | Tomcat embebido | Recibe la conexión TCP, parsea el HTTP y crea `HttpServletRequest` |
| 3 | `DispatcherServlet.doDispatch()` | Punto de entrada de Spring MVC; enruta la solicitud al `HandlerMapping` |
| 4 | `JwtAuthFilter.doFilterInternal()` | Valida firma/expiración del token, consulta blacklist en Redis y establece el `SecurityContext` |
| 5 | `RequestMappingHandlerMapping.getHandler()` | Localiza el método del controlador que coincide con la URL y el verbo HTTP |
| 6 | `LibroController.listar()` | Recibe `Pageable` ya mapeado, delega al servicio |
| 7 | `LibroService.listar()` | Lógica de negocio; `@Cacheable("libros")` + `@Transactional(readOnly=true)`; llama al repositorio |
| 8 | `LibroRepository.findByEstado_Nombre()` | Spring Data JPA genera el SQL; Hibernate lo ejecuta contra PostgreSQL |
| 9 | `MappingJackson2HttpMessageConverter.write()` | Serializa `Page<LibroResponseDTO>` a JSON dentro de `doDispatch()` antes de retornar |
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

Despliegue de producción en **Render + Neon + Upstash** (plan free, sin
VM propia): ver [docs/despliegue/DEPLOYMENT.md](docs/despliegue/DEPLOYMENT.md).

- **Frontend:** https://biblora-sgb.onrender.com
- **Backend:** https://sgb-backend-b058.onrender.com
- **Health check:** https://sgb-backend-b058.onrender.com/actuator/health

> El backend corre en el plan Free de Render: duerme tras 15 min de
> inactividad, así que la primera petición tras un rato sin tráfico puede
> tardar ~30–60 s en responder (cold start).

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

### 👤 Cuenta demo (para evaluación — credenciales públicas)

El sistema desplegado en producción (https://biblora-sgb.onrender.com)
incluye un usuario demo de acceso libre para el tribunal, creado por la
migración Flyway `V11__seed_usuario_demo.sql`, **separado del admin real
y con rol limitado (LECTOR, sin permisos administrativos)**:

| Campo      | Valor                  |
|------------|------------------------|
| Correo     | `u@uteq.edu.ec`        |
| Contraseña | `usuario1`             |
| Rol        | `LECTOR`               |

Este usuario es el que el tribunal puede usar para entrar sin
registrarse. No modifica ni comparte la cuenta `admin@sgb-saas.local`.

## 🔁 Reproducibilidad (D.1 / D.2)

El repositorio se orquesta con `make` y el pipeline completo se corre de
punta a punta desde un clone limpio con un solo comando (criterio D.1 de la
guía):

```bash
make all   # = up → test → bench → audit → docs → compilar PDF del informe
```

Targets disponibles: `up` `down` `test` `bench` `audit` `docs` `all` `clean`.

- **`make docs`** regenera la evidencia documental que cambia con cada
  corrida: `docs/entorno/versions.txt` (versiones **reales** del entorno —
  Docker, docker compose, JDK, Node, Angular CLI, Python y k6, criterio D.2 —
  vía `scripts/capture-versions.sh`), el análisis estadístico de rendimiento
  (Wilcoxon pareado + Cliff's delta, `scripts/perf-analysis.py`) y verifica
  (solo advierte, no regenera) la sincronía del render C4 contra
  `docs/arquitectura/workspace.dsl`.
- **Notebooks con outputs archivados**: `docs/mediciones/perf-analysis.ipynb`
  (análisis de rendimiento, invoca `scripts/perf-analysis.py` — una sola
  fuente de verdad, no duplica lógica) y `docs/mediciones/sus-analysis.ipynb`
  (SUS, en estado *pendiente de datos* — no se fabrican resultados).
- **Semillas fijas (D.2)**: toda aleatoriedad del pipeline usa semilla
  explícita, no el default no determinista del lenguaje — el PRNG
  `mulberry32` de `k6/libros-listado-test.js` y el bootstrap del IC 95% del
  p95 en `scripts/perf-analysis.py` (`BOOTSTRAP_SEED`) usan **42**, ambos
  documentados en su propio código. El resto del pipeline (agregación
  estadística, SUS pendiente) no usa muestreo aleatorio: *no aplica*,
  confirmado. Ver también la convención en `docs/mediciones/README.md`.
- **Requisitos adicionales** para `make all`: `latexmk` (compilar el PDF —
  Windows: MiKTeX, `winget install MiKTeX.MiKTeX`; Debian/Ubuntu:
  `apt install latexmk`) y Chrome instalado (para `make test-frontend` con
  ChromeHeadless). `make test-frontend` instala `node_modules` automáticamente
  si faltan (`npm ci`, mismo paso que CI).

## 📄 Estado del proyecto

**Entrega 1B (Junio 2026):** módulo de autenticación JWT + CRUD de `Libro` con Spring Data JPA, Flyway, Redis y Docker Compose. Ver `docs/` para el informe técnico completo.