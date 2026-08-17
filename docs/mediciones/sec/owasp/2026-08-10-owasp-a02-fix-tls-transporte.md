# Evidencia — OWASP A02:2021 Fallos criptográficos — TLS en tránsito (Módulo 10.1)

## Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-08-10T00:00:00Z
- **Rama**: `feature/seguridad-transporte`
- **Entorno**: revisión de código/configuración local (sin Docker/Postgres/
  Redis disponibles en este entorno de edición — ver limitación declarada
  abajo, mismo criterio que el archivo de hallazgo original).

## Referencia al gap original

Este archivo **no reemplaza ni edita**
[`2026-07-30-owasp-a02-fallo-criptografico.md`](2026-07-30-owasp-a02-fallo-criptografico.md).
Documenta qué parte de ese gap se cierra en esta rama y cuál sigue
pendiente, con la misma honestidad declarada ahí: no se fabrica evidencia
de TLS funcionando donde no hay TLS activo.

## Qué NO se cierra en esta rama (sigue pendiente, a propósito)

TLS real (certificado, `https://` funcionando end-to-end) sigue **fuera
de alcance**: el propio hallazgo original ya establece que un entorno
públicamente accesible con TLS es requisito de la Entrega Final, no de
esta Tercera Entrega. Nada en esta rama activa `server.ssl.*` ni certificados.

## Qué SÍ se cierra en esta rama: la decisión y la preparación del código

1. **Decisión documentada** — `docs/adr/adr-015-tls-transporte.md`: TLS
   termina en el proxy (Nginx del frontend en desarrollo / balanceador en
   producción), no en el backend Spring Boot. Antes de esta rama, esa
   decisión no estaba escrita en ningún lado — solo se sabía, por omisión,
   que hoy no hay TLS.
2. **`server.forward-headers-strategy: framework`** agregado en
   `application.yml` — el backend ahora confía en `X-Forwarded-Proto` del
   proxy. Esto es directamente análogo al caso ya verificado en el
   hallazgo original con el atributo `Secure` de la cookie de refresh
   token: código listo desde antes de tener TLS activo, para que activarlo
   después no requiera un release de la aplicación.

## Metodología / verificación realizada

Revisión estática del `SecurityFilterChain` (`SecurityConfig.java`) y de
`HeadersConfigurer`/`HstsHeaderWriter` de Spring Security 6: el escritor de
`Strict-Transport-Security` ya está activo por defecto (confirmado en el
propio hallazgo `2026-07-30-owasp-a05-mala-configuracion-seguridad.md`,
que documenta que el header no aparece **solo** porque `request.isSecure()`
da `false` sobre HTTP plano). No se agregó código nuevo para HSTS en sí —
solo la condición (`forward-headers-strategy`) que permite que ese writer
ya existente reconozca una request como segura cuando de verdad venga de
un proxy TLS-terminating.

Verificación pendiente **para cuando exista un proxy TLS real** (dev con
certificado autofirmado o producción): repetir el mismo comando que
`2026-07-30-owasp-a05-mala-configuracion-seguridad.md` usó como control —

```bash
curl -I -s https://<host-detras-del-proxy>/actuator/health
```

— y confirmar que `Strict-Transport-Security` aparece en la respuesta sin
tocar código de la aplicación otra vez.

## Estado

- **Decisión de arquitectura (dónde termina TLS)**: **CERRADO** —
  documentada en ADR-015.
- **Preparación del backend para reconocer TLS del proxy**: **CERRADO** —
  `forward-headers-strategy: framework`.
- **TLS real activo end-to-end**: **GAP CONOCIDO, diferido a Entrega
  Final**, sin cambios respecto al hallazgo original.
