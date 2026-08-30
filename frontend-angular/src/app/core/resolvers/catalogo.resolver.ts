import { ResolveFn } from '@angular/router';
import { inject } from '@angular/core';
import { forkJoin, of, EMPTY, timeout, catchError } from 'rxjs';
import { LibroService } from '../services/libro.service';
import { CategoriaService } from '../services/categoria.service';
import { ToastService } from '../../shared/toast/toast.service';

export const catalogoResolver: ResolveFn<any> = () => {
  const libroService = inject(LibroService);
  const categoriaService = inject(CategoriaService);
  const toast = inject(ToastService);
  return forkJoin({
    libros: libroService.listar({ page: 0, size: 10, sort: 'titulo,asc' }),
    categorias: categoriaService.listar()
  }).pipe(
    timeout(90000),
    catchError(() => {
      toast.warning('Carga demorada', 'Se demoro mucho al cargar los datos, intentalo de nuevo');
      return EMPTY;
    })
  );
};
