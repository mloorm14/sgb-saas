import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { catchError, map, Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ProblemDetail } from '../models/problem-detail.model';

export interface BackupRequest {
  desde: string;
  hasta: string;
  tablas: string[];
  formato: 'sql' | 'csv';
  tipo?: string;
}

export interface BackupEntry {
  id: number | string;
  desde: string;
  hasta: string;
  tablas: string[];
  formato: string;
  tamanoBytes?: number;
  tamano?: string;
  estado: string;
  creadoEn: string;
  creadoPor?: string;
  tipo?: string;
}

export interface BackupProgramacion {
  id: number;
  creadoPor: number;
  cadaHoras: number | null;
  cadaDias: number | null;
  formato: string;
  activo: boolean;
  creadoEn: string;
  ultimaEjecucion: string | null;
  tablas?: string[];
}

export interface RegistroRespaldo {
  id: number;
  tipo: string;
  estado: string;
  nombreArchivo?: string;
  tamanoArchivoBytes?: number;
  rutaR2?: string;
  mensajeError?: string;
  ejecutadoPor?: number;
  iniciadoEn: string;
  finalizadoEn?: string;
}

export interface ConfiguracionRespaldo {
  id: number;
  habilitado: boolean;
  frecuenciaHoras: number;
  diasRetencion: number;
  ultimaEjecucion?: string;
  proximaEjecucion?: string;
  actualizadoPor?: number;
  actualizadoEn?: string;
}

export type BackupResumen = BackupEntry;
export type BackupDetalle = BackupEntry;

@Injectable({ providedIn: 'root' })
export class BackupService {
  private apiUrl = `${environment.apiUrl}/v1/admin/backups`;
  private drUrl = `${environment.apiUrl}/v1/admin/respaldo-completo`;
  constructor(private http: HttpClient) {}

  private toIsoOffset(local: string): string {
    if (!local) return local;
    let s = local.trim();
    if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(s)) s += ':00';
    if (!/[Z+-]/.test(s.slice(10))) s += '-05:00';
    return s;
  }
  generar(dto: BackupRequest): Observable<BackupEntry> {
    const norm: BackupRequest = { ...dto, desde: this.toIsoOffset(dto.desde), hasta: this.toIsoOffset(dto.hasta) };
    return this.http.post<BackupEntry>(this.apiUrl, norm).pipe(catchError(err => this.manejarError(err)));
  }
  listar(filtros?: { desde?: string; hasta?: string; formato?: string }): Observable<BackupEntry[]> {
    let params = new HttpParams();
    if (filtros?.desde) params = params.set('desde', filtros.desde);
    if (filtros?.hasta) params = params.set('hasta', filtros.hasta);
    if (filtros?.formato) params = params.set('formato', filtros.formato);
    return this.http.get<BackupEntry[]>(this.apiUrl, { params }).pipe(catchError(err => this.manejarError(err)));
  }
  descargar(id: number | string): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${id}/download`, { responseType: 'blob' }).pipe(catchError(err => this.manejarError(err)));
  }
  eliminar(id: number | string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(catchError(err => this.manejarError(err)));
  }
  borrar(id: number | string): Observable<boolean> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(map(() => true), catchError(err => this.manejarError(err)));
  }
  programar(id: number): Observable<BackupProgramacion> {
    return this.http.post<BackupProgramacion>(`${this.apiUrl}/${id}/programar`, {}).pipe(catchError(err => this.manejarError(err)));
  }
  listarProgramaciones(): Observable<BackupProgramacion[]> {
    return this.http.get<BackupProgramacion[]>(`${this.apiUrl}/programacion`).pipe(catchError(err => this.manejarError(err)));
  }
  ejecutarAhora(id: number): Observable<any> {
    return this.http.post(`${this.apiUrl}/${id}/ejecutar-ahora`, {}).pipe(catchError(err => this.manejarError(err)));
  }

  // ── Backups Completos (Disaster Recovery) ────────────────────────────────
  obtenerConfigDR(): Observable<ConfiguracionRespaldo> {
    return this.http.get<ConfiguracionRespaldo>(`${this.drUrl}/config`).pipe(catchError(err => this.manejarError(err)));
  }
  actualizarConfigDR(dto: { frecuenciaHoras?: number; diasRetencion?: number; habilitado?: boolean }): Observable<ConfiguracionRespaldo> {
    return this.http.put<ConfiguracionRespaldo>(`${this.drUrl}/config`, dto).pipe(catchError(err => this.manejarError(err)));
  }
  listarRegistrosRespaldo(tipo?: string): Observable<RegistroRespaldo[]> {
    let params = new HttpParams();
    if (tipo) params = params.set('tipo', tipo);
    return this.http.get<RegistroRespaldo[]>(`${this.drUrl}/registros`, { params }).pipe(catchError(err => this.manejarError(err)));
  }
  dispararBackupCompleto(): Observable<any> {
    return this.http.post(`${this.drUrl}/trigger`, {}).pipe(catchError(err => this.manejarError(err)));
  }

  private manejarError(err: unknown): Observable<never> {
    const problem = (err as { error?: ProblemDetail })?.error;
    if (problem?.status) console.warn(`[backup.service] ${problem.title} (${problem.status}): ${problem.detail}`);
    return throwError(() => err);
  }
}