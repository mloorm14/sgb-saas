import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReporteService, LibroMasPrestadoDetallado, ReporteMorosidad, ReporteInventario, ReporteVencidos, ReporteCategoriasDemandadas } from '../core/services/reporte-gerencial.service';
import { Observable } from 'rxjs';

export type VistaReporte = 'tarjetas' | 'libros' | 'morosidad' | 'inventario' | 'vencidos' | 'categorias';

export interface ModuloReporte {
  id: Exclude<VistaReporte, 'tarjetas'>;
  codigo: string;
  etiqueta: string;
  descripcion: string;
  icono: string;
  color: string;
}

@Component({
  selector: 'app-reportes',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './reportes.component.html'
})
export class ReportesComponent {
  vista: VistaReporte = 'tarjetas';

  modulos: ModuloReporte[] = [
    {
      id: 'libros',
      codigo: 'RPT-LIB',
      etiqueta: 'Libros más prestados',
      descripcion: 'Ranking de títulos con más préstamos',
      icono: 'menu_book',
      color: 'bg-primary/10 text-primary'
    },
    {
      id: 'morosidad',
      codigo: 'RPT-MOR',
      etiqueta: 'Morosidad',
      descripcion: 'Usuarios con deuda y atraso',
      icono: 'payments',
      color: 'bg-error/10 text-error'
    },
    {
      id: 'inventario',
      codigo: 'RPT-INV',
      etiqueta: 'Inventario y disponibilidad',
      descripcion: 'Stock, disponibilidad y estado',
      icono: 'inventory_2',
      color: 'bg-secondary/10 text-secondary'
    },
    {
      id: 'vencidos',
      codigo: 'RPT-VEN',
      etiqueta: 'Préstamos vencidos activos',
      descripcion: 'Préstamos sin devolver fuera de plazo',
      icono: 'event_busy',
      color: 'bg-tertiary/10 text-tertiary'
    },
    {
      id: 'categorias',
      codigo: 'RPT-CAT',
      etiqueta: 'Categorías más demandadas',
      descripcion: 'Categorías con mayor demanda de préstamos',
      icono: 'category',
      color: 'bg-success/10 text-success'
    }
  ];

  libros: LibroMasPrestadoDetallado[] = [];
  morosos: ReporteMorosidad[] = [];
  inventario: ReporteInventario[] = [];
  vencidos: ReporteVencidos[] = [];
  categorias: ReporteCategoriasDemandadas[] = [];

  private morososTodos: ReporteMorosidad[] = [];
  private vencidosTodos: ReporteVencidos[] = [];
  private categoriasTodas: ReporteCategoriasDemandadas[] = [];

  // Libros más prestados
  librosDesde = '';
  librosHasta = '';
  librosDia = '';
  librosTipoDias: number | null = null;
  limiteTop = 10;

  // Morosidad
  morosidadCorreo = '';
  morosidadDesde = '';
  morosidadHasta = '';
  morosidadDia = '';
  morosidadTipoDias: number | null = null;

  // Inventario
  busquedaInventario = '';
  estadoStock = '';
  inventarioPage = 0;
  inventarioPageSize = 10;

  // Vencidos
  vencidosCorreo = '';
  vencidosLibro = '';
  vencidosIsbn = '';
  vencidosDesde = '';
  vencidosHasta = '';
  vencidosDia = '';
  vencidosTipoDias: number | null = null;

  // Categorías
  categoriasBusqueda = '';
  limiteCategorias = 10;

  cargando = false;
  errorMsg = '';
  descargandoPdf: string | null = null;

  constructor(private reporteService: ReporteService) {}

  abrirModulo(id: Exclude<VistaReporte, 'tarjetas'>): void {
    this.vista = id;
    this.errorMsg = '';
    this.cargarModuloActual();
  }

  volverATarjetas(): void {
    this.vista = 'tarjetas';
    this.errorMsg = '';
  }

  etiquetaModulo(id: VistaReporte): string {
    return this.modulos.find(m => m.id === id)?.etiqueta ?? 'Reportes';
  }

  aplicarFiltros(): void {
    this.cargarModuloActual();
  }

  limpiarFiltros(): void {
    switch (this.vista) {
      case 'libros':
        this.librosDesde = '';
        this.librosHasta = '';
        this.librosDia = '';
        this.librosTipoDias = null;
        this.limiteTop = 10;
        break;
      case 'morosidad':
        this.morosidadCorreo = '';
        this.morosidadDesde = '';
        this.morosidadHasta = '';
        this.morosidadDia = '';
        this.morosidadTipoDias = null;
        break;
      case 'inventario':
        this.busquedaInventario = '';
        this.estadoStock = '';
        this.inventarioPage = 0;
        break;
      case 'vencidos':
        this.vencidosCorreo = '';
        this.vencidosLibro = '';
        this.vencidosIsbn = '';
        this.vencidosDesde = '';
        this.vencidosHasta = '';
        this.vencidosDia = '';
        this.vencidosTipoDias = null;
        break;
      case 'categorias':
        this.categoriasBusqueda = '';
        this.limiteCategorias = 10;
        break;
    }
    this.cargarModuloActual();
  }

