import { ResolveFn } from '@angular/router';
import { inject } from '@angular/core';
import { EMPTY, timeout, catchError, of } from 'rxjs';
import { LibroService } from '../services/libro.service';
import { ToastService } from '../../shared/toast/toast.service';

export const libroDetalleResolver: ResolveFn<any> = (route) => {
  const libroService = inject(LibroService);
  const toast = inject(ToastService);
  const id = Number(route.paramMap.get('id'));
  if (!id) return of(null);
  return libroService.obtener(id).pipe(
    timeout(90000),
    catchError(() => {
      toast.warning('Carga demorada', 'Se demoro mucho al cargar los datos, intentalo de nuevo');
      return EMPTY;
    })
  );
};
