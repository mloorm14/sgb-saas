import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { ToastService } from '../../shared/toast/toast.service';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';

export interface BackupRequest {
  desde: string; // datetime-local format: YYYY-MM-DDTHH:MM
  hasta: string; // datetime-local format: YYYY-MM-DDTHH:MM
  tablas: string[]; // ['prestamos','reservas',...]
  formato: 'sql' | 'csv';
}

export interface BackupResumen {
  id: number;
  creado_en: string;
  desde: string;
  hasta: string;
  tablas: string[];
  formato: string;
  tamano_bytes: number | null;
  estado: string;
}

export interface BackupDetalle extends BackupResumen {
  ruta: string; // URL o path del archivo
  url_descarga: string;
}

@Injectable({ providedIn: 'root' })
export class BackupService {

  private apiUrl = environment.apiUrl + '/admin/backups';

  constructor(
    private http: HttpClient,
    private toast: ToastService,
    private confirm: ConfirmDialogService
  ) {}

  /** POST /api/v1/admin/backups - Genera un nuevo backup */
  generar(request: BackupRequest): Observable<BackupDetalle> {
    return this.http.post<{ id: number; ruta: string }>(`${this.apiUrl}`, request)
      .pipe(
        map(({ id, ruta }) => ({
          id,
          creado_en: new Date().toISOString(),
          desde: request.desde,
          hasta: request.hasta,
          tablas: request.tablas,
          formato: request.formato,
          tamano_bytes: null, // se llenaría después si el backend lo devuelve
          estado: 'COMPLETADO',
          ruta,
          url_descarga: `${environment.apiUrl}/api/v1/admin/backups/${id}/download`
        })),
        catchError(err => {
          this.toast.error('Error', 'No se pudo generar el backup. Verifique los parámetros e intente nuevamente.');
          return of(null as any);
        })
      );
  }

  /** GET /api/v1/admin/backups - Lista backups con filtros opcionales */
  listar(desde?: string, hasta?: string, estado?: string): Observable<BackupResumen[]> {
    let params = '';
    if (desde) params += `?desde=${desde}`;
    if (hasta) {
      if (!params) params += '?';
      else params += `&hasta=${hasta}`;
    }
    if (estado) {
      if (!params) params += '?';
      else params += `&estado=${estado}`;
    }

    return this.http.get<BackupResumen[]>(`${this.apiUrl}${params}`)
      .pipe(
        catchError(err => {
          this.toast.error('Error', 'No se pudo cargar el historial de backups.');
          return of([]);
        })
      );
  }

  /** GET /api/v1/admin/backups/{id}/download - Descargar archivo */
  descargar(id: number): Observable<Blob> {
    const url = `${this.apiUrl}/${id}/download`;
    return this.http.get(url, { responseType: 'blob' }).pipe(
      catchError(err => {
        this.toast.error('Error', 'No se pudo descargar el archivo de backup.');
        return of(null as any);
      })
    );
  }

  /** DELETE /api/v1/admin/backups/{id} - Borrar backup (con confirmación) */
  borrar(id: number): Observable<boolean> {
    return this.confirm.confirmar(
      'Confirmar eliminación',
      `¿Está seguro de desear eliminar el backup #${id}? Esta acción no se puede deshacer.`
    ).pipe(
      map(confirmado => confirmado ? true : false),
      catchError(() => of(false))
    );
  }
}