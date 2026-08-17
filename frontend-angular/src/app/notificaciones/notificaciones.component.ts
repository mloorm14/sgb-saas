import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NotificacionService } from '../core/services/notificacion.service';
import { Notificacion } from '../core/models/notificacion.model';
import { Page } from '../core/models/pagina.model';

// Mis notificaciones (Rama B, mockup 10). El backend devuelve Page<NotificacionResponseDTO>
// con paginación (page/size). La LECTOR ve solo las suyas (enforced server-side).
@Component({
  selector: 'app-notificaciones',
  imports: [CommonModule],
  templateUrl: './notificaciones.component.html'
})
export class NotificacionesComponent implements OnInit {
  notificaciones: Notificacion[] = [];
  totalPages = 0;
  currentPage = 0;
  pageSize = 10;
  cargando = false;
  errorMsg = '';

  constructor(private notificacionService: NotificacionService) {}

  ngOnInit(): void {
    this.cargarPagina();
  }

  cargarPagina(): void {
    this.cargando = true;
    this.errorMsg = '';
    this.notificacionService.listar({ page: this.currentPage, size: this.pageSize }).subscribe({
      next: (data: Page<Notificacion>) => {
        this.notificaciones = data.content;
        this.totalPages = data.totalPages;
        this.cargando = false;
      },
      error: () => {
        this.errorMsg = 'Error al cargar las notificaciones';
        this.cargando = false;
      }
    });
  }

  paginaAnterior(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.cargarPagina();
    }
  }

  paginaSiguiente(): void {
    if (this.currentPage < this.totalPages - 1) {
      this.currentPage++;
      this.cargarPagina();
    }
  }

  irAPagina(pagina: number): void {
    if (pagina >= 0 && pagina < this.totalPages && pagina !== this.currentPage) {
      this.currentPage = pagina;
      this.cargarPagina();
    }
  }

  get paginasVisibles(): number[] {
    if (this.totalPages <= 5) {
      return Array.from({ length: this.totalPages }, (_, i) => i);
    }
    const inicio = Math.max(0, Math.min(this.currentPage - 2, this.totalPages - 5));
    return Array.from({ length: 5 }, (_, i) => inicio + i);
  }

  // "12 ago 2026 14:30": el backend envía ISO (OffsetDateTime).
  formatearFecha(iso: string): string {
    if (!iso) return '';
    const fecha = new Date(iso);
    const meses = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
    return `${fecha.getDate()} ${meses[fecha.getMonth()]} ${fecha.getFullYear()} ${fecha.getHours().toString().padStart(2, '0')}:${fecha.getMinutes().toString().padStart(2, '0')}`;
  }

  badgeEnviadoOk(enviadoOk: boolean): string {
    return enviadoOk ? 'bg-success/15 text-success' : 'bg-error-container text-on-error-container';
  }

  textoEnviado(enviadoOk: boolean): string {
    return enviadoOk ? 'Enviada' : 'No enviada';
  }
}