import { ResolveFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, of, timeout } from 'rxjs';
import { LibroPublicoService } from '../services/libro-publico.service';
import { ToastService } from '../../shared/toast/toast.service';
import { Libro } from '../models/libro.model';

export const libroPublicoDetalleResolver: ResolveFn<Libro | null> = (route) => {
  const svc = inject(LibroPublicoService);
  const toast = inject(ToastService);
  const router = inject(Router);
  const id = Number(route.paramMap.get('id'));
  if (!id) {
    router.navigate(['/']);
    return of(null);
  }
  return svc.obtener(id).pipe(
    timeout(15000),
    catchError((err: HttpErrorResponse) => {
      if (err.status === 404) {
        toast.warning('No encontrado', 'El libro solicitado no existe');
        router.navigate(['/']);
      } else {
        toast.error('Error', 'No se pudo cargar el libro. Intente nuevamente');
      }
      return of(null);
    })
  );
};
