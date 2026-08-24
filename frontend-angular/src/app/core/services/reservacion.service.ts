import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../models/pagina.model';
import { ProblemDetail } from '../models/problem-detail.model';
import { CambioEstadoReservacionRequest, Reservacion, ReservacionHoy, ReservacionRequest } from '../models/reservacion.model';
import { HistorialReservacion, UsuarioReservaciones } from '../models/reservaciones-gestion.model';

export interface ReservacionListarParams {
  page?: number;
  size?: number;
  sort?: string;
}

@Injectable({
  providedIn: 'root'
})
export class ReservacionService {

  private apiUrl = `${environment.apiUrl}/v1/reservaciones`;

  constructor(private http: HttpClient) {}

  crear(dto: ReservacionRequest): Observable<Reservacion> {
    return this.http.post<Reservacion>(this.apiUrl, dto).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  listarPorUsuario(usuarioId: number, params: ReservacionListarParams = {}): Observable<Page<Reservacion>> {
    let httpParams = new HttpParams();
    if (params.page !== undefined) httpParams = httpParams.set('page', params.page);
    if (params.size !== undefined) httpParams = httpParams.set('size', params.size);
    if (params.sort) httpParams = httpParams.set('sort', params.sort);

    return this.http.get<Page<Reservacion>>(`${this.apiUrl}/usuario/${usuarioId}`, { params: httpParams }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // PATCH /api/v1/reservaciones/{id}/estado: solo BIBLIOTECARIO/GERENTE en
  // el backend (@PreAuthorize). Aceptar = LISTA_PARA_RETIRO, rechazar = CANCELADA.
  cambiarEstado(id: number, dto: CambioEstadoReservacionRequest): Observable<Reservacion> {
    return this.http.patch<Reservacion>(`${this.apiUrl}/${id}/estado`, dto).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // RFC 7807: el backend responde ProblemDetail. El error se re-lanza
  // intacto (status + error.detail siguen disponibles en el componente).
  private manejarError(err: unknown): Observable<never> {
    const problem = (err as { error?: ProblemDetail })?.error;
    if (problem?.status) {
      console.warn(`[reservacion.service] ${problem.title} (${problem.status}): ${problem.detail}`);
    }
    return throwError(() => err);
  }

  // ── Ventanilla del bibliotecario (/gestion/*) ────────────
  // buscarUsuarioPorCorreo: 404 (ProblemDetail) si el correo no coincide
  // con ningún usuario -> el componente muestra el mensaje de error.
  buscarUsuarioPorCorreo(correo: string): Observable<UsuarioReservaciones> {
    return this.http.get<UsuarioReservaciones>(`${this.apiUrl}/gestion/buscar-usuario`, {
      params: new HttpParams().set('correo', correo)
    }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // historialReservaciones: retorna las reservaciones del usuario
  // con el título del libro resuelto.
  historialReservaciones(usuarioId: number): Observable<HistorialReservacion[]> {
    return this.http.get<HistorialReservacion[]>(`${this.apiUrl}/gestion/historial-reservaciones`, {
      params: new HttpParams().set('usuarioId', usuarioId)
    }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // Dashboard del bibliotecario: reservaciones que vencen hoy
  // (PENDIENTE o LISTA_PARA_RETIRO con fecha_limite_retiro = hoy).
  reservacionesDeHoy(): Observable<ReservacionHoy[]> {
    return this.http.get<ReservacionHoy[]>(`${this.apiUrl}/hoy`).pipe(
      catchError(err => this.manejarError(err))
    );
  }
}