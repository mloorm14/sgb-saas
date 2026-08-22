import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ProblemDetail } from '../models/problem-detail.model';
import {
  DevolucionCompletaResponse,
  DevolucionHistorial,
  DevolucionRequest,
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
      `${this.apiUrl}/tipos-dano`
    ).pipe(catchError(err => this.manejarError(err)));
  }

  private manejarError(err: any): Observable<never> {
    const pd = err.error as ProblemDetail;
    const msg = pd?.detail ?? pd?.title ?? err.message ?? 'Error desconocido';
    console.error('[DevolucionService]', msg, err);
    return throwError(() => new Error(msg));
  }
}
