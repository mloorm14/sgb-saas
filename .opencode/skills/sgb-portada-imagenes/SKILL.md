---
name: sgb-portada-imagenes
description: Manejo de imágenes de portada de libros en SGB-SaaS — upload multipart, almacenamiento BYTEA en PostgreSQL, componente PortadaLibroComponent, y límites configurables. Usar SIEMPRE al modificar la subida, visualización o eliminación de portadas de libros — cubre el flujo completo backend+frontend y las restricciones de tipo/tamaño.
---

# Imágenes de Portada — SGB-SaaS

## Flujo completo

```
Frontend (File) → FormData POST → Backend (MultipartFile) → BYTEA en PostgreSQL
                                                                     ↓
Frontend ← Blob response ← Backend (byte[]) ← SELECT portada_imagen
```

## Backend

### Migración (V13)

```sql
-- database/migrations/V13__portada_imagen.sql
ALTER TABLE libros ADD COLUMN portada_imagen BYTEA;
ALTER TABLE libros ADD COLUMN portada_nombre VARCHAR(255);
ALTER TABLE libros ADD COLUMN portada_tipo VARCHAR(50);
ALTER TABLE libros ADD COLUMN portada_tamanio BIGINT;
ALTER TABLE configuracion_sistema ADD COLUMN max_tamano_portada_mb INT DEFAULT 2;
```

### Endpoints

| Método | URL | Descripción |
|--------|-----|-------------|
| `GET` | `/api/v1/libros/{id}/portada` | Sirve la imagen con Content-Type dinámico |
| `POST` | `/api/v1/libros/{id}/portada` | Sube nueva portada (multipart) |
| `DELETE` | `/api/v1/libros/{id}/portada` | Elimina portada |
| `GET` | `/api/v1/libros/lookup-isbn/portada?isbn=` | Proxy Google Books (ver sgb-external-api-proxy) |

### Tipos permitidos

`image/png`, `image/jpeg`, `image/webp`, `image/avif`

### Detección de Content-Type al servir

El controller de portada detecta el tipo MIME desde los bytes reales
de la imagen (no confía en el nombre de archivo):

```java
@GetMapping("/{id}/portada")
public ResponseEntity<byte[]> obtenerPortada(@PathVariable Long id) {
    byte[] imagen = libroService.obtenerPortada(id);
    String contentType = detectarContentType(imagen);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(contentType))
        .body(imagen);
}

private String detectarContentType(byte[] bytes) {
    if (bytes.length < 4) return "application/octet-stream";
    // PNG: 89 50 4E 47
    if (bytes[0] == (byte) 0x89 && bytes[1] == 0x50) return "image/png";
    // JPEG: FF D8 FF
    if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) return "image/jpeg";
    // WebP: RIFF....WEBP
    if (bytes[0] == 0x52 && bytes[8] == 0x57) return "image/webp";
    // AVIF: ....ftypavif
    if (bytes.length > 12 && new String(bytes, 8, 4).equals("ftypavif")) return "image/avif";
    return "application/octet-stream";
}
```

**REGLA:** NUNCA confiés en `archivo.getContentType()` del
`MultipartFile` para guardar el tipo — el browser puede mandar
`application/octet-stream`. Detectar desde los bytes.

### Límite de tamaño

Configurable via tabla `configuracion_sistema` con key `max_tamano_portada_mb` (default: 2 MB).

### Patrón de subida en el controller

```java
@PostMapping("/{id}/portada")
public ResponseEntity<Void> subirPortada(
        @PathVariable Long id,
        @RequestParam("archivo") MultipartFile archivo) {

    // Validar tipo
    if (!List.of("image/png", "image/jpeg", "image/webp", "image/avif")
            .contains(archivo.getContentType())) {
        throw new BadRequestException("Tipo de archivo no permitido");
    }

    // Validar tamaño
    long maxSize = configuracionService.obtenerEntero("max_tamano_portada_mb") * 1024 * 1024;
    if (archivo.getSize() > maxSize) {
        throw new BadRequestException("Archivo excede el tamaño máximo permitido");
    }

    libroService.guardarPortada(id, archivo);
    return ResponseEntity.ok().build();
}
```

### Patrón de servicio

```java
public void guardarPortada(Long libroId, MultipartFile archivo) {
    Libro libro = libroRepo.findById(libroId)
        .orElseThrow(() -> new NotFoundException("Libro no encontrado"));

    try {
        libro.setPortadaImagen(archivo.getBytes());
        libro.setPortadaNombre(archivo.getOriginalFilename());
        libro.setPortadaTipo(archivo.getContentType());
        libro.setPortadaTamanio(archivo.getSize());
        libroRepo.save(libro);
    } catch (IOException e) {
        throw new InternalException("Error al guardar portada: " + e.getMessage());
    }
}
```

## Frontend

### PortadaLibroComponent

**Archivo:** `frontend-angular/src/app/shared/portada-libro/portada-libro.component.ts`

Componente reutilizable que muestra la portada de un libro o un placeholder.

**Inputs:**
- `libroId: number` — ID del libro
- `tienePortada: boolean` — si el libro tiene portada (evita llamada innecesaria)

**Patrón de carga:**
```typescript
ngOnInit() {
    if (this.tienePortada && this.libroId) {
        this.libroService.obtenerPortada(this.libroId).subscribe(blob => {
            this.portadaUrl = URL.createObjectURL(blob);
        });
    }
}

ngOnDestroy() {
    if (this.portadaUrl) {
        URL.revokeObjectURL(this.portadaUrl);
    }
}
```

**REGLAS CRÍTICAS:**
- SIEMPRE llamar `URL.revokeObjectURL()` en `ngOnDestroy` para evitar memory leaks.
- Si `tienePortada` es `false`, no hacer la llamada HTTP — mostrar placeholder directamente.
- Mostrar animación de loading mientras se carga la imagen.

### Service de portada

```typescript
obtenerPortada(libroId: number): Observable<Blob> {
    return this.http.get(`${environment.apiUrl}/libros/${libroId}/portada`, {
        responseType: 'blob'
    });
}

subirPortada(libroId: number, archivo: File): Observable<any> {
    const formData = new FormData();
    formData.append('archivo', archivo);
    return this.http.post(`${environment.apiUrl}/libros/${libroId}/portada`, formData);
}
```

## Reglas

- NUNCA usar `[value]` en el input de archivo — usar el nativo del browser.
- NUNCA mostrar la URL de Google Books directamente en un `<img src>` — siempre proxy backend.
- NUNCA hacer `revokeObjectURL` antes de que el componente se destruya (puede causar imagen rota).
- Si se sube una nueva portada, la anterior se sobrescribe (no hay historial).
- El placeholder por defecto es el ícono `menu_book` de Material Icons.
