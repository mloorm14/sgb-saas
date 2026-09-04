import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Favorito } from '../models/favorito.model';
import { ProblemDetail } from '../models/problem-detail.model';

// Contrato de FavoritoController (/api/v1/favoritos), verificado en
// backend-springboot: todas las operaciones son 100% LECTOR y el usuario se
// resuelve del token JWT — nunca se manda usuarioId en URL ni en el body.
// listar() devuelve array plano (sin paginación).
@Injectable({
  providedIn: 'root'
})
export class FavoritoService {

  private apiUrl = `${environment.apiUrl}/v1/favoritos`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Favorito[]> {
    return this.http.get<Favorito[]>(`${this.apiUrl}/todo`).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  listarPaginado(page?: number, size?: number): Observable<{ content: Favorito[]; totalPages: number; totalElements: number }> {
    const params: Record<string, string> = {};
    if (page !== undefined) params['page'] = String(page);
    if (size !== undefined) params['size'] = String(size);
    return this.http.get<{ content: Favorito[]; totalPages: number; totalElements: number }>(this.apiUrl, { params }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  agregar(libroId: number): Observable<Favorito> {
    return this.http.post<Favorito>(`${this.apiUrl}/${libroId}`, {}).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  quitar(libroId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${libroId}`).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // RFC 7807: el backend responde ProblemDetail. El error se re-lanza
  // intacto (status + error.detail siguen disponibles en el componente).
  private manejarError(err: unknown): Observable<never> {
    const problem = (err as { error?: ProblemDetail })?.error;
    if (problem?.status) {
      console.warn(`[favorito.service] ${problem.title} (${problem.status}): ${problem.detail}`);
    }
    return throwError(() => err);
  }
}