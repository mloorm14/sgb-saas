import { HttpEvent, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, Observable, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  // Adjunta el Bearer; ante 401/403 por token vencido reintenta una sola vez con token refrescado (marca X-Retry).
  const enviar = (solicitud: HttpRequest<unknown>): Observable<HttpEvent<unknown>> => {
    const token = authService.getAccessToken();
    const authReq = token
      ? solicitud.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : solicitud;

    return next(authReq).pipe(
      catchError((error) => {
        const isAuthEndpoint = solicitud.url.includes('/auth/');
        const yaReintentada = solicitud.headers.has('X-Retry');
        if ((error.status === 401 || error.status === 403) && !isAuthEndpoint && !yaReintentada) {
          // Refresca el token y reintenta una vez con X-Retry; si falla, logout a /login.
          return authService.refresh().pipe(
            switchMap((refreshResp) => {
              const nuevoToken = refreshResp?.accessToken ?? authService.getAccessToken();
              // B7: el retry reenvía la cookie (withCredentials) y el Bearer nuevo.
              return enviar(solicitud.clone({
                withCredentials: true,
                setHeaders: {
                  ...(nuevoToken ? { Authorization: `Bearer ${nuevoToken}` } : {}),
                  'X-Retry': 'true'
                }
              }));
            }),
            catchError((refreshError) => {
              authService.logout('/login');
              router.navigate(['/login']);
              return throwError(() => refreshError);
            })
          );
        }
        return throwError(() => error);
      })
    );
  };

  return enviar(req);
};