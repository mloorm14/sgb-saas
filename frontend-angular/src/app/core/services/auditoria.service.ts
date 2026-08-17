import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../models/pagina.model';
import { ProblemDetail } from '../models/problem-detail.model';
import { EventoAuditoria } from '../models/evento-auditoria.model';

// Contrato de AuditoriaController (/api/v1/auditoria), verificado en
// backend-springboot: GERENTE/ADMIN a nivel de clase. Filtros opcionales:
// usuarioId (Long), modulo (String, tablaAfectada), desde/hasta
// (OffsetDateTime ISO con offset -- el frontend envia ...Z).
@Injectable({
  providedIn: 'root'
})
export class AuditoriaService {

  private apiUrl = `${environment.apiUrl}/v1/auditoria`;

  constructor(private http: HttpClient) {}

  listar(opciones: {
    usuarioId?: number | null;
    modulo?: string;
    desde?: string;
    hasta?: string;
    page: number;
    size: number;
  }): Observable<Page<EventoAuditoria>> {
    let params = new HttpParams()
      .set('page', opciones.page)
      .set('size', opciones.size);
    if (opciones.usuarioId) params = params.set('usuarioId', opciones.usuarioId);
    if (opciones.modulo) params = params.set('modulo', opciones.modulo);
    if (opciones.desde) params = params.set('desde', opciones.desde);
    if (opciones.hasta) params = params.set('hasta', opciones.hasta);

    return this.http.get<Page<EventoAuditoria>>(this.apiUrl, { params }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  private manejarError(err: unknown): Observable<never> {
    const problem = (err as { error?: ProblemDetail })?.error;
    if (problem?.status) {
      console.warn(`[auditoria.service] ${problem.title} (${problem.status}): ${problem.detail}`);
    }
    return throwError(() => err);
  }
}