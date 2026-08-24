import { HttpEvent, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, Observable, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  // Hallazgo δ del reporte: el refresh solo se disparaba con 401, pero un
  // accessToken vencido también produce 403 en endpoints protegidos
  // (25519/spring security responde 403 para tokens expirados en el filtro,
  // y el backend sin AuthenticationEntryPoint no distingue). Además, sin
  // guardia anti-bucle un 403 real por falta de rol (no por token vencido)
  // reintentaría indefinidamente tras cada refresh. La recursión pasa la
  // request clonada con el header interno X-Retry: el segundo 401/403 ya
  // con esa marca se propaga tal cual -- se refresca UNA sola vez y si
  // sigue fallando es legítimamente "no autorizado", no "sesión vencida".
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
          // Antes de desloguear: intentamos refrescar el accessToken con
          // el refreshToken de la cookie HttpOnly. Si funciona,
          // reintentamos la request original una sola vez con el token
          // nuevo y la marca X-Retry puesta. Si el refresh falla, la
          // sesión sí venció: logout + redirección a login.
          return authService.refresh().pipe(
            switchMap(() =>
              enviar(solicitud.clone({ setHeaders: { 'X-Retry': 'true' } }))
            ),
            catchError((refreshError) => {
              authService.logout();
              router.navigate(['/']);
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