  private cargarModuloActual(): void {
    switch (this.vista) {
      case 'libros': this.cargarLibros(); break;
      case 'morosidad': this.cargarMorosidad(); break;
      case 'inventario': this.cargarInventario(); break;
      case 'vencidos': this.cargarVencidos(); break;
      case 'categorias': this.cargarCategorias(); break;
    }
  }

  private cargarLibros(): void {
    this.cargando = true;
    this.errorMsg = '';
    const rango = this.rangoIso(this.librosDesde, this.librosHasta, this.librosDia, this.librosTipoDias);
    this.reporteService.librosMasPrestadosDetallado(rango.desde, rango.hasta, this.limiteTop).subscribe({
      next: (libros) => {
        this.libros = libros;
        this.cargando = false;
      },
      error: (err) => this.fallar(err)
    });
  }

  private cargarMorosidad(): void {
    this.cargando = true;
    this.errorMsg = '';
    this.reporteService.morosidad().subscribe({
      next: (morosos) => {
        this.morososTodos = morosos;
        this.morosos = this.filtrarMorosidad(morosos);
        this.cargando = false;
      },
      error: (err) => this.fallar(err)
    });
  }

  private filtrarMorosidad(lista: ReporteMorosidad[]): ReporteMorosidad[] {
    const correo = this.morosidadCorreo.trim().toLowerCase();
    const tipo = this.normalizarTipoDias(this.morosidadTipoDias);
    return lista.filter(m => {
      if (correo && !(m.correo ?? '').toLowerCase().includes(correo)) return false;
      if (tipo != null) {
        const dias = Number(m.diasAtrasoPromedio);
        if (Number.isNaN(dias) || dias < 1 || dias > tipo) return false;
      }
      return true;
    });
  }

  private cargarInventario(): void {
    this.cargando = true;
    this.errorMsg = '';
    this.reporteService.inventario(undefined, this.estadoStock || undefined, this.busquedaInventario.trim() || undefined).subscribe({
      next: (inventario) => {
        this.inventario = inventario;
        this.inventarioPage = 0;
        this.cargando = false;
      },
      error: (err) => this.fallar(err)
    });
  }

  // ── Paginación inventario (igual que libros) ──
  get inventarioTotalPages(): number {
    return Math.max(1, Math.ceil(this.inventario.length / this.inventarioPageSize));
  }
  get inventarioPaginado(): ReporteInventario[] {
    const start = this.inventarioPage * this.inventarioPageSize;
    return this.inventario.slice(start, start + this.inventarioPageSize);
  }
  get inventarioPaginasVisibles(): number[] {
    const windowSize = 4;
    let start = Math.max(0, this.inventarioPage - 1);
    let end = Math.min(this.inventarioTotalPages, start + windowSize);
    if (end - start < windowSize) start = Math.max(0, end - windowSize);
    return Array.from({ length: end - start }, (_, i) => start + i);
  }
  get puedeInvAnterior(): boolean { return this.inventarioPage > 0; }
  get puedeInvSiguiente(): boolean { return this.inventarioPage < this.inventarioTotalPages - 1; }
  irAInventarioPage(p: number): void {
    if (p < 0 || p >= this.inventarioTotalPages || p === this.inventarioPage) return;
    this.inventarioPage = p;
  }
  paginaAnteriorInventario(): void { if (this.puedeInvAnterior) this.inventarioPage--; }
  paginaSiguienteInventario(): void { if (this.puedeInvSiguiente) this.inventarioPage++; }
  cambiarTamanoInventario(n: number): void {
    this.inventarioPageSize = Number(n);
    this.inventarioPage = 0;
  }

  private cargarVencidos(): void {
    this.cargando = true;
    this.errorMsg = '';
    const busqueda = this.vencidosCorreo.trim() || this.vencidosLibro.trim() || this.vencidosIsbn.trim() || undefined;
    this.reporteService.vencidos(undefined, busqueda).subscribe({
      next: (vencidos) => {
        this.vencidosTodos = vencidos;
        this.vencidos = this.filtrarVencidos(vencidos);
        this.cargando = false;
      },
      error: (err) => this.fallar(err)
    });
  }

