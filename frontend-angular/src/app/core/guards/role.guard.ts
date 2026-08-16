import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

// Cierra el hallazgo lambda: hoy un LECTOR podia abrir /prestamos/gestion
// aunque el backend lo rechace despues con 403. Cada ruta declara los roles
// que el backend realmente exige en sus @PreAuthorize (verificado en los
// controllers de backend-springboot).
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