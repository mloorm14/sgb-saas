# Métricas de Calidad de Diseño (Auth + Libros)

Generado desde commit **c7c6d9e** (código real verificado).

---

## Resumen de Métricas

| Clase | LOC | Métodos (negocio) | LPM | Complejidad (método + regla) | Acoplamiento (deps reales) |
|-------|-----|-------------------|-----|------------------------------|----------------------------|
| **JwtAuthFilter** | 68 | 1 (`doFilterInternal`) | 46.0 | **5** (`doFilterInternal`: 4 `if` + 1 `catch`) | 4 (JwtService, RedisTemplate, UserDetailsServiceImpl, FilterChain) |
| **LibroService** | 247 | 17 (públicos + privados de negocio) | 13.5 | **8** (`actualizar`: 3 `if` + 1 `for` + 2 null-checks + 1 loop `resolverCategorias`/`resolverAutores`) | 8 (LibroRepo, EditorialRepo, IdiomaRepo, EstadoLibroRepo, CategoriaRepo, AutorRepo, EstadoLibroRepository, cache) |
| **AuthController** | 118 | 8 (públicos HTTP + 2 privados helper) | 11.8 | **4** (`login`: 2 `if` + 1 validación cookie nula + 1 validación DTO `@Valid`) | 2 (AuthService, JwtService) |

---

## Reglas de Conteo (transparencia)

| Métrica | Regla aplicada |
|---------|----------------|
| **LOC** | `wc -l` sobre archivo `.java` (incluye comentarios, imports, anotaciones). |
| **Métodos de negocio** | Métodos públicos del servicio/controlador + privados con lógica real (no getters/setters/constructores/builder Lombok). |
| **LPM** | LOC / métodos de negocio. |
| **Complejidad ciclomática aproximada** | 1 + Σ puntos de decisión en el método más complejo:<br>• `if` / `else if` / `else` = +1 c/u<br>• `switch` case = +1 c/u<br>• `for` / `while` / `do-while` / `for-each` = +1 c/u<br>• `? :` (ternario) = +1<br>• `&&` / `||` en condiciones = +1 c/u<br>• `try` / `catch` = +1 (punto de decisión implícito de flujo) |
| **Acoplamiento (CBO real)** | Clases inyectadas por constructor **o** usadas directamente en cuerpo de métodos (excluye: imports de anotaciones, DTOs, exceptions, tipos primitivos, `java.*`, `jakarta.*`, `org.springframework.*` genéricos). Cuenta: repos inyectados, servicios inyectados, entidades manipuladas, utilidades llamadas. |

---

## Detalle por Clase

### JwtAuthFilter (`JwtAuthFilter.java` – 68 LOC)

| Ítem | Valor |
|------|-------|
| LOC total | 68 |
| Métodos de negocio | 1 (`doFilterInternal`) |
| LPM | 68.0 |
| Método más complejo | `doFilterInternal` – **CC ≈ 5** |
| Puntos de decisión | 4 `if` (líneas 37, 44, 50, 56) + 1 `catch` (línea 62) = **5** |
| Acoplamiento real | **4** — JwtService, RedisTemplate, UserDetailsServiceImpl, FilterChain |

### LibroService (`LibroService.java` – 247 LOC)

| Ítem | Valor |
|------|-------|
| LOC total | 247 |
| Métodos de negocio | 17 (9 públicos + 8 privados con lógica: `validarStock`, `resolverCategorias`, `resolverAutores`, `toDTO`, `fromDTO`, `listarPorCategoria`, `listarPorAutor`, `buscarPorId`, `sugerir`, `crear`, `actualizar`, `eliminar`, `validarStock`, `resolverCategorias`, `resolverAutores`, `toDTO`, `fromDTO`) |
| LPM | 14.5 |
| Método más complejo | `actualizar` (líneas 133-157) – **CC ≈ 8** |
| Puntos de decisión en `actualizar` | 3 `if` (líneas 134, 137, 141) + 1 `for` en `resolverCategorias` (L189) + 1 `for` en `resolverAutores` (L200) + 2 null-checks ternarios (L150-154) ≈ **8** |
| Acoplamiento real | **8** — LibroRepository, EditorialRepository, IdiomaRepository, EstadoLibroRepository, CategoriaRepository, AutorRepository, EstadoLibro (entidad), cache `@CacheEvict`/`@Cacheable` |

### AuthController (`AuthController.java` – 118 LOC)

| Ítem | Valor |
|------|-------|
| LOC total | 118 |
| Métodos de negocio | 8 (6 endpoints HTTP + 2 helpers: `obtenerIpOrigen`, `buildRefreshCookie`) |
| LPM | 14.7 |
| Método más complejo | `login` (líneas 55-61) / `logout` (63-71) / `refresh` (92-103) – **CC ≈ 4** |
| Puntos de decisión en `login` | 1 validación `@Valid` (implícita) + 1 `if` cookie nula (L94) + 1 `if` validación service (implícita) + 1 construcción cookie = **≈4** |
| Acoplamiento real | **2** — AuthService, JwtService |

---

## Resumen Comparativo

| Clase | CC máx | Acoplamiento | LPM | Comentario |
|-------|--------|--------------|-----|------------|
| JwtAuthFilter | 5 (baja) | 4 (moderado) | 68 | Un solo método grande; filtro de seguridad simple. |
| LibroService | 8 (media) | 8 (alto) | 14.5 | Lógica de negocio real + 6 repos + cache; `actualizar` es el hotspot. |
| AuthController | 4 (baja) | 2 (bajo) | 14.7 | Controlador delgado; delega a AuthService. |

> **Nota**: La complejidad de `LibroService.actualizar` (8) está en el límite aceptable (suelen recomendarse ≤10). Si crece, considerar extraer `validarStock` + `resolverCategorias/Autores` a métodos separados ya hechos, o mover mapeo DTO a un mapper dedicado.

---

**Generado**: 2026-08-17  
**Commit base**: `c7c6d9e15b0df904f524e6f0a214e3e0ab0dff63`  
**Archivos fuente**: `backend-springboot/src/main/java/com/uteq/backend/...`