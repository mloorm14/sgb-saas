# Flujo MVC en Spring Boot 3 — SGB-SaaS

Diagrama de secuencia UML del ciclo de vida de una petición autenticada, trazado sobre el endpoint `GET /api/v1/libros` con las clases reales del proyecto.

📎 **Diagrama:** [`docs/diagramas/flujo-mvc-springboot.png`](docs/diagramas/flujo-mvc-springboot.png)

## Descripción de cada paso del flujo

1. **Angular → JwtAuthFilter:** `LibrosComponent.cargarLibros()` envía `GET /api/v1/libros` con el header `Authorization: Bearer <token>`.
2. **JwtAuthFilter → SecurityContext:** `JwtAuthFilter.doFilterInternal()` valida firma/expiración del JWT, revisa la blacklist en Redis y llama a `SecurityContextHolder.getContext().setAuthentication(authToken)`.
3. **SecurityContext → JwtAuthFilter:** el contexto de seguridad queda actualizado y confirma el registro de la autenticación.
4. **JwtAuthFilter → LibroController:** `filterChain.doFilter()` continúa la cadena y Spring despacha la solicitud al método `LibroController.listar(pageable)`.
5. **LibroController → SecurityContext:** el interceptor de `@PreAuthorize` consulta la autenticación actual para validar que el rol del usuario esté permitido.
6. **SecurityContext → LibroController:** devuelve el objeto `Authentication` con los roles del usuario.
7. **LibroController → LibroService:** invoca `LibroService.listar(pageable)`, anotado con `@Cacheable("libros")` y `@Transactional(readOnly = true)`.
8. **LibroService → LibroRepository:** si no hay acierto de caché, llama a `LibroRepository.findByEstado_Nombre("ACTIVO", pageable)`.
9. **LibroRepository → PostgreSQL:** Hibernate genera y ejecuta el `SELECT` sobre `libros` filtrando por estado y paginando/ordenando por título.
10. **PostgreSQL → LibroRepository:** la base de datos devuelve el `ResultSet` con las filas encontradas.
11. **LibroRepository → LibroService:** Spring Data JPA mapea el resultado a `Page<Libro>` y lo retorna al servicio.
12. **LibroService (interno):** convierte cada `Libro` de la página a `LibroResponseDTO` mediante `toDTO()`.
13. **LibroService (interno):** cada conversión retorna el DTO correspondiente al llamador del bucle.
14. **LibroService → LibroController:** retorna `Page<LibroResponseDTO>`; Spring además guarda el resultado en la caché `"libros"`.
15. **LibroController → JwtAuthFilter:** el `ResponseEntity` se serializa a JSON mediante `MappingJackson2HttpMessageConverter.write()` con código `200 OK`.
16. **JwtAuthFilter → Angular:** la respuesta JSON llega finalmente al cliente Angular, que actualiza el listado de libros en pantalla.

