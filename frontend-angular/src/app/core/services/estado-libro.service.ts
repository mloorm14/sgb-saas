import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { EstadoLibro } from '../models/estado-libro.model';
import { ProblemDetail } from '../models/problem-detail.model';

// Contrato de EstadoLibroController (/api/v1/estados-libro): array plano,
// sin paginación. Verificado en backend-springboot (FIX 3).
@Injectable({
  providedIn: 'root'
})
export class EstadoLibroService {

  private apiUrl = `${environment.apiUrl}/v1/estados-libro`;

  constructor(private http: HttpClient) {}

  listar(): Observable<EstadoLibro[]> {
    return this.http.get<EstadoLibro[]>(this.apiUrl).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // RFC 7807: el backend responde ProblemDetail. El error se re-lanza
  // intacto (status + error.detail siguen disponibles en el componente).
  private manejarError(err: unknown): Observable<never> {
    const problem = (err as { error?: ProblemDetail })?.error;
    if (problem?.status) {
      console.warn(`[estado-libro.service] ${problem.title} (${problem.status}): ${problem.detail}`);
    }
    return throwError(() => err);
  }
}