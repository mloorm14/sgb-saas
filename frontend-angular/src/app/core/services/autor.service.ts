import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Autor } from '../models/autor.model';
import { ProblemDetail } from '../models/problem-detail.model';

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

  buscar(texto: string): Observable<Autor[]> {
    return this.http.get<Autor[]>(`${this.apiUrl}/buscar`, { params: { q: texto } }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  crear(nombre: string): Observable<Autor> {
    return this.http.post<Autor>(this.apiUrl, { nombre }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  private manejarError(err: unknown): Observable<never> {
    const problem = (err as { error?: ProblemDetail })?.error;
    if (problem?.status) {
      console.warn(`[autor.service] ${problem.title} (${problem.status}): ${problem.detail}`);
    }
    return throwError(() => err);
  }
}