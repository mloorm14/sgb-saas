import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Notificacion, NotificacionListarParams } from '../models/notificacion.model';
import { Page } from '../models/pagina.model';
import { ProblemDetail } from '../models/problem-detail.model';
import { AuthService } from './auth.service';

// Contrato de NotificacionController (/api/v1/notificaciones/usuario/{usuarioId}), verificado en
// backend-springboot: GET paginado, solo notificaciones del usuario autenticado.
// El usuarioId se resuelve del token JWT (AuthService.getUserId()), nunca se manda en query.
@Injectable({
  providedIn: 'root'
})
export class NotificacionService {

  private apiUrl = `${environment.apiUrl}/v1/notificaciones`;

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  listar(params: NotificacionListarParams = {}): Observable<Page<Notificacion>> {
    const usuarioId = this.authService.getUserId();
    if (usuarioId === null) {
      // No debería pasar si el guard de auth funciona, pero defendemos igual
      return throwError(() => new Error('Usuario no autenticado'));
    }

    let httpParams = new HttpParams();
    if (params.page !== undefined) httpParams = httpParams.set('page', params.page);
    if (params.size !== undefined) httpParams = httpParams.set('size', params.size);

    return this.http.get<Page<Notificacion>>(`${this.apiUrl}/usuario/${usuarioId}`, { params: httpParams }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // RFC 7807: el backend responde ProblemDetail. El error se re-lanza
  // intacto (status + error.detail siguen disponibles en el componente).
  private manejarError(err: unknown): Observable<never> {
    const problem = (err as { error?: ProblemDetail })?.error;
    if (problem?.status) {
      console.warn(`[notificacion.service] ${problem.title} (${problem.status}): ${problem.detail}`);
    }
    return throwError(() => err);
  }
}