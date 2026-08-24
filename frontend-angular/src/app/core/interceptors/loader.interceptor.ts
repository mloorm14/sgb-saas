import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { LoaderService } from '../../shared/loader/loader.service';

export const loaderInterceptor: HttpInterceptorFn = (req, next) => {
  const loaderService = inject(LoaderService);

  // No mostrar loader en polling de chatbot o polling liviano
  if (req.url.includes('/chatbot/')) {
    return next(req);
  }

  loaderService.show();
  return next(req).pipe(finalize(() => loaderService.hide()));
};
