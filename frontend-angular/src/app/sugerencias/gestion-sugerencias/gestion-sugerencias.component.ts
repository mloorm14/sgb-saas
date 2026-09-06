import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SugerenciaAdquisicionService } from '../../core/services/sugerencia-adquisicion.service';
import { SugerenciaAgrupada } from '../../core/models/sugerencia-adquisicion.model';

// Gestión por demanda (GERENTE/ADMIN): gráfica de columnas con lo más
// pedido arriba + tabla paginada abajo con botón "Confirmar adquisición"
// por ISBN. Al confirmar (botón o creando el libro en Libros), las PENDIENTE
// de ese ISBN pasan a APROBADA y salen del listado.
@Component({
  selector: 'app-gestion-sugerencias',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './gestion-sugerencias.component.html'
})
export class GestionSugerenciasComponent implements OnInit {
  masPedidos: SugerenciaAgrupada[] = [];
  totalPages = 0;
  currentPage = 0;
  pageSize = 10;
  cargando = false;
  errorMsg = '';
  confirmandoIsbn: string | null = null;

  constructor(private sugerenciaService: SugerenciaAdquisicionService) {}

  ngOnInit(): void {
    this.cargarPagina();
  }

  // ── Gráfica: top 8 de la página actual, barras en % del máximo ──
  get chartItems(): SugerenciaAgrupada[] {
    return this.masPedidos.slice(0, 8);
  }

  get maxCantidad(): number {
    return this.chartItems.reduce((max, item) => Math.max(max, item.cantidad), 0);
  }

  alturaBarra(cantidad: number): number {
    if (!this.maxCantidad) return 0;
    return Math.max(4, Math.round((cantidad / this.maxCantidad) * 100));
  }

  // ── Paginación (patrón proveedores) ──
  get paginasVisibles(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i);
  }

  get puedeAnterior(): boolean { return this.currentPage > 0; }
  get puedeSiguiente(): boolean { return this.currentPage < this.totalPages - 1; }

  irAPagina(pagina: number): void {
    if (pagina < 0 || pagina >= this.totalPages || pagina === this.currentPage) return;
    this.currentPage = pagina;
    this.cargarPagina();
  }

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

  cargarPagina(): void {
    this.cargando = true;
    this.errorMsg = '';
    this.sugerenciaService.listarMasPedidos({
      page: this.currentPage,
      size: this.pageSize
    }).subscribe({
      next: (data) => {
        this.masPedidos = data.content;
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

  confirmarAdquisicion(isbn: string): void {
    if (!isbn || this.confirmandoIsbn) return;
    this.confirmandoIsbn = isbn;
    this.errorMsg = '';
    this.sugerenciaService.confirmarAdquisicion(isbn).subscribe({
      next: () => {
        this.confirmandoIsbn = null;
        this.cargarPagina();
      },
      error: (err) => {
        this.confirmandoIsbn = null;
        this.errorMsg = (err as { error?: { detail?: string } })?.error?.detail
          || 'Error al confirmar la adquisición';
      }
    });
  }
}