  private filtrarVencidos(lista: ReporteVencidos[]): ReporteVencidos[] {
    const correo = this.vencidosCorreo.trim().toLowerCase();
    const libro = this.vencidosLibro.trim().toLowerCase();
    const isbn = this.vencidosIsbn.trim().toLowerCase();
    const tipo = this.normalizarTipoDias(this.vencidosTipoDias);
    return lista.filter(v => {
      if (correo && !(v.usuarioCorreo ?? '').toLowerCase().includes(correo)) return false;
      if (libro && !(v.libroTitulo ?? '').toLowerCase().includes(libro)) return false;
      if (isbn && !(v.libroIsbn ?? '').toLowerCase().includes(isbn)) return false;
      if (tipo != null) {
        const dias = Number(v.diasAtraso);
        if (Number.isNaN(dias) || dias < 1 || dias > tipo) return false;
      }
      if (!this.enRangoFecha(v.fechaDevolucionEstimada, this.vencidosDia, this.vencidosDesde, this.vencidosHasta)) {
        return false;
      }
      return true;
    });
  }

  private cargarCategorias(): void {
    this.cargando = true;
    this.errorMsg = '';
    this.reporteService.categoriasDemandadas(undefined, undefined, this.limiteCategorias).subscribe({
      next: (categorias) => {
        this.categoriasTodas = categorias;
        this.categorias = this.filtrarCategorias(categorias);
        this.cargando = false;
      },
      error: (err) => this.fallar(err)
    });
  }

  private filtrarCategorias(lista: ReporteCategoriasDemandadas[]): ReporteCategoriasDemandadas[] {
    const q = this.categoriasBusqueda.trim().toLowerCase();
    if (!q) return lista;
    return lista.filter(c => (c.categoriaNombre ?? '').toLowerCase().includes(q));
  }

  private fallar(err: unknown): void {
    this.cargando = false;
    this.errorMsg = (err as { error?: { detail?: string } })?.error?.detail
      || 'Error al cargar los reportes';
  }

  normalizarTipoDias(valor: number | null): number | null {
    if (valor == null || valor === 0) return null;
    const n = Math.trunc(Number(valor));
    if (Number.isNaN(n) || n < 1 || n > 7) return null;
    return n;
  }

  private rangoIso(desde: string, hasta: string, dia: string, tipoDias: number | null): { desde?: string; hasta?: string } {
    if (dia) {
      return { desde: `${dia}T00:00:00.000Z`, hasta: `${dia}T23:59:59.999Z` };
    }
    const n = this.normalizarTipoDias(tipoDias);
    if (n) {
      const fin = new Date();
      const ini = new Date();
      ini.setUTCDate(fin.getUTCDate() - (n - 1));
      return {
        desde: `${ini.toISOString().slice(0, 10)}T00:00:00.000Z`,
        hasta: `${fin.toISOString().slice(0, 10)}T23:59:59.999Z`
      };
    }
    return {
      desde: desde ? `${desde}T00:00:00.000Z` : undefined,
      hasta: hasta ? `${hasta}T23:59:59.999Z` : undefined
    };
  }

  private enRangoFecha(iso: string, dia: string, desde: string, hasta: string): boolean {
    if (dia) {
      if (!iso) return false;
      const t = new Date(iso).getTime();
      return t >= new Date(`${dia}T00:00:00.000Z`).getTime()
        && t <= new Date(`${dia}T23:59:59.999Z`).getTime();
    }
    if (desde && iso && new Date(iso).getTime() < new Date(`${desde}T00:00:00.000Z`).getTime()) return false;
    if (hasta && iso && new Date(iso).getTime() > new Date(`${hasta}T23:59:59.999Z`).getTime()) return false;
    return true;
  }

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
    const rango = this.rangoIso(this.librosDesde, this.librosHasta, this.librosDia, this.librosTipoDias);
    this.descargarPdf(
      this.reporteService.librosMasPrestadosPdf(rango.desde, rango.hasta, this.limiteTop),
      'reporte-libros-prestados.pdf', 'libros-pdf'
    );
  }

  descargarMorosidadPdf(): void {
    this.descargarPdf(this.reporteService.morosidadPdf(), 'reporte-morosidad.pdf', 'morosidad-pdf');
  }

  descargarInventarioPdf(): void {
    this.descargarPdf(
      this.reporteService.inventarioPdf(this.estadoStock || undefined, this.busquedaInventario.trim() || undefined),
      'reporte-inventario.pdf', 'inventario-pdf'
    );
  }

  descargarVencidosPdf(): void {
    const busqueda = this.vencidosCorreo.trim() || this.vencidosLibro.trim() || this.vencidosIsbn.trim() || undefined;
    this.descargarPdf(
      this.reporteService.vencidosPdf(undefined, busqueda),
      'reporte-vencidos.pdf', 'vencidos-pdf'
    );
  }

  descargarCategoriasPdf(): void {
    this.descargarPdf(
      this.reporteService.categoriasDemandadasPdf(undefined, undefined, this.limiteCategorias),
      'reporte-categorias.pdf', 'categorias-pdf'
    );
  }

  private async generarExcel(headers: string[], rows: (string | number)[][], nombre: string, sheetName: string): Promise<void> {
    const XLSX = await import('xlsx');
    const wb = XLSX.utils.book_new();
    const wsData = [headers, ...rows];
    const ws = XLSX.utils.aoa_to_sheet(wsData);
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
