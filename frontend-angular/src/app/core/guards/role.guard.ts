import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

// Defensa en profundidad: el backend ya rechaza estos endpoints con 403
// para quien no tenga el rol (ver UsuarioAdminController/
// ConfiguracionSistemaController), pero sin este guard la ruta igual
// renderizaría el componente y dispararía llamadas HTTP condenadas a
// fallar en vez de redirigir de una. authGuard (autenticado o no) sigue
// aplicando aparte -- este guard solo agrega el chequeo de rol.
export function roleGuard(...allowedRoles: string[]): CanActivateFn {
  return () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (authService.hasRole(...allowedRoles)) {
      return true;
    }

    router.navigate(['/libros']);
    return false;
  };
}
