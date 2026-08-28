import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map, catchError, of } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn() && !authService.tokenExpirado()) {
    return true;
  }

  // Sin accessToken (F5) o con JWT vencido: la cookie de refresh sigue
  // viva. Pedir uno nuevo en vez de logout, que borraría esa cookie.
  return authService.refresh().pipe(
    map(() => true),
    catchError(() => {
      router.navigate(['/login']);
      return of(false);
    })
  );
};