import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Proveedor } from '../models/proveedor.model';
import { ProblemDetail } from '../models/problem-detail.model';

@Injectable({
  providedIn: 'root'
})
export class ProveedorService {

  private apiUrl = `${environment.apiUrl}/v1/proveedores`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Proveedor[]> {
    return this.http.get<Proveedor[]>(this.apiUrl).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  buscar(q: string): Observable<Proveedor[]> {
    return this.http.get<Proveedor[]>(`${this.apiUrl}/buscar`, { params: { q } }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  crear(data: Partial<Proveedor>): Observable<Proveedor> {
    return this.http.post<Proveedor>(this.apiUrl, data).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  actualizar(id: number, data: Partial<Proveedor>): Observable<Proveedor> {
    return this.http.put<Proveedor>(`${this.apiUrl}/${id}`, data).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  private manejarError(err: unknown): Observable<never> {
    const problem = (err as { error?: ProblemDetail })?.error;
    if (problem?.status) {
      console.warn(`[proveedor.service] ${problem.title} (${problem.status}): ${problem.detail}`);
    }
    return throwError(() => err);
  }
}
