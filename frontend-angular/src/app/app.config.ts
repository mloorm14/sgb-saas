import { ApplicationConfig, APP_INITIALIZER } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { jwtInterceptor } from './core/interceptors/jwt.interceptor';
import { loaderInterceptor } from './core/interceptors/loader.interceptor';
import { AuthService } from './core/services/auth.service';

function inicializarSesion(authService: AuthService): () => Promise<boolean> {
  return () => new Promise((resolve) => {
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