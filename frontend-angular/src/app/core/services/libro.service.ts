import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Libro, LibroRequest, LibroSugerencia, LibroIsbnLookup } from '../models/libro.model';
import { Page } from '../models/pagina.model';
import { ProblemDetail } from '../models/problem-detail.model';

export interface LibroListarParams {
  page?: number;
  size?: number;
  sort?: string;
  q?: string;
  estadoLibroId?: number;
  categoriaId?: number;
  autorId?: number;
}

@Injectable({
  providedIn: 'root'
})
export class LibroService {

  private apiUrl = `${environment.apiUrl}/v1/libros`;

  constructor(private http: HttpClient) {}

  listar(params: LibroListarParams = {}): Observable<Page<Libro>> {
    let httpParams = new HttpParams();
    if (params.page !== undefined) httpParams = httpParams.set('page', params.page);
    if (params.size !== undefined) httpParams = httpParams.set('size', params.size);
    if (params.sort) httpParams = httpParams.set('sort', params.sort);
    if (params.q) httpParams = httpParams.set('q', params.q);
    if (params.estadoLibroId !== undefined) httpParams = httpParams.set('estadoLibroId', params.estadoLibroId);
    if (params.categoriaId !== undefined) httpParams = httpParams.set('categoriaId', params.categoriaId);
    if (params.autorId !== undefined) httpParams = httpParams.set('autorId', params.autorId);

    return this.http.get<Page<Libro>>(this.apiUrl, { params: httpParams }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  listarPendientes(params: { q?: string; anioPublicacion?: number; page?: number; size?: number; estadoIds?: number[] } = {}): Observable<Page<Libro>> {
    let httpParams = new HttpParams();
    if (params.q) httpParams = httpParams.set('q', params.q);
    if (params.anioPublicacion !== undefined) httpParams = httpParams.set('anioPublicacion', params.anioPublicacion);
    if (params.page !== undefined) httpParams = httpParams.set('page', params.page);
    if (params.size !== undefined) httpParams = httpParams.set('size', params.size);
    if (params.estadoIds && params.estadoIds.length > 0) {
      params.estadoIds.forEach(id => httpParams = httpParams.append('estadoIds', id));
    }
    return this.http.get<Page<Libro>>(`${this.apiUrl}/pendientes`, { params: httpParams }).pipe(
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

  crear(dto: LibroRequest): Observable<Libro> {
    return this.http.post<Libro>(this.apiUrl, dto).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  actualizar(id: number, dto: LibroRequest): Observable<Libro> {
    return this.http.put<Libro>(`${this.apiUrl}/${id}`, dto).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // GET /v1/libros/{id}/portada: binario con Content-Type dinamico. No puede
  // usarse directo en <img src> porque requiere el header Authorization.
  obtenerPortada(id: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${id}/portada`, { responseType: 'blob' }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // POST /v1/libros/{id}/portada: multipart con el campo "archivo". El
  // navegador pone el boundary del multipart; no fijar Content-Type a mano.
  subirPortada(id: number, archivo: File): Observable<Libro> {
    const formData = new FormData();
    formData.append('archivo', archivo);
    return this.http.post<Libro>(`${this.apiUrl}/${id}/portada`, formData).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // GET /v1/libros/lookup-isbn?isbn= (autocompletar del inventario desde
  // Google Books). 404 con ProblemDetail si no hay resultado.
  buscarPorIsbn(isbn: string): Observable<LibroIsbnLookup> {
    return this.http.get<LibroIsbnLookup>(`${this.apiUrl}/lookup-isbn`, { params: new HttpParams().set('isbn', isbn) }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // GET /v1/libros/lookup-isbn/portada?isbn=: proxy de la portada de
  // Google Books (el backend descarga el thumbnail, el navegador no llama
  // a Google directo). Blob para el preview con URL.createObjectURL.
  portadaPorIsbn(isbn: string): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/lookup-isbn/portada`, { params: new HttpParams().set('isbn', isbn), responseType: 'blob' }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // RFC 7807: el backend responde ProblemDetail. El error se re-lanza
  // intacto (status + error.detail siguen disponibles en el componente,
  // igual que antes del refactor a services).
  private manejarError(err: unknown): Observable<never> {
    const problem = (err as { error?: ProblemDetail })?.error;
    if (problem?.status) {
      console.warn(`[libro.service] ${problem.title} (${problem.status}): ${problem.detail}`);
    }
    return throwError(() => err);
  }
}