import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { LoaderService } from '../../shared/loader/loader.service';

export const loaderInterceptor: HttpInterceptorFn = (req, next) => {
  const loaderService = inject(LoaderService);

  // No mostrar loader en polling de chatbot, polling liviano ni respaldos (B11: skeletons propios).
  if (req.url.includes('/chatbot/') || req.url.includes('/backups') || req.url.includes('/respaldo-completo')) {
    return next(req);
  }

  loaderService.show();
  return next(req).pipe(finalize(() => loaderService.hide()));
};
