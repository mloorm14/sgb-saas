import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { SugerenciaAdquisicionService } from '../../core/services/sugerencia-adquisicion.service';
import { SugerenciaAdquisicion } from '../../core/models/sugerencia-adquisicion.model';

// Mis solicitudes de adquisición: lista paginada por creadoEn.
// Estados por color: Pendiente (ámbar),
// Aprobada (verde) y Rechazada (rojo error-container).
@Component({
  standalone: true,
  selector: 'app-mis-sugerencias',
  imports: [CommonModule, FormsModule, RouterLink],
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

  get puedeAnterior(): boolean { return this.currentPage > 0; }
  get puedeSiguiente(): boolean { return this.currentPage < this.totalPages - 1; }

  paginaAnterior(): void {
    if (!this.puedeAnterior) return;
    this.currentPage--;
    this.cargarPagina();
  }

  paginaSiguiente(): void {
    if (!this.puedeSiguiente) return;
    this.currentPage++;
    this.cargarPagina();
  }

  cambiarTamano(n: number): void {
    this.pageSize = Number(n);
    this.currentPage = 0;
    this.cargarPagina();
  }
}