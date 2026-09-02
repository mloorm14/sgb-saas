import { ApplicationConfig, APP_INITIALIZER } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { jwtInterceptor } from './core/interceptors/jwt.interceptor';
import { loaderInterceptor } from './core/interceptors/loader.interceptor';
import { AuthService } from './core/services/auth.service';

// El accessToken vive solo en memoria. Tras F5 hay que pedir uno nuevo
// con la cookie HttpOnly de refresh *antes* de que authGuard evalúe la
// ruta. Si no hay cookie (visita anónima), el 400/401 se ignora y la
// app arranca igual.
export function inicializarSesion(authService: AuthService): () => Promise<boolean> {
  return () => new Promise((resolve) => {
    if (authService.isLoggedIn()) {
      resolve(true);
      return;
    }
    authService.refresh().subscribe({
      next: () => resolve(true),
      error: () => resolve(true)
    });
  });
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(withInterceptors([jwtInterceptor, loaderInterceptor])),
    {
      provide: APP_INITIALIZER,
      useFactory: inicializarSesion,
      deps: [AuthService],
      multi: true
    }
  ]
};