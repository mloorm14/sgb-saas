import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SugerenciaAdquisicionService } from '../../core/services/sugerencia-adquisicion.service';
import { SugerenciaAdquisicion } from '../../core/models/sugerencia-adquisicion.model';

// Filtros del mockup 22. El valor se manda tal cual a ?estado=; el
// backend filtra por el estado literal del catálogo (PENDIENTE/APROBADA/
// RECHAZADA). "Todas" no envía el parámetro.
type FiltroEstado = 'PENDIENTE' | 'APROBADA' | 'RECHAZADA' | '';

@Component({
  selector: 'app-gestion-sugerencias',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './gestion-sugerencias.component.html'
})
export class GestionSugerenciasComponent implements OnInit {
  filtroEstado: FiltroEstado = 'PENDIENTE';

  sugerencias: SugerenciaAdquisicion[] = [];
  totalPages: number = 0;
  currentPage: number = 0;
  pageSize: number = 10;
  cargando: boolean = false;
  errorMsg: string = '';
  cambiandoId: number | null = null;

  constructor(private sugerenciaService: SugerenciaAdquisicionService) {}

  ngOnInit(): void {
    this.cargarPagina();
  }

  get paginasVisibles(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i);
  }

  cambiarFiltro(estado: FiltroEstado): void {
    if (this.filtroEstado === estado) return;
    this.filtroEstado = estado;
    this.currentPage = 0;
    this.cargarPagina();
  }

  // Se llama desde el template (paginacion numerada) -> no private.
  cargarPagina(): void {
    this.cargando = true;
    this.errorMsg = '';
    this.sugerenciaService.listarTodas(this.filtroEstado, {
      page: this.currentPage,
      size: this.pageSize,
      sort: 'creadoEn,desc'
    }).subscribe({
      next: (data) => {
        this.sugerencias = data.content;
        this.totalPages = data.totalPages;
        this.cargando = false;
      },
      error: (err) => {
        this.errorMsg = (err as { error?: { detail?: string } })?.error?.detail
          || 'Error al cargar las sugerencias';
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

  // El backend solo acepta APROBADA o RECHAZADA (CambioEstadoSugerenciaRequestDTO
  // @Pattern) -- no hay botón para volver a Pendiente.
  aprobar(sugerencia: SugerenciaAdquisicion): void {
    this.cambiarEstado(sugerencia, 'APROBADA');
  }

  rechazar(sugerencia: SugerenciaAdquisicion): void {
    this.cambiarEstado(sugerencia, 'RECHAZADA');
  }

  private cambiarEstado(sugerencia: SugerenciaAdquisicion, nuevoEstado: 'APROBADA' | 'RECHAZADA'): void {
    this.cambiandoId = sugerencia.id;
    this.errorMsg = '';
    this.sugerenciaService.cambiarEstado(sugerencia.id, nuevoEstado).subscribe({
      next: () => {
        this.cambiandoId = null;
        this.cargarPagina();
      },
      error: (err) => {
        this.cambiandoId = null;
        this.errorMsg = (err as { error?: { detail?: string } })?.error?.detail
          || 'Error al cambiar el estado de la sugerencia';
      }
    });
  }

  // El DTO trae usuarioId (Long), no el correo del solicitante -- se
  // muestra el id en vez de inventar un campo que no existe.
  solicitanteLabel(sugerencia: SugerenciaAdquisicion): string {
    return `Usuario #${sugerencia.usuarioId}`;
  }

  // Muestra quién revisó la sugerencia (solo cuando ya no está PENDIENTE).
  // El DTO trae revisadoPor (number, id del usuario que revisó).
  // No hay endpoint para resolver id->nombre: queda como "Usuario #{id}".
  // TODO: cuando exista endpoint de catálogo de usuarios, resolver nombre real.
  revisadoPorLabel(sugerencia: SugerenciaAdquisicion): string {
    if (!sugerencia.revisadoPor) return '—';
    return `Usuario #${sugerencia.revisadoPor}`;
  }
}