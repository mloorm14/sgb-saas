import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Libro, LibroSugerencia } from '../models/libro.model';
import { Page } from '../models/pagina.model';
import { ProblemDetail } from '../models/problem-detail.model';

// Rama C (portal público): solo lectura contra /api/publico/libros, que
// SecurityConfig deja pasar sin JWT (permitAll sobre /api/publico/**).
// Los DTO son exactamente los mismos que /api/v1/libros (LibroResponseDTO,
// LibroSugerenciaDTO), por eso se reutilizan Libro y LibroSugerencia.
export interface LibroPublicoListarParams {
  page?: number;
  size?: number;
  sort?: string;
  categoriaId?: number;
  autorId?: number;
}

@Injectable({
  providedIn: 'root'
})
export class LibroPublicoService {

  private apiUrl = `${environment.apiUrl}/publico/libros`;

  constructor(private http: HttpClient) {}

  listar(params: LibroPublicoListarParams = {}): Observable<Page<Libro>> {
    let httpParams = new HttpParams();
    if (params.page !== undefined) httpParams = httpParams.set('page', params.page);
    if (params.size !== undefined) httpParams = httpParams.set('size', params.size);
    if (params.sort) httpParams = httpParams.set('sort', params.sort);
    if (params.categoriaId !== undefined) httpParams = httpParams.set('categoriaId', params.categoriaId);
    if (params.autorId !== undefined) httpParams = httpParams.set('autorId', params.autorId);

    return this.http.get<Page<Libro>>(this.apiUrl, { params: httpParams }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  sugerencias(texto: string): Observable<LibroSugerencia[]> {
    return this.http.get<LibroSugerencia[]>(`${this.apiUrl}/sugerencias`, { params: new HttpParams().set('texto', texto) }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  obtener(id: number): Observable<Libro> {
    return this.http.get<Libro>(`${this.apiUrl}/${id}`).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // GET /publico/libros/{id}/portada: endpoint público SIN header
  // Authorization, así que (a diferencia de LibroService.obtenerPortada,
  // que resuelve Blob por el interceptor de JWT) se usa directo en <img src>.
  // El img-src del CSP en public/_headers incluye el origen del API.
  portadaUrl(id: number): string {
    return `${this.apiUrl}/${id}/portada`;
  }

  private manejarError(err: unknown): Observable<never> {
    const problem = (err as { error?: ProblemDetail })?.error;
    if (problem?.status) {
      console.warn(`[libro-publico.service] ${problem.title} (${problem.status}): ${problem.detail}`);
    }
    return throwError(() => err);
  }
}