import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReporteService, LibroMasPrestadoDetallado, ReporteMorosidad, ReporteInventario, ReporteVencidos, ReporteCategoriasDemandadas } from '../core/services/reporte-gerencial.service';
import { Observable } from 'rxjs';
import * as XLSX from 'xlsx';

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
  descargandoPdf: string | null = null;

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

  aplicarFiltros(): void { this.cargarTodos(); }

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

  cambiarLimite(): void { this.cargarTodos(); }

  filtrarInventario(): void {
    this.cargando = true;
    this.errorMsg = '';
    this.reporteService.inventario(undefined, this.estadoStock || undefined, this.busquedaInventario || undefined).subscribe({
      next: (inventario) => { this.inventario = inventario; this.cargando = false; },
      error: (err) => this.fallar(err)
    });
  }

  filtrarVencidos(): void {
    this.cargando = true;
    this.errorMsg = '';
    this.reporteService.vencidos(this.diasAtrasoMin || undefined, this.busquedaVencidos || undefined).subscribe({
      next: (vencidos) => { this.vencidos = vencidos; this.cargando = false; },
      error: (err) => this.fallar(err)
    });
  }

  filtrarCategorias(): void {
    this.cargando = true;
    this.errorMsg = '';
    const desde = this.fechaDesde ? this.fechaDesde + 'T00:00:00Z' : undefined;
    const hasta = this.fechaHasta ? this.fechaHasta + 'T23:59:59Z' : undefined;
    this.reporteService.categoriasDemandadas(desde, hasta, this.limiteCategorias).subscribe({
      next: (categorias) => { this.categorias = categorias; this.cargando = false; },
      error: (err) => this.fallar(err)
    });
  }

  private fallar(err: unknown): void {
    this.cargando = false;
    this.errorMsg = (err as { error?: { detail?: string } })?.error?.detail
      || 'Error al cargar los reportes';
  }

  // ── PDF downloads ──────────────────────────────────────
  private descargarPdf(blob$: Observable<Blob>, nombre: string, clave: string): void {
    this.descargandoPdf = clave;
    this.errorMsg = '';
    blob$.subscribe({
      next: (blob: Blob) => {
        const url = URL.createObjectURL(blob);
        const enlace = document.createElement('a');
        enlace.href = url;
        enlace.download = nombre;
        enlace.click();
        URL.revokeObjectURL(url);
        this.descargandoPdf = null;
      },
      error: (err: unknown) => {
        this.descargandoPdf = null;
        this.errorMsg = (err as { error?: { detail?: string } })?.error?.detail || 'Error al generar el PDF';
      }
    });
  }

  descargarLibrosPdf(): void {
    const desde = this.fechaDesde ? this.fechaDesde + 'T00:00:00Z' : undefined;
    const hasta = this.fechaHasta ? this.fechaHasta + 'T23:59:59Z' : undefined;
    this.descargarPdf(
      this.reporteService.librosMasPrestadosPdf(desde, hasta, this.limiteTop),
      'reporte-libros-prestados.pdf', 'libros-pdf'
    );
  }

  descargarMorosidadPdf(): void {
    this.descargarPdf(this.reporteService.morosidadPdf(), 'reporte-morosidad.pdf', 'morosidad-pdf');
  }

  descargarInventarioPdf(): void {
    this.descargarPdf(
      this.reporteService.inventarioPdf(this.estadoStock || undefined, this.busquedaInventario || undefined),
      'reporte-inventario.pdf', 'inventario-pdf'
    );
  }

  descargarVencidosPdf(): void {
    this.descargarPdf(
      this.reporteService.vencidosPdf(this.diasAtrasoMin || undefined, this.busquedaVencidos || undefined),
      'reporte-vencidos.pdf', 'vencidos-pdf'
    );
  }

  descargarCategoriasPdf(): void {
    const desde = this.fechaDesde ? this.fechaDesde + 'T00:00:00Z' : undefined;
    const hasta = this.fechaHasta ? this.fechaHasta + 'T23:59:59Z' : undefined;
    this.descargarPdf(
      this.reporteService.categoriasDemandadasPdf(desde, hasta, this.limiteCategorias),
      'reporte-categorias.pdf', 'categorias-pdf'
    );
  }

  // ── Excel downloads ────────────────────────────────────
  private generarExcel(headers: string[], rows: (string | number)[][], nombre: string, sheetName: string): void {
    const wb = XLSX.utils.book_new();
    const wsData = [headers, ...rows];
    const ws = XLSX.utils.aoa_to_sheet(wsData);

    // Estilos de ancho de columna
    ws['!cols'] = headers.map((_, i) => {
      const maxLen = Math.max(
        headers[i].length,
        ...rows.map(r => String(r[i] ?? '').length)
      );
      return { wch: Math.min(maxLen + 4, 40) };
    });

    XLSX.utils.book_append_sheet(wb, ws, sheetName);
    XLSX.writeFile(wb, nombre, { bookType: 'xlsx' });
  }

  excelLibros(): void {
    const headers = ['#', 'Título', 'ISBN', 'Autor', 'Categoría', 'Préstamos', '% del total'];
    const rows = this.libros.map((l, i) => [i + 1, l.titulo, l.isbn || '—', l.autorNombre || '—', l.categoriaNombre || '—', l.totalPrestamos, l.porcentaje + '%']);
    this.generarExcel(headers, rows, 'reporte-libros-prestados.xlsx', 'Libros más prestados');
  }

  excelMorosidad(): void {
    const headers = ['Usuario', 'Correo', 'Deuda', 'Multas pendientes', 'Días atraso (prom.)'];
    const rows = this.morosos.map(m => [m.nombre + ' ' + m.apellido, m.correo, m.montoTotalAdeudado, m.cantidadMultasPendientes, m.diasAtrasoPromedio]);
    this.generarExcel(headers, rows, 'reporte-morosidad.xlsx', 'Morosidad');
  }

  excelInventario(): void {
    const headers = ['Título', 'ISBN', 'Autor', 'Categoría', 'Stock', 'Disponible', 'Estado'];
    const rows = this.inventario.map(i => [i.titulo, i.isbn || '—', i.autorNombre || '—', i.categoriaNombre || '—', i.stockTotal, i.stockDisponible, i.estadoDisponibilidad]);
    this.generarExcel(headers, rows, 'reporte-inventario.xlsx', 'Inventario');
  }

  excelVencidos(): void {
    const headers = ['Usuario', 'Correo', 'Libro', 'ISBN', 'Vencimiento', 'Días atraso', 'Multa estimada'];
    const rows = this.vencidos.map(v => [v.usuarioNombre, v.usuarioCorreo, v.libroTitulo, v.libroIsbn || '—', this.fechaCorta(v.fechaDevolucionEstimada), v.diasAtraso, v.montoMultaEstimada]);
    this.generarExcel(headers, rows, 'reporte-vencidos.xlsx', 'Préstamos vencidos');
  }

  excelCategorias(): void {
    const headers = ['#', 'Categoría', 'Préstamos', '% del total'];
    const rows = this.categorias.map((c, i) => [i + 1, c.categoriaNombre, c.totalPrestamos, c.porcentaje + '%']);
    this.generarExcel(headers, rows, 'reporte-categorias.xlsx', 'Categorías demandadas');
  }

  fechaCorta(iso: string): string {
    if (!iso) return '—';
    const fecha = new Date(iso);
    return fecha.toLocaleDateString('es-ES', { day: '2-digit', month: 'short', year: 'numeric' });
  }
}
