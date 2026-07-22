<!--
PLANTILLA — copiar este archivo para cada evidencia/medición nueva, no
editar esta plantilla in situ para un resultado concreto. Ver
docs/mediciones/README.md para la convención de nombre de archivo.

Antes de llenar el resto de secciones, generar la cabecera con:
    ./scripts/mediciones-header.sh >> docs/mediciones/<subcarpeta>/<archivo>.md
y luego completar manualmente el resto de este archivo debajo de esa
cabecera (o pegar el bloque generado en el lugar de "Cabecera de medición"
de abajo si se está escribiendo el archivo directamente).
-->

# Evidencia — <título corto y descriptivo de qué se está midiendo/verificando>

## Cabecera de medición

<!-- Reemplazar este bloque por la salida real de scripts/mediciones-header.sh -->
- **Fecha (ISO 8601 UTC)**: YYYY-MM-DDTHH:MM:SSZ
- **Commit**: `hash-corto`
- **Docker**: ...
- **Docker Compose**: ...
- **Java**: ...
- **Maven**: ...
- **PostgreSQL** (contenedor `sgb_postgres`): ...
- **Redis** (contenedor `sgb_redis`): ...

## Propósito

<!--
Qué requisito de la guía motiva esta medición (ej. "Bloque A.1 — cookie
HttpOnly", "Bloque C.1 — prueba de carga 50 VUs", "Bloque C.2 — auditoría
OWASP control A02"). Enlazar al ADR relacionado si existe uno.
-->

## Metodología / comando ejecutado

<!--
Comando(s) EXACTOS ejecutados, en un bloque de código, tal como se
corrieron (no una paráfrasis). Si el script/comando genera datos
aleatorios o simulados, documentar aquí la semilla fija usada (ver
requisito de semilla en docs/mediciones/README.md) — sin semilla
documentada, el resultado no es reproducible y no cuenta como evidencia
válida para el Bloque B.2.
-->

```bash
# comando aquí
```

## Resultados crudos

<!--
Output real y completo (o el fragmento representativo si es muy largo,
indicando que se truncó y dónde encontrar el resto) — sin editar ni
parafrasear. Cabeceras HTTP completas si aplica, salida de redis-cli/psql
tal cual, etc.
-->

```
(pegar aquí)
```

## Análisis breve

<!--
2-5 líneas: qué confirma este resultado, qué NO cubre (limitaciones), y
cualquier hallazgo colateral que haya surgido durante la verificación
(documentar honestamente incluso si no era el objetivo original de esta
medición — así se hizo con los hallazgos de PageImpl/Redis y
AuthorizationDeniedException en mediciones anteriores).
-->
