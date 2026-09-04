import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { catchError, map, Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page, normalizarPagina } from '../models/pagina.model';
import { ProblemDetail } from '../models/problem-detail.model';
import { UsuarioAdmin } from '../models/usuario-admin.model';

// Contrato de UsuarioAdminController (/api/v1/admin/usuarios), verificado
// en backend-springboot: el listado es ADMIN/GERENTE; los PATCH de rol y
// estado son SOLO ADMIN. El backend filtra por nombre o correo
// (UsuarioAdminService: findByNombreContainingIgnoreCaseOrCorreoContainingIgnoreCase).
@Injectable({
  providedIn: 'root'
})
export class UsuarioAdminService {

  private apiUrl = `${environment.apiUrl}/v1/admin/usuarios`;

  constructor(private http: HttpClient) {}

  listar(filtro: string, page: number, size: number, mios = false): Observable<Page<UsuarioAdmin>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size);
    if (filtro.trim()) {
      params = params.set('filtro', filtro.trim());
    }
    // F8-gerente: ?mios=true filtra por creado_por propio en el backend (V38).
    if (mios) {
      params = params.set('mios', 'true');
    }
    return this.http.get<Page<UsuarioAdmin>>(this.apiUrl, { params }).pipe(
      map(data => normalizarPagina(data)),
      catchError(err => this.manejarError(err))
    );
  }

  // F8-gerente: POST /v1/admin/usuarios (GERENTE limitado a LECTOR/BIBLIOTECARIO en backend).
  crear(payload: { nombre: string; apellido: string; correo: string; password: string; rol: string }): Observable<unknown> {
    return this.http.post<unknown>(this.apiUrl, payload).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // Solo ADMIN en backend (DELETE soft a INACTIVO).
  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // PATCH /{id}/rol -- body { nuevoRol } (CambioRolRequestDTO: @NotBlank)
  cambiarRol(id: number, nuevoRol: string): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/${id}/rol`, { nuevoRol }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  // PATCH /{id}/estado -- body { nuevoEstado, motivo } (el motivo es
  // @NotBlank en CambioEstadoUsuarioRequestDTO y queda en la bitácora).
  cambiarEstado(id: number, nuevoEstado: string, motivo: string): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/${id}/estado`, { nuevoEstado, motivo }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  private manejarError(err: unknown): Observable<never> {
    const problem = (err as { error?: ProblemDetail })?.error;
    if (problem?.status) {
      console.warn(`[usuario-admin.service] ${problem.title} (${problem.status}): ${problem.detail}`);
    }
    return throwError(() => err);
  }
}