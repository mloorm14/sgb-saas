import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../models/pagina.model';
import { ProblemDetail } from '../models/problem-detail.model';
import {
  DevolucionResponse,
  LibroMasPrestado,
  Prestamo,
  PrestamoActivo,
  PrestamoRequest,
  RenovacionResponse,
  ReporteMorosidad,
  ReporteUsoPorPeriodo
} from '../models/prestamo.model';
import {
  HistorialPrestamo,
  UsuarioPrestamos,
  UsuarioSugerencia
} from '../models/prestamos-gestion.model';

export interface PrestamoListarParams {
  page?: number;
  size?: number;
  sort?: string;
}

export interface ReporteRangoParams {
  limite?: number;
  desde?: string;
  hasta?: string;
  granularidad?: string;
}

@Injectable({
  providedIn: 'root'
})
export class PrestamoService {

  private apiUrl = `${environment.apiUrl}/v1/prestamos`;

  constructor(private http: HttpClient) {}

  crear(dto: PrestamoRequest): Observable<Prestamo> {
    return this.http.post<Prestamo>(this.apiUrl, dto).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  devolver(id: number): Observable<DevolucionResponse> {
    return this.http.post<DevolucionResponse>(`${this.apiUrl}/${id}/devolucion`, {}).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  renovar(id: number): Observable<RenovacionResponse> {
    return this.http.post<RenovacionResponse>(`${this.apiUrl}/${id}/renovacion`, {}).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  listarPorUsuario(usuarioId: number, params: PrestamoListarParams = {}): Observable<Page<Prestamo>> {
    let httpParams = this.aHttpParams(params);
    return this.http.get<Page<Prestamo>>(`${this.apiUrl}/usuario/${usuarioId}`, { params: httpParams }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  activosPorUsuario(usuarioId: number): Observable<PrestamoActivo[]> {
    return this.http.get<PrestamoActivo[]>(`${this.apiUrl}/usuario/${usuarioId}/activos`).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // ── Ventanilla del bibliotecario (/gestion/*) ────────────
  // buscarUsuarioPorCorreo: 404 (ProblemDetail) si el correo no coincide
  // con ningún usuario -> el componente muestra el mensaje de error.
  buscarUsuarioPorCorreo(correo: string): Observable<UsuarioPrestamos> {
    return this.http.get<UsuarioPrestamos>(`${this.apiUrl}/gestion/buscar-usuario`, {
      params: new HttpParams().set('correo', correo)
    }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // sugerenciasUsuarios: autocompletado predictivo por correo parcial.
  // Retorna hasta 3 resultados para el dropdown del buscador.
  sugerenciasUsuarios(correo: string): Observable<UsuarioSugerencia[]> {
    return this.http.get<UsuarioSugerencia[]>(`${this.apiUrl}/gestion/sugerencias-usuarios`, {
      params: new HttpParams().set('correo', correo)
    }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  historial(usuarioId: number): Observable<HistorialPrestamo[]> {
    return this.http.get<HistorialPrestamo[]>(`${this.apiUrl}/gestion/historial`, {
      params: new HttpParams().set('usuarioId', usuarioId)
    }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  reporteLibrosMasPrestados(params: ReporteRangoParams = {}): Observable<LibroMasPrestado[]> {
    return this.http.get<LibroMasPrestado[]>(`${this.apiUrl}/reportes/libros-mas-prestados`, { params: this.aHttpParams(params) }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  reporteMorosidad(limite: number): Observable<ReporteMorosidad[]> {
    return this.http.get<ReporteMorosidad[]>(`${this.apiUrl}/reportes/morosidad`, { params: new HttpParams().set('limite', limite) }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  reporteUso(params: ReporteRangoParams = {}): Observable<ReporteUsoPorPeriodo[]> {
    return this.http.get<ReporteUsoPorPeriodo[]>(`${this.apiUrl}/reportes/uso`, { params: this.aHttpParams(params) }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // GET /reportes/morosidad/pdf: application/pdf, se descarga como Blob.
  reporteMorosidadPdf(limite: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/reportes/morosidad/pdf`, {
      params: new HttpParams().set('limite', limite),
      responseType: 'blob'
    }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  private aHttpParams<T extends object>(params: T): HttpParams {
    let httpParams = new HttpParams();
    for (const [clave, valor] of Object.entries(params)) {
      if (valor !== undefined && valor !== null) {
        httpParams = httpParams.set(clave, String(valor));
      }
    }
    return httpParams;
  }

  // RFC 7807: el backend responde ProblemDetail. El error se re-lanza
  // intacto (status + error.detail siguen disponibles en el componente).
  private manejarError(err: unknown): Observable<never> {
    const problem = (err as { error?: ProblemDetail })?.error;
    if (problem?.status) {
      console.warn(`[prestamo.service] ${problem.title} (${problem.status}): ${problem.detail}`);
    }
    return throwError(() => err);
  }
}