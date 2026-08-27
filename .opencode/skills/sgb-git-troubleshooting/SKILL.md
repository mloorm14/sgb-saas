---
name: sgb-git-troubleshooting
description: Resolución de problemas comunes de Git en el proyecto SGB-SaaS, especialmente en Windows — CRLF/LF, autenticación, ramas, stash, y errores de push. Usar SIEMPRE al encontrar un error de Git durante una tarea, o cuando git status muestre cambios inesperados — cubre los problemas más frecuentes del equipo y sus soluciones exactas.
---

# Troubleshooting de Git — SGB-SaaS

## Problema 1: Cientos de archivos "modified" en git status

**Síntoma:** `git status` muestra decenas o cientos de archivos modificados que no tocaste.

**Causa:** Configuración CRLF/LF en Windows. Git convierte automáticamente los saltos de línea.

**Solución:** NO commitees esos cambios. Son de configuración, no de contenido.

```bash
# Verificar si es CRLF
git diff --stat  # Si ves archivos .java, .ts, .html que no tocaste, es CRLF

# Restaurar archivos a su estado original
git checkout -- .
```

**Prevención:** Configurar Git para que no convierta automáticamente:
```bash
git config --global core.autocrlf false
```

## Problema 2: Push falla por autenticación (403)

**Síntoma:** `git push` pide usuario/contraseña o devuelve 403.

**Solución:** NO inventes tokens. Usá el mecanismo ya configurado en el remote:

```bash
# Ver la URL actual del remote
git remote -v

# Si usa HTTPS, verificar credential helper
git config --global credential.helper
```

Si la autenticación falla, PARÁ y devolvé el error al usuario. No edites la URL del remote con credenciales embebidas.

## Problema 3: Merge conflict

**Síntoma:** `git merge` o `git pull` muestra conflictos.

**Solución:**
```bash
# 1. Ver archivos en conflicto
git status

# 2. Abrir cada archivo y buscar marcadores de conflicto
# <<<<<<< HEAD
# tu código
# =======
# código del otro
# >>>>>>> branch-name

# 3. Resolver manualmente, guardar, y stage
git add <archivo-resuelto>

# 4. Completar el merge
git commit
```

**REGLA:** Si la tarea te dice que no toques cierta carpeta, NO hagas merge de esa rama en tu trabajo.

## Problema 4: Quiero guardar cambios temporalmente

```bash
# Guardar cambios sin commit
git stash

# Ver stash guardado
git stash list

# Restaurar stash
git stash pop

# Restaurar sin eliminar del stash
git stash apply
```

## Problema 5: Commiteé algo que no quería

```bash
# Si el commit no está pusheado aún
git reset --soft HEAD~1  # Deshace el commit, mantiene los cambios staged
git reset --mixed HEAD~1 # Deshace el commit, mantiene los cambios sin stage
git reset --hard HEAD~1  # Deshace el commit Y borra los cambios (PELIGROSO)

# Si ya está pusheado, no uses reset — hacé un revert
git revert HEAD
```

## Problema 6: Quiero ver el historial

```bash
# Últimos 10 commits
git log --oneline -10

# Ver cambios de un commit
git show <commit-hash>

# Ver diff de mi rama vs base
git diff demo/interfaces-completas...HEAD
```

## Problema 7: Rama apunta al commit equivocado

```bash
# Ver en qué commit estoy
git log --oneline -1

# Mover mi rama al último commit de otra rama
git reset --hard demo/interfaces-completas
```

## Problema 8: Remote tiene cambios que yo no tengo

```bash
# Traer cambios sin merge
git pull --rebase origin demo/interfaces-completas

# Si hay conflictos durante rebase
git add <archivo>
git rebase --continue
```

## Ramas protegidas

| Rama | Regla |
|------|-------|
| `main` | NUNCA push directo |
| `demo/interfaces-completas` | Solo merge del equipo a mano |
| `feature/*`, `fix/*`, etc. | Tu rama de trabajo — libre |

## Flujo correcto de una tarea

```bash
# 1. Empezar desde la rama base
git checkout demo/interfaces-completas
git pull
git checkout -b feature/mi-tarea

# 2. Trabajar, commitear (conventional commits)
git add .
git commit -m "feat(scope): descripción"

# 3. Terminar — push a origin
git push -u origin feature/mi-tarea

# 4. NO mergear, NO abrir PR, NO push a main
# Avisar al equipo que la rama está lista
```
