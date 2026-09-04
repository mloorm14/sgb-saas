import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ProblemDetail } from '../models/problem-detail.model';

export interface LibroMasPrestado {
  libroId: number;
  titulo: string;
  isbn: string;
  totalPrestamos: number;
}

export interface LibroMasPrestadoDetallado {
  libroId: number;
  titulo: string;
  isbn: string;
  autorNombre: string;
  categoriaNombre: string;
  totalPrestamos: number;
  porcentaje: number;
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

export interface ReporteInventario {
  libroId: number;
  titulo: string;
  isbn: string;
  autorNombre: string;
  categoriaNombre: string;
  stockTotal: number;
  stockDisponible: number;
  estadoDisponibilidad: string;
}

export interface ReporteVencidos {
  prestamoId: number;
  usuarioNombre: string;
  usuarioCorreo: string;
  libroTitulo: string;
  libroIsbn: string;
  fechaDevolucionEstimada: string;
  diasAtraso: number;
  montoMultaEstimada: number;
}

export interface ReporteCategoriasDemandadas {
  categoriaId: number;
  categoriaNombre: string;
  totalPrestamos: number;
  porcentaje: number;
}

export interface ReporteUsoPorPeriodo {
  periodo: string;
  totalPrestamos: number;
  totalDevoluciones: number;
}

export interface ResumenFinancieroMultas {
  totalRecaudado: number;
  totalPendiente: number;
  totalGeneradoHoy: number;
  pagosRecientes: { multaId: number; montoPagado: number; fechaPagada: string; usuarioCorreo: string; usuarioNombre: string; libroTitulo: string }[];
}

@Injectable({
  providedIn: 'root'
})
export class ReporteService {

  private apiUrl = `${environment.apiUrl}/v1/prestamos/reportes`;

  constructor(private http: HttpClient) {}

