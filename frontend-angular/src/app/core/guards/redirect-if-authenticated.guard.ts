import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

export const redirectIfAuthenticatedGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isLoggedIn()) return true;
  if (auth.hasRole('ADMIN')) return router.createUrlTree(['/dashboard-admin']);
  if (auth.hasRole('GERENTE')) return router.createUrlTree(['/dashboard-gerente']);
  if (auth.hasRole('BIBLIOTECARIO')) return router.createUrlTree(['/dashboard-bibliotecario']);
  if (auth.hasRole('LECTOR')) return router.createUrlTree(['/dashboard-lector']);
  return router.createUrlTree(['/dashboard-lector']);
};
