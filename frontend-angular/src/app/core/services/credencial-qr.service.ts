import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ProblemDetail } from '../models/problem-detail.model';

@Injectable({ providedIn: 'root' })
export class CredencialQrService {

  private apiUrl = `${environment.apiUrl}/v1/credencial-qr/mi-credencial`;

  constructor(private http: HttpClient) {}

  obtenerImagen(): Observable<Blob> {
    return this.http.get(this.apiUrl, { responseType: 'blob' }).pipe(
      catchError(err => {
        const problem = (err as { error?: ProblemDetail })?.error;
        if (problem?.status) {
          console.warn(`[credencial-qr.service] ${problem.title} (${problem.status}): ${problem.detail}`);
        }
        return throwError(() => err);
      })
    );
  }
}
