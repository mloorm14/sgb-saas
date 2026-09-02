---
name: sgb-dashboard-shells
description: Arquitectura de dashboard shells con sidebar y rutas anidadas por rol en SGB-SaaS. Usar SIEMPRE al agregar una pantalla nueva dentro de un shell, al modificar sidebar o navegación, o al crear rutas hijas — cubre la estructura de los 4 shells, cómo registrar rutas, el patrón de roles visibles, y los errores comunes al salir del shell sin darse cuenta.
---

# Dashboard Shells — SGB-SaaS

## Los 4 shells del proyecto

Cada shell es un componente standalone con un `<nav>` sidebar y un `<router-outlet>` para rutas hijas. El shell se oculta cuando el usuario está en una pantalla fuera de su dashboard.

### 1. Lector Shell

- **Ruta:** `/dashboard-lector`
- **Componente:** `dashboard-lector.component.ts`
- **Rol:** `LECTOR`
- **Sidebar:**
  - BIBLIOTECA: Catálogo, Mis Préstamos, Reservaciones, Multas
  - MI CUENTA: Favoritos, Sugerencias, Notificaciones, Mi Credencial
- **Rutas hijas:** catalogo, catalogo/:id, prestamos, reservaciones, multas, favoritos, sugerencias, sugerencias/nueva, notificaciones, mi-credencial
- **Incluye:** `ChatbotWidgetComponent` (widget flotante)

### 2. Bibliotecario Shell

- **Ruta:** `/dashboard-bibliotecario`
- **Componente:** `dashboard-bibliotecario.component.ts`
- **Rol:** `BIBLIOTECARIO`
- **Sidebar:**
  - Inicio
  - GESTIÓN: Libros, Préstamos, Reservaciones, Devoluciones, Multas
- **Rutas hijas:** (home), libros, prestamos/gestion, reservaciones, devoluciones, multas

### 3. Gerente/Admin Shell (compartido)

- **Ruta:** `/dashboard-admin`
- **Componente:** `dashboard-gerente-admin.component.ts`
- **Rol:** `GERENTE` o `ADMIN`
- **Sidebar:**
  - INICIO
  - GESTIÓN: Libros, Préstamos, Reservaciones, Multas, Sugerencias, Usuarios (solo ADMIN)
  - SISTEMA: Auditoría, Reportes, Configuración (solo ADMIN)
- **Rutas hijas:** (home), libros, prestamos/gestion, reservaciones, multas, sugerencias/gestion, admin/usuarios, auditoria, reportes, admin/configuracion

### 4. Gerente Standalone (sin shell)

- **Ruta:** `/dashboard-gerente`
- **Componente:** `dashboard-gerente.component.ts`
- **Rol:** `GERENTE`
- **NO es shell** — es una página standalone con KPI widgets
- **No tiene sidebar ni router-outlet** — es una sola pantalla

## Guards de autenticación

El proyecto usa guards funcionales (Angular 15+) en `core/guards/`:

```typescript
// auth.guard.ts — verifica token válido y no expirado
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);
  if (authService.isLoggedIn()) {
    if (authService.tokenExpirado()) {
      authService.logout('/login');
      return false;
    }
    return true;
  }
  router.navigate(['/login']);
  return false;
};

// role.guard.ts — higher-order function que retorna CanActivateFn
export function roleGuard(rolesPermitidos: string[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (rolesPermitidos.some(r => auth.hasRole(r))) return true;
    router.navigate(['/no-autorizado']);
    return false;
  };
}
```

**Uso en rutas:** `{ canActivate: [authGuard, roleGuard(['BIBLIOTECARIO'])] }`

## HomeComponent por shell

Cada shell tiene su propio HomeComponent (no hay uno genérico):

| Shell | HomeComponent |
|-------|---------------|
| Bibliotecario | `DashboardBibliotecarioHomeComponent` |
| Gerente/Admin | `DashboardGerenteAdminHomeComponent` |
| Lector | (ruta `''` apunta al catálogo directamente) |

Al crear una ruta hija, el path `''` del shell debe apuntar al
HomeComponent correspondiente.

## Cómo agregar una ruta hija a un shell

### Paso 1: Crear el componente

```bash
# Ejemplo: agregar pantalla "inventario" al shell de Bibliotecario
ng generate component inventario --standalone
```

### Paso 2: Registrar la ruta hija en `app.routes.ts`

```typescript
{
  path: 'dashboard-bibliotecario',
  component: DashboardBibliotecarioComponent,
  canActivate: [authGuard, roleGuard(['BIBLIOTECARIO'])],
  children: [
    { path: '', component: DashboardBibliotecarioHomeComponent },
    { path: 'libros', component: LibrosComponent },
    // ... rutas existentes
    { path: 'inventario', component: InventarioComponent }, // NUEVA
  ]
}
```

### Paso 3: Agregar link en el sidebar del shell

En el HTML del shell correspondiente, agregar el link con roles:

```html
<a routerLink="/dashboard-bibliotecario/inventario"
   routerLinkActive="bg-primary-container text-on-primary-container"
   class="flex items-center gap-3 px-3 py-2 rounded-lg text-sm">
  <span class="material-icons">inventory_2</span>
  Inventario
</a>
```

### Paso 4: Verificar que el routerLink tiene el prefijo completo

```html
<!-- MAL: sale del shell sin que el usuario se dé cuenta -->
<a routerLink="/inventario">...</a>

<!-- BIEN: mantiene dentro del shell -->
<a routerLink="/dashboard-bibliotecario/inventario">...</a>
```

## Patrón de roles visibles en sidebar

Cada link del sidebar puede filtrarse por roles usando `AuthService.hasRole()`:

```typescript
// En el .ts del shell
links = [
  { label: 'Usuarios', route: '/dashboard-admin/admin/usuarios', roles: ['ADMIN'] },
  { label: 'Reportes', route: '/dashboard-admin/reportes', roles: ['GERENTE', 'ADMIN'] },
];

// En el .html
@for (link of links; track link.route) {
  @if (hasAnyRole(link.roles)) {
    <a [routerLink]="link.route" ...>{{ link.label }}</a>
  }
}
```

## Regla de rutas internas

Cualquier `routerLink` interno entre pantallas del MISMO rol debe llevar el prefijo completo del shell:

```html
<!-- MAL -->
<a routerLink="/catalogo">Catálogo</a>

<!-- BIEN -->
<a routerLink="/dashboard-lector/catalogo">Catálogo</a>
```

Si el usuario hace clic en un link sin prefijo, se sale del shell y pierde el sidebar, pero la URL puede parecer que sigue dentro del dashboard.

## Antes de dar por terminada una pantalla

Grepeá tus propios `routerLink` en el HTML del componente para confirmar que ninguno quedó sin el prefijo del shell:

```bash
grep -n "routerLink=" frontend-angular/src/app/<mi-componente>/*.html
```
