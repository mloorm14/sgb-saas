import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn()) {
    // Hallazgo epsilon/lambda: un accessToken vencido no debe esperar el
    // 403 del backend para invalidar la sesion. tokenExpirado() reusa la
    // decodificacion del JWT que ya vive en AuthService.
    if (authService.tokenExpirado()) {
      authService.logout(); // limpia sesion y navega a /login
      return false;
    }
    return true;
  }

  router.navigate(['/login']);
  return false;
};