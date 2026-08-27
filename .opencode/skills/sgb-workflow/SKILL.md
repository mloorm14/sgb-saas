---
name: sgb-workflow
description: Reglas de proceso Git, formato de commits, y disciplina de testing para el proyecto SGB-SaaS. Usar SIEMPRE al empezar cualquier tarea nueva en este repo, antes del primer commit o push — cubre identidad de Git, rama base, qué está prohibido (PR, merge, push a main), formato de commits, y alcance de tests.
---

# Workflow de SGB-SaaS

## 1. Identidad de Git

Antes del primer commit, confirmá:

```bash
git config user.name
git config user.email
```

Si devuelven algo, usá esa identidad para todos los commits — nunca la
cambies, nunca inventes una identidad ni un correo genérico tipo
"agent@...". Si están vacíos, PARÁ y pedile al usuario que los configure.

## 2. Autenticación para push

No tenés credenciales propias ni las inventes. Usá el mecanismo ya
configurado en el `git remote` de esta máquina. Si `git push` falla por
auth (403, pide usuario/contraseña), NO lo resuelvas vos — parás y le
devolvés el error exacto al usuario.

## 3. Saltos de línea (Windows)

Si `git status`/`git diff` muestra cientos de archivos "modified" que no
tocaste, es casi seguro CRLF/LF, no contenido real. No lo commitees a
ciegas — avisale al usuario, es config de su máquina.

## 4. Rama base

`demo/interfaces-completas` es la rama de referencia (integración más
completa y actualizada). Para cualquier tarea nueva:

```bash
git checkout demo/interfaces-completas
git pull
git checkout -b <feature|fix|refactor|docs>/<nombre-descriptivo>
```

Si la tarea puntual indica otra base (por ejemplo para no pisar trabajo
en curso de un compañero), seguí esa indicación en vez de esta regla.

## 5. Coordinación con el equipo

El repo lo tocan varias personas en paralelo. Si una tarea dice no tocar
cierta carpeta/componente porque otro compañero trabaja ahí activamente,
respetalo aunque veas algo "mejorable" de paso. Ante la duda, preguntá,
no asumas que está libre.

## 6. Qué NUNCA hacer

- NUNCA merge de tu rama a ninguna otra (ni `demo/interfaces-completas`,
  ni `main`, ni la de un compañero).
- NUNCA abras un Pull Request.
- NUNCA push directo a `main`.
- La integración final de ramas la decide el equipo a mano.

Al terminar: dejá tu rama pusheada a `origin` y avisá que está lista
para revisión — nada más.

## 7. Formato de commits

Conventional Commits, siempre título Y cuerpo:

```
git commit -m "tipo(ámbito): título breve

Cuerpo: qué se hizo, por qué, y cualquier decisión que tomaste que no
estaba 100% especificada en la tarea."
```

Un commit por archivo o por cambio lógico. Tipos: `feat`, `fix`,
`refactor`, `test`, `docs`, `chore`.

## 8. Tests

Camino feliz + 1-2 casos de error reales por componente/clase nueva, no
un test por cada combinación posible. Mientras iterás, corré solo el
archivo que estás tocando (`--include='**/archivo.spec.ts'` en frontend,
`-Dtest=NombreClase` en backend). Corré la suite COMPLETA una sola vez
al final, y reportá el TOTAL exacto en el resumen — nunca un número
aproximado.

## 9. Al terminar cualquier tarea

Cerrá con un resumen que incluya: archivos creados/editados (backend y
frontend por separado si tocaste ambos), decisiones tomadas que no
estaban 100% especificadas, TOTAL de tests final y si el build compiló
limpio, y qué quedó como TODO explícito. Si encontrás un gap real
(algo que el proyecto necesita pero no existe todavía), documentalo en
un comentario en el código y seguí adelante — no bloquees la tarea por
eso, pero tampoco lo escondas.
