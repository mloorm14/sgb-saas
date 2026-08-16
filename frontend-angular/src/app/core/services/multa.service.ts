import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../models/pagina.model';
import { Multa, MultaAccionResponse } from '../models/multa.model';
import { ProblemDetail } from '../models/problem-detail.model';

export interface MultaListarParams {
  page?: number;
  size?: number;
  sort?: string;
}

@Injectable({
  providedIn: 'root'
})
export class MultaService {

  private apiUrl = `${environment.apiUrl}/v1/multas`;

  constructor(private http: HttpClient) {}

  listarPorUsuario(usuarioId: number, params: MultaListarParams = {}): Observable<Page<Multa>> {
    let httpParams = new HttpParams();
    if (params.page !== undefined) httpParams = httpParams.set('page', params.page);
    if (params.size !== undefined) httpParams = httpParams.set('size', params.size);
    if (params.sort) httpParams = httpParams.set('sort', params.sort);

    return this.http.get<Page<Multa>>(`${this.apiUrl}/usuario/${usuarioId}`, { params: httpParams }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // POST /v1/multas/{id}/pago (sin body): solo BIBLIOTECARIO/GERENTE.
  pagar(id: number): Observable<MultaAccionResponse> {
    return this.http.post<MultaAccionResponse>(`${this.apiUrl}/${id}/pago`, {}).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // POST /v1/multas/{id}/anulacion con { motivo }: solo GERENTE/ADMIN.
  anular(id: number, motivo: string): Observable<MultaAccionResponse> {
    return this.http.post<MultaAccionResponse>(`${this.apiUrl}/${id}/anulacion`, { motivo }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // RFC 7807: el backend responde ProblemDetail. El error se re-lanza
  // intacto (status + error.detail siguen disponibles en el componente).
  private manejarError(err: unknown): Observable<never> {
    const problem = (err as { error?: ProblemDetail })?.error;
    if (problem?.status) {
      console.warn(`[multa.service] ${problem.title} (${problem.status}): ${problem.detail}`);
    }
    return throwError(() => err);
  }
}