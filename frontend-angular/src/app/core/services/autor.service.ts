import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Autor } from '../models/autor.model';
import { ProblemDetail } from '../models/problem-detail.model';

// Contrato de AutorController (/api/v1/autores): array plano, sin
// paginación. Verificado en backend-springboot.
@Injectable({
  providedIn: 'root'
})
export class AutorService {

  private apiUrl = `${environment.apiUrl}/v1/autores`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Autor[]> {
    return this.http.get<Autor[]>(this.apiUrl).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // RFC 7807: el backend responde ProblemDetail. El error se re-lanza
  // intacto (status + error.detail siguen disponibles en el componente).
  private manejarError(err: unknown): Observable<never> {
    const problem = (err as { error?: ProblemDetail })?.error;
    if (problem?.status) {
      console.warn(`[autor.service] ${problem.title} (${problem.status}): ${problem.detail}`);
    }
    return throwError(() => err);
  }
}