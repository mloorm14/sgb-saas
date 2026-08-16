import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../models/pagina.model';
import { ProblemDetail } from '../models/problem-detail.model';
import { Reservacion, ReservacionRequest } from '../models/reservacion.model';

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

  // RFC 7807: el backend responde ProblemDetail. El error se re-lanza
  // intacto (status + error.detail siguen disponibles en el componente).
  private manejarError(err: unknown): Observable<never> {
    const problem = (err as { error?: ProblemDetail })?.error;
    if (problem?.status) {
      console.warn(`[reservacion.service] ${problem.title} (${problem.status}): ${problem.detail}`);
    }
    return throwError(() => err);
  }
}