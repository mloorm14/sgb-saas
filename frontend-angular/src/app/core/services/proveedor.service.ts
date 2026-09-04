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

  listar(page?: number, size?: number, q?: string, activo?: boolean): Observable<{ content: Proveedor[]; totalPages: number; totalElements: number }> {
    const params: Record<string, string> = {};
    if (page !== undefined) params['page'] = String(page);
    if (size !== undefined) params['size'] = String(size);
    if (q) params['q'] = q;
    if (activo !== undefined && activo !== null) params['activo'] = String(activo);
    return this.http.get<{ content: Proveedor[]; totalPages: number; totalElements: number }>(this.apiUrl, { params }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  listarTodo(): Observable<Proveedor[]> {
    return this.http.get<Proveedor[]>(`${this.apiUrl}/todo`).pipe(
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
