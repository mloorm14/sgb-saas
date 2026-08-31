import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription, forkJoin, of } from 'rxjs';
import { map, switchMap, takeUntil } from 'rxjs/operators';
import { ToastService } from '../../shared/toast/toast.service';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';

export interface BackupRequest {
  desde: string;
  hasta: string;
  tablas: string[];
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
  ruta: string;
  url_descarga: string;
}

@Component({
  selector: 'app-backups',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule
  ],
  templateUrl: './backups.component.html',
  styleUrls: ['./backups.component.css']
})
export class BackupsComponent implements OnInit, OnDestroy {

  desde: string = '';
  hasta: string = '';
  tablas: string[] = ['prestamos', 'reservas', 'multas', 'libros', 'usuarios', 'bitacora_auditoria'];
  formato: 'sql' | 'csv' = 'sql';
  loading = false;
  error: string | null = null;
  backups: BackupResumen[] = [];
  displayedColumns: string[] = ['fecha', 'desde', 'hasta', 'tablas', 'formato', 'tamano', 'estado', 'acciones'];
  dataSource: any[] = [];
  private subscriptions = new Subscription();

  constructor(
    private backupSvc: BackupService,
    private toast: ToastService,
    private confirm: ConfirmDialogService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.cargarHistorial();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  cargarHistorial(refresh = false): void {
    this.loading = true;
    this.backupSvc.listar().subscribe({
      next: (data) => {
        this.backups = data || [];
        this.dataSource = this.backups;
        this.length = this.backups.length;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Error cargando backups';
        this.toast.error('Error', this.error);
        this.loading = false;
      }
    });
  }

  generarBackup(): void {
    if (!this.desde || !this.hasta) {
      this.toast.warning('Atención', 'Complete ambos rangos de fecha.');
      return;
    }
    const desdeDate = new Date(this.desde);
    const hastaDate = new Date(this.hasta);
    if (desdeDate >= hastaDate) {
      this.toast.error('Error', 'La fecha "desde" debe ser anterior a "hasta".');
      return;
    }
    const diffDias = Math.ceil((hastaDate.getTime() - desdeDate.getTime()) / (1000 * 60 * 60 * 24));
    if (diffDias > 30) {
      this.toast.error('Error', `Rango máximo de 30 días. Diferencia: ${diffDias} días.`);
      return;
    }

    const request: BackupRequest = {
      desde: this.desde,
      hasta: this.hasta,
      tablas: this.tablas,
      formato: this.formato
    };

    this.loading = true;
    this.backupSvc.generar(request).subscribe({
      next: (detalle: any) => {
        this.toast.success('Éxito', `Backup generado (${detalle.formato.toUpperCase()}). ID: ${detalle.id}`);
        this.cargarHistorial(true);
        this.desde = '';
        this.hasta = '';
        this.tablas = ['prestamos', 'reservas', 'multas', 'libros', 'usuarios', 'bitacora_auditoria'];
        this.formato = 'sql';
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Error generando backup';
        this.toast.error('Error', this.error || 'Consulte los parámetros e intente nuevamente.');
        this.loading = false;
      }
    });
  }

  borrarBackup(id: number): void {
    this.confirm.confirmar(
      'Eliminar backup',
      `¿Está seguro de eliminar el backup #${id}?`
    ).subscribe({
      next: (confirmado) => {
        if (confirmado) {
          this.backupSvc.borrar(id).subscribe({
            next: (borrado) => {
              if (borrado) {
                this.toast.success('Eliminado', 'Backup eliminado correctamente.');
                this.cargarHistorial();
              }
            }
          });
        }
      }
    );
  }

  getNombreTabla(tabla: string): string {
    const map: Record<string, string> = {
      prestamos: 'Préstamos',
      reservas: 'Reservas',
      multas: 'Multas',
      libros: 'Libros',
      usuarios: 'Usuarios',
      bitacora_auditoria: 'Bitácora auditoría'
    };
    return map[tabla] || tabla;
  }

  sinDatos(): boolean {
    return this.backups.length === 0 && !this.loading;
  }
}