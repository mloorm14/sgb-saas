import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

// Defensa en profundidad: el backend ya rechaza estos endpoints con 403
// para quien no tenga el rol (ver UsuarioAdminController/
// ConfiguracionSistemaController), pero sin este guard la ruta igual
// renderizaría el componente y dispararía llamadas HTTP condenadas a
// fallar en vez de redirigir de una. authGuard (autenticado o no) sigue
// aplicando aparte -- este guard solo agrega el chequeo de rol.
//
// Cierra tambien el hallazgo lambda: hoy un LECTOR podia abrir
// /prestamos/gestion aunque el backend lo rechace despues con 403. Cada
// ruta declara los roles que el backend realmente exige en sus
// @PreAuthorize (verificado en los controllers de backend-springboot).
export function roleGuard(rolesPermitidos: string[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    if (rolesPermitidos.some(r => auth.hasRole(r))) {
      return true;
    }

    router.navigate(['/no-autorizado']);
    return false;
  };
}
