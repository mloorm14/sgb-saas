---
name: sgb-external-api-proxy
description: Patrones de integración con APIs externas (Google Books, Gemini, SMTP) en SGB-SaaS. Usar SIEMPRE al agregar una llamada a API externa, al modificar el proxy de portadas, o al configurar el chatbot — cubre el patrón de proxy backend para CSP, RestClient de Spring Boot, manejo de errores, y la configuración de Gemini.
---

# APIs Externas y Proxy — SGB-SaaS

## Regla de oro: CSP estricta

El frontend NUNCA llama directamente a APIs externas. La Content-Security-Policy (`frontend-angular/public/_headers`) bloquea recursos externos. Toda llamada a API externa pasa por el backend como proxy.

## APIs existentes en el proyecto

### 1. Google Books API (proxy de portadas)

**Archivo:** `LibroIsbnLookupService.java`

El backend busca información de libros por ISBN y sirve las portadas como proxy para que el frontend nunca llame a Google directamente.

**Endpoint de proxy de portada:**
```
GET /api/v1/libros/lookup-isbn/portada?isbn={isbn}
```

El backend descarga la imagen de Google Books y la sirve al frontend con el `Content-Type` correcto. Si Google no tiene portada, retorna 404.

**Patrón de proxy:**
```java
@Service
public class LibroIsbnLookupService {

    private final RestClient restClient;

    public byte[] obtenerPortadaPorIsbn(String isbn) {
        try {
            byte[] imagen = restClient.get()
                .uri("https://www.googleapis.com/books/v1/volumes?q=isbn:{isbn}", isbn)
                .retrieve()
                .body(/* ... extraer thumbnailImageLinks */);
            return imagen;
        } catch (Exception e) {
            throw new NotFoundException("Portada no encontrada para ISBN: " + isbn);
        }
    }
}
```

### 2. Google Gemini API (chatbot)

**Archivo:** `GeminiClient.java`

Integración con Gemini para el chatbot del proyecto. Soporta function calling (tools) vía `ChatbotOrchestrator`.

**Configuración (application.properties):**
```properties
app.gemini.api-key=${GEMINI_API_KEY}
app.gemini.modelo=gemini-2.0-flash
app.gemini.url-base=https://generativelanguage.googleapis.com/v1beta
app.gemini.timeout-ms=30000
```

**Patrón de uso:**
```java
@Component
public class GeminiClient {

    private final RestClient restClient;
    private final GeminiProperties properties;

    public String generarContenido(String prompt) {
        return restClient.post()
            .uri("{url}/models/{model}:generateContent?key={key}",
                properties.getUrlBase(), properties.getModelo(), properties.getApiKey())
            .body(new GeminiRequest(prompt))
            .retrieve()
            .body(GeminiResponse.class)
            .getCandidates()[0].getContent().getParts()[0].getText();
    }
}
```

**Rate limiting:** `ChatbotRateLimiter` controla la frecuencia de llamadas por usuario.

### 3. SMTP (email)

**Archivo:** `EmailService.java`

Envío de emails para alertas de vencimiento, verificación de correo, y notificaciones.

```properties
spring.mail.host=${SMTP_HOST}
spring.mail.port=${SMTP_PORT}
spring.mail.username=${SMTP_USERNAME}
spring.mail.password=${SMTP_PASSWORD}
```

## Cómo agregar una nueva llamada a API externa

### Paso 1: Crear el cliente con RestClient

```java
@Component
public class MiNuevoCliente {

    private final RestClient restClient;

    public MiNuevoCliente(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("https://api.externa.com").build();
    }

    public MiResponse consultar(String param) {
        try {
            return restClient.get()
                .uri("/endpoint/{param}", param)
                .retrieve()
                .body(MiResponse.class);
        } catch (Exception e) {
            throw new ExternalApiException("Error consultando API externa: " + e.getMessage());
        }
    }
}
```

### Paso 2: Crear DTOs para la respuesta

```java
public record MiResponse(String campo1, String campo2) {}
```

### Paso 3: Agregar configuración en application.properties

```properties
app.mi-api.url-base=${MI_API_URL}
app.mi-api.api-key=${MI_API_KEY}
app.mi-api.timeout-ms=10000
```

### Paso 4: Crear endpoint proxy en el controller

```java
@RestController
@RequestMapping("/api/v1/mi-recurso")
public class MiRecursoController {

    @GetMapping("/externo/{param}")
    public ResponseEntity<MiResponse> consultarExterno(@PathVariable String param) {
        return ResponseEntity.ok(miNuevoCliente.consultar(param));
    }
}
```

### Paso 5: Usar el service desde el frontend

```typescript
// En el service de Angular
obtenerDatoExterno(param: string): Observable<MiResponse> {
    return this.http.get<MiResponse>(`${environment.apiUrl}/mi-recurso/externo/${param}`)
        .pipe(catchError(this.handleError));
}
```

## Reglas para APIs externas

- NUNCA exponer URLs de APIs externas al frontend — siempre proxy backend.
- NUNCA hardcodear API keys en el código — usar variables de entorno.
- Siempre configurar timeouts para no bloquear el thread del servidor.
- Manejar errores de la API externa y mapearlos a ProblemDetail.
- Considerar rate limiting si la API externa lo tiene.
- El backend debe cachear respuestas de APIs externas cuando sea posible (Redis con TTL).
