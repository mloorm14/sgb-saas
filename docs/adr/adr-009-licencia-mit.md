# ADR-009: Licencia MIT para el proyecto

## Title

Adopción de la licencia MIT para SGB-SaaS.

## Context

SGB-SaaS es un proyecto académico (PFC de UTEQ, Tercera Entrega) desarrollado
por tres integrantes (Marlon Loor Medranda, Irvin Cajas Ibarra, Moises
Panama Murillo). El plan es publicar el repositorio con un DOI en Zenodo
como parte de la sustentación y del portafolio del equipo, lo cual requiere
una licencia de código abierto explícita en la raíz del repositorio — sin
`LICENSE`, el código queda bajo "todos los derechos reservados" por
defecto, lo que impide legalmente que terceros (evaluadores, futuros
lectores del DOI, otros estudiantes) lo reutilicen o lo bifurquen aunque el
repositorio sea públicamente visible.

No hay ningún requisito del proyecto (ni de la guía de la Tercera Entrega,
ni de Zenodo) que exija cláusulas de patentes, copyleft, o compatibilidad
con un ecosistema de licencias corporativo. Es software académico sin
intención comercial ni de integrarse como dependencia de otro proyecto con
requisitos legales complejos.

## Decision

Se adopta la **licencia MIT**, aplicada en `LICENSE` (raíz del repo) con
copyright a nombre de los tres integrantes del equipo, año 2026.

## Alternativas consideradas

- **Apache License 2.0**: incluye una cláusula explícita de concesión de
  patentes (protección adicional si el software terminara usado en un
  contexto con disputas de patentes) y un aviso de cambios más formal en
  redistribuciones modificadas. Descartada aquí porque ese nivel de
  protección legal no aporta nada a un proyecto académico sin patentes
  involucradas ni expectativa de litigio — solo añade texto legal más largo
  sin beneficio práctico para este caso.
- **GPL-3.0 (copyleft)**: obligaría a que cualquier trabajo derivado se
  publique también bajo GPL. Descartada porque el objetivo es máxima
  permisividad para que otros estudiantes o evaluadores puedan reutilizar
  fragmentos del código libremente (incluso en proyectos privados o con
  otra licencia), sin imponer condiciones sobre cómo debe licenciarse el
  trabajo derivado.
- **Sin licencia explícita (todos los derechos reservados por defecto)**:
  descartada porque es incompatible con el objetivo de publicar con DOI en
  Zenodo de forma abierta — un DOI sin licencia clara deja en ambigüedad
  legal si el material puede citarse, reutilizarse o bifurcarse.

## Status

Aceptado e implementado (`LICENSE` en la raíz).

## Consequences

**Positivas:**

- MIT es la licencia más reconocida y de menor fricción en el ecosistema
  de software abierto/académico: cualquiera que revise el repositorio (para
  la sustentación, para Zenodo, o casualmente en GitHub) entiende de
  inmediato los términos sin necesitar asesoría legal.
- Máxima permisividad: no impone restricciones sobre cómo se reutiliza el
  código (uso comercial, modificación, redistribución bajo otra licencia),
  alineado con el espíritu académico de "que sirva de referencia a otros".
- Habilita la publicación con DOI en Zenodo sin ambigüedad legal sobre los
  derechos de reutilización.

**Negativas:**

- Sin cláusula de patentes (a diferencia de Apache-2.0): si en el futuro el
  proyecto involucrara patentes de terceros, MIT no ofrece la protección
  explícita que sí da Apache-2.0. Riesgo aceptado como irrelevante para el
  alcance actual (proyecto académico, sin patentes involucradas).
- MIT no es copyleft: un tercero podría tomar el código, modificarlo y
  redistribuirlo bajo una licencia más restrictiva sin obligación de
  compartir esos cambios de vuelta. Aceptado deliberadamente — no es un
  objetivo de este proyecto forzar reciprocidad.

## Referencias

- Texto completo de la licencia: `LICENSE`
- [Zenodo — GitHub/Software Preservation](https://zenodo.org) (requiere
  licencia explícita en el repositorio para el flujo de publicación con DOI)
