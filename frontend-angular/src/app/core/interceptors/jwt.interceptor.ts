import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const token = authService.getAccessToken();

  const authReq = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authReq).pipe(
    catchError((error) => {
      const isAuthEndpoint = req.url.includes('/auth/');
      if (error.status === 401 && !isAuthEndpoint) {
        // Antes de desloguear: intentamos refrescar el accessToken con
        // el refreshToken de la cookie HttpOnly. Si funciona, reintentamos
        // la request original una sola vez con el token nuevo.
        return authService.refresh().pipe(
          switchMap(() => {
            const nuevoToken = authService.getAccessToken();
            const reintento = req.clone({ setHeaders: { Authorization: `Bearer ${nuevoToken}` } });
            return next(reintento);
          }),
          catchError((refreshError) => {
            authService.logout();
            router.navigate(['/login']);
            return throwError(() => refreshError);
          })
        );
      }
      return throwError(() => error);
    })
  );
};