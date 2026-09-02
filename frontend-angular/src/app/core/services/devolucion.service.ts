import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ProblemDetail } from '../models/problem-detail.model';
import {
  CategoriaDano,
  DevolucionCompletaResponse,
  DevolucionHistorial,
  DevolucionRequest,
  EvidenciaDanoResponse,
  TipoDano
} from '../models/devoluciones.model';

@Injectable({
  providedIn: 'root'
})
export class DevolucionService {

  private apiUrl = `${environment.apiUrl}/v1/devoluciones`;

  constructor(private http: HttpClient) {}

  registrarDevolucion(prestamoId: number, dto: DevolucionRequest): Observable<DevolucionCompletaResponse> {
    return this.http.post<DevolucionCompletaResponse>(
      `${this.apiUrl}/prestamo/${prestamoId}`, dto
    ).pipe(catchError(err => this.manejarError(err)));
  }

  historialDevoluciones(): Observable<DevolucionHistorial[]> {
    return this.http.get<DevolucionHistorial[]>(
      `${this.apiUrl}/historial`
    ).pipe(catchError(err => this.manejarError(err)));
  }

  listarTiposDano(): Observable<TipoDano[]> {
    return this.http.get<TipoDano[]>(
      `${environment.apiUrl}/v1/tipos-dano`
    ).pipe(catchError(err => this.manejarError(err)));
  }

  listarCategoriasDano(): Observable<CategoriaDano[]> {
    return this.http.get<CategoriaDano[]>(
      `${environment.apiUrl}/v1/categorias-dano`
    ).pipe(catchError(err => this.manejarError(err)));
  }

  crearCategoriaDano(nombre: string): Observable<CategoriaDano> {
    return this.http.post<CategoriaDano>(`${environment.apiUrl}/v1/categorias-dano`, { nombre }).pipe(catchError(err => this.manejarError(err)));
  }

  crearTipoDano(nombre: string, categoriaId: number, tipoCosto: string, valor: number): Observable<TipoDano> {
    return this.http.post<TipoDano>(
      `${environment.apiUrl}/v1/tipos-dano`, { nombre, categoriaId, tipoCosto, valor }
    ).pipe(catchError(err => this.manejarError(err)));
  }

  actualizarTipoDano(id: number, nombre: string, categoriaId: number, tipoCosto: string, valor: number): Observable<TipoDano> {
    return this.http.put<TipoDano>(
      `${environment.apiUrl}/v1/tipos-dano/${id}`, { nombre, categoriaId, tipoCosto, valor }
    ).pipe(catchError(err => this.manejarError(err)));
  }

  eliminarTipoDano(id: number): Observable<void> {
    return this.http.delete<void>(
      `${environment.apiUrl}/v1/tipos-dano/${id}`
    ).pipe(catchError(err => this.manejarError(err)));
  }

  subirEvidencia(registroDanoId: number, archivo: File): Observable<EvidenciaDanoResponse> {
    const formData = new FormData();
    formData.append('archivo', archivo);
    return this.http.post<EvidenciaDanoResponse>(
      `${this.apiUrl}/evidencia/${registroDanoId}`, formData
    ).pipe(catchError(err => this.manejarError(err)));
  }

  listarEvidencias(registroDanoId: number): Observable<EvidenciaDanoResponse[]> {
    return this.http.get<EvidenciaDanoResponse[]>(
      `${this.apiUrl}/evidencia/${registroDanoId}`
    ).pipe(catchError(err => this.manejarError(err)));
  }

  obtenerEvidenciaArchivo(id: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/evidencia/${id}/archivo`, {
      responseType: 'blob'
    }).pipe(catchError(err => this.manejarError(err)));
  }

  private manejarError(err: any): Observable<never> {
    const pd = err.error as ProblemDetail;
    const msg = pd?.detail ?? pd?.title ?? err.message ?? 'Error desconocido';
    console.error('[DevolucionService]', msg, err);
    return throwError(() => new Error(msg));
  }
}
