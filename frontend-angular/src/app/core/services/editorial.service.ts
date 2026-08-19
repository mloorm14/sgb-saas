import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Editorial } from '../models/editorial.model';
import { ProblemDetail } from '../models/problem-detail.model';

// Contrato de EditorialController (/api/v1/editoriales): array plano, sin
// paginación. Verificado en backend-springboot (FIX 3).
@Injectable({
  providedIn: 'root'
})
export class EditorialService {

  private apiUrl = `${environment.apiUrl}/v1/editoriales`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Editorial[]> {
    return this.http.get<Editorial[]>(this.apiUrl).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // RFC 7807: el backend responde ProblemDetail. El error se re-lanza
  // intacto (status + error.detail siguen disponibles en el componente).
  private manejarError(err: unknown): Observable<never> {
    const problem = (err as { error?: ProblemDetail })?.error;
    if (problem?.status) {
      console.warn(`[editorial.service] ${problem.title} (${problem.status}): ${problem.detail}`);
    }
    return throwError(() => err);
  }
}