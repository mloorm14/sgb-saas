import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Idioma } from '../models/idioma.model';
import { ProblemDetail } from '../models/problem-detail.model';

// Contrato de IdiomaController (/api/v1/idiomas): array plano, sin
// paginación. Verificado en backend-springboot (FIX 3).
@Injectable({
  providedIn: 'root'
})
export class IdiomaService {

  private apiUrl = `${environment.apiUrl}/v1/idiomas`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Idioma[]> {
    return this.http.get<Idioma[]>(this.apiUrl).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  buscar(q: string): Observable<Idioma[]> {
    return this.http.get<Idioma[]>(`${this.apiUrl}/buscar`, { params: { q } }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  crear(nombre: string): Observable<Idioma> {
    return this.http.post<Idioma>(this.apiUrl, { nombre }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // RFC 7807: el backend responde ProblemDetail. El error se re-lanza
  // intacto (status + error.detail siguen disponibles en el componente).
  private manejarError(err: unknown): Observable<never> {
    const problem = (err as { error?: ProblemDetail })?.error;
    if (problem?.status) {
      console.warn(`[idioma.service] ${problem.title} (${problem.status}): ${problem.detail}`);
    }
    return throwError(() => err);
  }
}