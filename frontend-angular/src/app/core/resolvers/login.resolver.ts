import { ResolveFn } from '@angular/router';
import { of, timeout, catchError, EMPTY, delay } from 'rxjs';
import { inject } from '@angular/core';
import { ToastService } from '../../shared/toast/toast.service';

export const loginResolver: ResolveFn<any> = () => {
  const toast = inject(ToastService);
  return of(true).pipe(
    delay(100),
    timeout(90000),
    catchError(() => {
      toast.warning('Carga demorada', 'Se demoro mucho al cargar los datos, intentalo de nuevo');
      return EMPTY;
    })
  );
};
