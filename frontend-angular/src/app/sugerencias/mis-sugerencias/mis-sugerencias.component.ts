import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { SugerenciaAdquisicionService } from '../../core/services/sugerencia-adquisicion.service';
import { SugerenciaAdquisicion } from '../../core/models/sugerencia-adquisicion.model';

// Mis solicitudes de adquisición (Rama B, mockup 08). listarMias es
// paginado (Page<SugerenciaAdquisicion>, sort por creadoEn). Los badges de
// estado usan los 3 colores exactos del mockup: Pendiente (ámbar),
// Aprobada (verde) y Rechazada (rojo error-container).
@Component({
  standalone: true,
  selector: 'app-mis-sugerencias',
  imports: [CommonModule, RouterLink],
  templateUrl: './mis-sugerencias.component.html'
})
export class MisSugerenciasComponent implements OnInit {
  sugerencias: SugerenciaAdquisicion[] = [];
  totalPages: number = 0;
  currentPage: number = 0;
  pageSize: number = 10;
  cargando: boolean = false;
  errorMsg: string = '';

  constructor(private sugerenciaService: SugerenciaAdquisicionService) {}

  ngOnInit(): void {
    this.cargarPagina();
  }

  private cargarPagina(): void {
    this.cargando = true;
    this.sugerenciaService.listarMias({
      page: this.currentPage,
      size: this.pageSize,
      sort: 'creadoEn,desc'
    }).subscribe({
      next: (data) => {
        this.sugerencias = data.content;
        this.totalPages = data.totalPages;
        this.cargando = false;
      },
      error: () => {
        this.errorMsg = 'Error al cargar las solicitudes';
        this.cargando = false;
      }
    });
  }

  estadoTexto(estado: string): string {
    return estado.charAt(0) + estado.slice(1).toLowerCase();
  }

  // Colores inline del mockup 08 (no existen como tokens en tailwind.config):
  // Pendiente -> ámbar, Aprobada -> verde.
  badgeEstilo(estado: string): Record<string, string> {
    if (estado === 'APROBADA') {
      return { background: '#dff7ee', color: '#0f6e56' };
    }
    return { background: '#fff6d9', color: '#7a5c00' };
  }

  badgeIcono(estado: string): string {
    if (estado === 'APROBADA') return 'check_circle';
    if (estado === 'RECHAZADA') return 'cancel';
    return 'schedule';
  }

  esRechazada(estado: string): boolean {
    return estado === 'RECHAZADA';
  }

  // "10 ago 2026": el backend envía ISO (LocalDateTime).
  formatearFecha(iso: string): string {
    if (!iso) return '';
    const fecha = new Date(iso);
    const meses = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
    return `${fecha.getDate()} ${meses[fecha.getMonth()]} ${fecha.getFullYear()}`;
  }

  get paginasVisibles(): number[] {
    if (this.totalPages <= 5) {
      return Array.from({ length: this.totalPages }, (_, i) => i);
    }
    const inicio = Math.max(0, Math.min(this.currentPage - 2, this.totalPages - 5));
    return Array.from({ length: 5 }, (_, i) => inicio + i);
  }

  irAPagina(pagina: number): void {
    if (pagina < 0 || pagina >= this.totalPages || pagina === this.currentPage) return;
    this.currentPage = pagina;
    this.cargarPagina();
  }
}