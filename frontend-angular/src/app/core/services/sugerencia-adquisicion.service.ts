import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../models/pagina.model';
import { ProblemDetail } from '../models/problem-detail.model';
import { SugerenciaAdquisicion, SugerenciaAdquisicionRequest } from '../models/sugerencia-adquisicion.model';

// Contrato de SugerenciaAdquisicionController (/api/v1/sugerencias-adquisicion),
// verificado en backend-springboot. crear() y listarMias() son 100% LECTOR
// (el usuario sale del token); listarTodas()/cambiarEstado() son
// GERENTE/ADMIN (rama F) y el backend solo acepta APROBADA o RECHAZADA
// como nuevo estado (CambioEstadoSugerenciaRequestDTO @Pattern).
export interface SugerenciaListarParams {
  page?: number;
  size?: number;
  sort?: string;
}

@Injectable({
  providedIn: 'root'
})
export class SugerenciaAdquisicionService {

  private apiUrl = `${environment.apiUrl}/v1/sugerencias-adquisicion`;

  constructor(private http: HttpClient) {}

  crear(dto: SugerenciaAdquisicionRequest): Observable<SugerenciaAdquisicion> {
    return this.http.post<SugerenciaAdquisicion>(this.apiUrl, dto).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  listarMias(params: SugerenciaListarParams = {}): Observable<Page<SugerenciaAdquisicion>> {
    let httpParams = new HttpParams();
    if (params.page !== undefined) httpParams = httpParams.set('page', params.page);
    if (params.size !== undefined) httpParams = httpParams.set('size', params.size);
    if (params.sort) httpParams = httpParams.set('sort', params.sort);

    return this.http.get<Page<SugerenciaAdquisicion>>(`${this.apiUrl}/mias`, { params: httpParams }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // GET /v1/sugerencias-adquisicion?estado= (GERENTE/ADMIN): todas las
  // sugerencias, con filtro opcional PENDIENTE|APROBADA|RECHAZADA.
  listarTodas(estado: string, params: SugerenciaListarParams = {}): Observable<Page<SugerenciaAdquisicion>> {
    let httpParams = new HttpParams();
    if (params.page !== undefined) httpParams = httpParams.set('page', params.page);
    if (params.size !== undefined) httpParams = httpParams.set('size', params.size);
    if (params.sort) httpParams = httpParams.set('sort', params.sort);
    if (estado) httpParams = httpParams.set('estado', estado);

    return this.http.get<Page<SugerenciaAdquisicion>>(this.apiUrl, { params: httpParams }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // PATCH /v1/sugerencias-adquisicion/{id}/estado -- body { nuevoEstado }.
  // El backend solo acepta APROBADA o RECHAZADA (@Pattern del DTO); no hay
  // forma de volver una sugerencia a PENDIENTE.
  cambiarEstado(id: number, nuevoEstado: 'APROBADA' | 'RECHAZADA'): Observable<SugerenciaAdquisicion> {
    return this.http.patch<SugerenciaAdquisicion>(`${this.apiUrl}/${id}/estado`, { nuevoEstado }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // RFC 7807: el backend responde ProblemDetail. El error se re-lanza
  // intacto (status + error.detail siguen disponibles en el componente).
  private manejarError(err: unknown): Observable<never> {
    const problem = (err as { error?: ProblemDetail })?.error;
    if (problem?.status) {
      console.warn(`[sugerencia-adquisicion.service] ${problem.title} (${problem.status}): ${problem.detail}`);
    }
    return throwError(() => err);
  }
}