import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Categoria } from '../models/categoria.model';
import { ProblemDetail } from '../models/problem-detail.model';

// Contrato de CategoriaController (/api/v1/categorias): array plano, sin
// paginación. Verificado en backend-springboot.
@Injectable({
  providedIn: 'root'
})
export class CategoriaService {

  private apiUrl = `${environment.apiUrl}/v1/categorias`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Categoria[]> {
    return this.http.get<Categoria[]>(this.apiUrl).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // RFC 7807: el backend responde ProblemDetail. El error se re-lanza
  // intacto (status + error.detail siguen disponibles en el componente).
  private manejarError(err: unknown): Observable<never> {
    const problem = (err as { error?: ProblemDetail })?.error;
    if (problem?.status) {
      console.warn(`[categoria.service] ${problem.title} (${problem.status}): ${problem.detail}`);
    }
    return throwError(() => err);
  }
}