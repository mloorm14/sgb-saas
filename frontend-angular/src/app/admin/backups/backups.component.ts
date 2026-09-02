import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { ToastService } from '../../shared/toast/toast.service';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';
import { BackupService, BackupRequest, BackupResumen } from '../../core/services/backup.service';

@Component({
  selector: 'app-backups',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './backups.component.html',
  styleUrls: ['./backups.component.css']
})
export class BackupsComponent implements OnInit, OnDestroy {
  desde = '';
  hasta = '';
  tablas: string[] = ['prestamos', 'reservas', 'multas', 'libros', 'usuarios', 'bitacora_auditoria'];
  formato: 'sql' | 'csv' = 'sql';
  loading = false;
  error: string | null = null;
  backups: BackupResumen[] = [];
  dataSource: BackupResumen[] = [];
  private subs = new Subscription();

  constructor(private backupSvc: BackupService, private toast: ToastService, private confirm: ConfirmDialogService) {}

  ngOnInit(): void { this.cargarHistorial(); }
  ngOnDestroy(): void { this.subs.unsubscribe(); }

  cargarHistorial(): void {
    this.loading = true;
    this.subs.add(this.backupSvc.listar().subscribe({
      next: (data: BackupResumen[]) => { this.backups = data || []; this.dataSource = this.backups; this.loading = false; },
      error: (err: unknown) => { this.error = 'Error cargando backups'; this.toast.error('Error', this.error); this.loading = false; }
    }));
  }

  generarBackup(): void {
    if (!this.desde || !this.hasta) { this.toast.warning('Atención', 'Complete ambos rangos de fecha.'); return; }
    const desdeDate = new Date(this.desde); const hastaDate = new Date(this.hasta);
    if (desdeDate >= hastaDate) { this.toast.error('Error', 'La fecha "desde" debe ser anterior a "hasta".'); return; }
    const diffDias = Math.ceil((hastaDate.getTime() - desdeDate.getTime()) / (1000*60*60*24));
    if (diffDias > 30) { this.toast.error('Error', `Rango máximo 30 días. Diferencia: ${diffDias} días.`); return; }
    const request: BackupRequest = { desde: this.desde, hasta: this.hasta, tablas: this.tablas, formato: this.formato };
    this.loading = true;
    this.subs.add(this.backupSvc.generar(request).subscribe({
      next: (detalle: unknown) => {
        const d = detalle as BackupResumen;
        this.toast.success('Éxito', `Backup generado (${d?.formato?.toUpperCase()}). ID: ${d?.id}`);
        this.cargarHistorial();
        this.loading = false;
      },
      error: (err: unknown) => { this.error = 'Error generando backup'; this.toast.error('Error', this.error); this.loading = false; }
    }));
  }

  descargarBackup(id: number | string): void {
    this.subs.add(this.backupSvc.descargar(id).subscribe((blob: Blob | null) => {
      if (!blob) return;
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a'); a.href = url; a.download = `backup_${id}.zip`; a.click(); URL.revokeObjectURL(url);
    }));
  }

  borrarBackup(id: number | string): void {
    this.subs.add(this.confirm.confirm({ title: 'Eliminar backup', message: `¿Está seguro de eliminar el backup #${id}?`, variant: 'danger' }).subscribe((confirmado: boolean) => {
      if (!confirmado) return;
      this.subs.add(this.backupSvc.borrar(id).subscribe((ok: boolean) => {
        if (ok) { this.toast.success('Eliminado', 'Backup eliminado correctamente.'); this.cargarHistorial(); }
      }));
    }));
  }

  getNombreTabla(tabla: string): string {
    const map: Record<string,string> = { prestamos:'Préstamos', reservas:'Reservas', multas:'Multas', libros:'Libros', usuarios:'Usuarios', bitacora_auditoria:'Bitácora auditoría' };
    return map[tabla] || tabla;
  }
  sinDatos(): boolean { return this.backups.length === 0 && !this.loading; }
}
