import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ProblemDetail } from '../models/problem-detail.model';

// Contrato de PrestamoController (/api/v1/prestamos/reportes), verificado
// en backend-springboot: BIBLIOTECARIO/GERENTE (ADMIN no tiene acceso).
// Solo morosidad tiene PDF; libros-mas-prestados y uso no.

export interface LibroMasPrestado {
  libroId: number;
  titulo: string;
  isbn: string;
  totalPrestamos: number;
}

export interface ReporteMorosidad {
  usuarioId: number;
  nombre: string;
  apellido: string;
  correo: string;
  montoTotalAdeudado: number;
  cantidadMultasPendientes: number;
  diasAtrasoPromedio: number;
}

export interface ReporteUsoPorPeriodo {
  periodo: string;
  totalPrestamos: number;
  totalDevoluciones: number;
}

export type GranularidadUso = 'dia' | 'semana' | 'mes';

@Injectable({
  providedIn: 'root'
})
export class ReporteService {

  private apiUrl = `${environment.apiUrl}/v1/prestamos/reportes`;

  constructor(private http: HttpClient) {}

  librosMasPrestados(desde?: string, hasta?: string): Observable<LibroMasPrestado[]> {
    const params: Record<string, string> = {};
    if (desde) params['desde'] = desde;
    if (hasta) params['hasta'] = hasta;
    return this.http.get<LibroMasPrestado[]>(`${this.apiUrl}/libros-mas-prestados`, { params }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  morosidad(): Observable<ReporteMorosidad[]> {
    return this.http.get<ReporteMorosidad[]>(`${this.apiUrl}/morosidad`).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  uso(granularidad: GranularidadUso): Observable<ReporteUsoPorPeriodo[]> {
    return this.http.get<ReporteUsoPorPeriodo[]>(`${this.apiUrl}/uso`, {
      params: { granularidad }
    }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // Blob para descargar con <a download> (no se abre en pestaña).
  morosidadPdf(): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/morosidad/pdf`, { responseType: 'blob' }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  private manejarError(err: unknown): Observable<never> {
    const problem = (err as { error?: ProblemDetail })?.error;
    if (problem?.status) {
      console.warn(`[reporte.service] ${problem.title} (${problem.status}): ${problem.detail}`);
    }
    return throwError(() => err);
  }
}