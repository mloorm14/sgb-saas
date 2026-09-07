import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

// Chequeo de rol por ruta (espejo del @PreAuthorize del backend).
// Sin rol → /no-autorizado, sin disparar llamadas condenadas al 403.
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
