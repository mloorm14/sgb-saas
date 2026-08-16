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
// GERENTE/ADMIN y pertenecen a la rama F — no se implementan aquí.
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