  librosMasPrestados(desde?: string, hasta?: string, limite?: number): Observable<LibroMasPrestado[]> {
    const params: Record<string, string> = {};
    if (desde) params['desde'] = desde;
    if (hasta) params['hasta'] = hasta;
    if (limite) params['limite'] = String(limite);
    return this.http.get<LibroMasPrestado[]>(`${this.apiUrl}/libros-mas-prestados`, { params }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  librosMasPrestadosDetallado(desde?: string, hasta?: string, limite?: number, categoriaId?: number): Observable<LibroMasPrestadoDetallado[]> {
    const params: Record<string, string> = {};
    if (desde) params['desde'] = desde;
    if (hasta) params['hasta'] = hasta;
    if (limite) params['limite'] = limite.toString();
    if (categoriaId) params['categoriaId'] = categoriaId.toString();
    return this.http.get<LibroMasPrestadoDetallado[]>(`${this.apiUrl}/libros-mas-prestados-detallado`, { params }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  morosidad(limite?: number): Observable<ReporteMorosidad[]> {
    const params: Record<string, string> = {};
    if (limite) params['limite'] = String(limite);
    return this.http.get<ReporteMorosidad[]>(`${this.apiUrl}/morosidad`, { params }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  inventario(categoriaId?: number, estadoStock?: string, busqueda?: string, page?: number, size?: number): Observable<{ content: ReporteInventario[]; totalPages: number; totalElements: number }> {
    const params: Record<string, string> = {};
    if (categoriaId) params['categoriaId'] = categoriaId.toString();
    if (estadoStock) params['estadoStock'] = estadoStock;
    if (busqueda) params['busqueda'] = busqueda;
    if (page !== undefined) params['page'] = String(page);
    if (size !== undefined) params['size'] = String(size);
    // Backend ahora es Page, pero si no se manda page/size devuelve Page con size 20 por defecto
    return this.http.get<{ content: ReporteInventario[]; totalPages: number; totalElements: number }>(`${this.apiUrl}/inventario`, { params }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  inventarioTodo(categoriaId?: number, estadoStock?: string, busqueda?: string): Observable<ReporteInventario[]> {
    const params: Record<string, string> = {};
    if (categoriaId) params['categoriaId'] = categoriaId.toString();
    if (estadoStock) params['estadoStock'] = estadoStock;
    if (busqueda) params['busqueda'] = busqueda;
    return this.http.get<ReporteInventario[]>(`${this.apiUrl}/inventario/todo`, { params }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  vencidos(diasAtrasoMin?: number, busqueda?: string): Observable<ReporteVencidos[]> {
    const params: Record<string, string> = {};
    if (diasAtrasoMin) params['diasAtrasoMin'] = diasAtrasoMin.toString();
    if (busqueda) params['busqueda'] = busqueda;
    return this.http.get<ReporteVencidos[]>(`${this.apiUrl}/vencidos`, { params }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  categoriasDemandadas(desde?: string, hasta?: string, limite?: number): Observable<ReporteCategoriasDemandadas[]> {
    const params: Record<string, string> = {};
    if (desde) params['desde'] = desde;
    if (hasta) params['hasta'] = hasta;
    if (limite) params['limite'] = limite.toString();
    return this.http.get<ReporteCategoriasDemandadas[]>(`${this.apiUrl}/categorias-demandadas`, { params }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  usoPorPeriodo(granularidad: 'dia' | 'semana' | 'mes', desde?: string, hasta?: string): Observable<ReporteUsoPorPeriodo[]> {
    const params: Record<string, string> = { granularidad };
    if (desde) params['desde'] = desde;
    if (hasta) params['hasta'] = hasta;
    return this.http.get<ReporteUsoPorPeriodo[]>(`${environment.apiUrl}/v1/prestamos/reportes/uso`, { params }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  resumenFinanciero(desde?: string, hasta?: string): Observable<ResumenFinancieroMultas> {
    const params: Record<string, string> = {};
    if (desde) params['desde'] = desde;
    if (hasta) params['hasta'] = hasta;
    return this.http.get<ResumenFinancieroMultas>(`${environment.apiUrl}/v1/multas/reportes/resumen-financiero`, { params }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // ── PDF endpoints ──────────────────────────────────────
  morosidadPdf(): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/morosidad/pdf`, { responseType: 'blob' }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  librosMasPrestadosPdf(desde?: string, hasta?: string, limite?: number): Observable<Blob> {
    const params: Record<string, string> = {};
    if (desde) params['desde'] = desde;
    if (hasta) params['hasta'] = hasta;
    if (limite) params['limite'] = limite.toString();
    return this.http.get(`${this.apiUrl}/libros-mas-prestados/pdf`, { params, responseType: 'blob' }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  inventarioPdf(estadoStock?: string, busqueda?: string): Observable<Blob> {
    const params: Record<string, string> = {};
    if (estadoStock) params['estadoStock'] = estadoStock;
    if (busqueda) params['busqueda'] = busqueda;
    return this.http.get(`${this.apiUrl}/inventario/pdf`, { params, responseType: 'blob' }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  vencidosPdf(diasAtrasoMin?: number, busqueda?: string): Observable<Blob> {
    const params: Record<string, string> = {};
    if (diasAtrasoMin) params['diasAtrasoMin'] = diasAtrasoMin.toString();
    if (busqueda) params['busqueda'] = busqueda;
    return this.http.get(`${this.apiUrl}/vencidos/pdf`, { params, responseType: 'blob' }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  categoriasDemandadasPdf(desde?: string, hasta?: string, limite?: number): Observable<Blob> {
    const params: Record<string, string> = {};
    if (desde) params['desde'] = desde;
    if (hasta) params['hasta'] = hasta;
    if (limite) params['limite'] = limite.toString();
    return this.http.get(`${this.apiUrl}/categorias-demandadas/pdf`, { params, responseType: 'blob' }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  usoPorPeriodoPdf(granularidad: 'dia' | 'semana' | 'mes', desde?: string, hasta?: string): Observable<Blob> {
    const params: Record<string, string> = { granularidad };
    if (desde) params['desde'] = desde;
    if (hasta) params['hasta'] = hasta;
    return this.http.get(`${this.apiUrl}/uso/pdf`, { params, responseType: 'blob' }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  resumenFinancieroPdf(desde?: string, hasta?: string): Observable<Blob> {
    const params: Record<string, string> = {};
    if (desde) params['desde'] = desde;
    if (hasta) params['hasta'] = hasta;
    return this.http.get(`${environment.apiUrl}/v1/multas/reportes/resumen-financiero/pdf`, { params, responseType: 'blob' }).pipe(
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
