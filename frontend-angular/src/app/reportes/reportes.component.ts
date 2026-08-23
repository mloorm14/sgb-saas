import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReporteService, LibroMasPrestadoDetallado, ReporteMorosidad, ReporteInventario, ReporteVencidos, ReporteCategoriasDemandadas } from '../core/services/reporte-gerencial.service';

@Component({
  selector: 'app-reportes',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './reportes.component.html'
})
export class ReportesComponent implements OnInit {
  libros: LibroMasPrestadoDetallado[] = [];
  morosos: ReporteMorosidad[] = [];
  inventario: ReporteInventario[] = [];
  vencidos: ReporteVencidos[] = [];
  categorias: ReporteCategoriasDemandadas[] = [];

  fechaDesde = '';
  fechaHasta = '';
  limiteTop = 10;

  busquedaInventario = '';
  estadoStock = '';
  busquedaVencidos = '';
  diasAtrasoMin: number | null = null;
  limiteCategorias = 10;

  cargando = false;
  errorMsg = '';
  descargandoPdf = false;

  constructor(private reporteService: ReporteService) {}

  ngOnInit(): void {
    this.cargarTodos();
  }

  private cargarTodos(): void {
    this.cargando = true;
    this.errorMsg = '';
    const desde = this.fechaDesde ? this.fechaDesde + 'T00:00:00Z' : undefined;
    const hasta = this.fechaHasta ? this.fechaHasta + 'T23:59:59Z' : undefined;
    this.reporteService.librosMasPrestadosDetallado(desde, hasta, this.limiteTop).subscribe({
      next: (libros) => {
        this.libros = libros;
        this.cargarMorosidad();
      },
      error: (err) => this.fallar(err)
    });
  }

  private cargarMorosidad(): void {
    this.reporteService.morosidad().subscribe({
      next: (morosos) => {
        this.morosos = morosos;
        this.cargarInventario();
      },
      error: (err) => this.fallar(err)
    });
  }

  private cargarInventario(): void {
    this.reporteService.inventario(undefined, this.estadoStock || undefined, this.busquedaInventario || undefined).subscribe({
      next: (inventario) => {
        this.inventario = inventario;
        this.cargarVencidos();
      },
      error: (err) => this.fallar(err)
    });
  }

  private cargarVencidos(): void {
    this.reporteService.vencidos(this.diasAtrasoMin || undefined, this.busquedaVencidos || undefined).subscribe({
      next: (vencidos) => {
        this.vencidos = vencidos;
        this.cargarCategorias();
      },
      error: (err) => this.fallar(err)
    });
  }

  private cargarCategorias(): void {
    const desde = this.fechaDesde ? this.fechaDesde + 'T00:00:00Z' : undefined;
    const hasta = this.fechaHasta ? this.fechaHasta + 'T23:59:59Z' : undefined;
    this.reporteService.categoriasDemandadas(desde, hasta, this.limiteCategorias).subscribe({
      next: (categorias) => {
        this.categorias = categorias;
        this.cargando = false;
      },
      error: (err) => this.fallar(err)
    });
  }

  aplicarFiltros(): void {
    this.cargarTodos();
  }

  limpiarFiltros(): void {
    this.fechaDesde = '';
    this.fechaHasta = '';
    this.limiteTop = 10;
    this.busquedaInventario = '';
    this.estadoStock = '';
    this.busquedaVencidos = '';
    this.diasAtrasoMin = null;
    this.limiteCategorias = 10;
    this.cargarTodos();
  }

  cambiarLimite(): void {
    this.cargarTodos();
  }

  filtrarInventario(): void {
    this.cargando = true;
    this.errorMsg = '';
    this.reporteService.inventario(undefined, this.estadoStock || undefined, this.busquedaInventario || undefined).subscribe({
      next: (inventario) => {
        this.inventario = inventario;
        this.cargando = false;
      },
      error: (err) => this.fallar(err)
    });
  }

  filtrarVencidos(): void {
    this.cargando = true;
    this.errorMsg = '';
    this.reporteService.vencidos(this.diasAtrasoMin || undefined, this.busquedaVencidos || undefined).subscribe({
      next: (vencidos) => {
        this.vencidos = vencidos;
        this.cargando = false;
      },
      error: (err) => this.fallar(err)
    });
  }

  filtrarCategorias(): void {
    this.cargando = true;
    this.errorMsg = '';
    const desde = this.fechaDesde ? this.fechaDesde + 'T00:00:00Z' : undefined;
    const hasta = this.fechaHasta ? this.fechaHasta + 'T23:59:59Z' : undefined;
    this.reporteService.categoriasDemandadas(desde, hasta, this.limiteCategorias).subscribe({
      next: (categorias) => {
        this.categorias = categorias;
        this.cargando = false;
      },
      error: (err) => this.fallar(err)
    });
  }

  private fallar(err: unknown): void {
    this.cargando = false;
    this.errorMsg = (err as { error?: { detail?: string } })?.error?.detail
      || 'Error al cargar los reportes';
  }

  descargarMorosidadPdf(): void {
    this.descargandoPdf = true;
    this.errorMsg = '';
    this.reporteService.morosidadPdf().subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const enlace = document.createElement('a');
        enlace.href = url;
        enlace.download = 'reporte-morosidad.pdf';
        enlace.click();
        URL.revokeObjectURL(url);
        this.descargandoPdf = false;
      },
      error: (err) => {
        this.descargandoPdf = false;
        this.errorMsg = (err as { error?: { detail?: string } })?.error?.detail
          || 'Error al generar el PDF de morosidad';
      }
    });
  }

  fechaCorta(iso: string): string {
    if (!iso) return '—';
    const fecha = new Date(iso);
    return fecha.toLocaleDateString('es-ES', { day: '2-digit', month: 'short', year: 'numeric' });
  }
}
