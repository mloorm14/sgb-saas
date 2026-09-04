import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { ReporteService, LibroMasPrestadoDetallado, ReporteMorosidad, ReporteInventario, ReporteVencidos, ReporteCategoriasDemandadas, ReporteUsoPorPeriodo, ResumenFinancieroMultas } from '../core/services/reporte-gerencial.service';

export type VistaReporte = 'tarjetas' | 'libros' | 'morosidad' | 'inventario' | 'vencidos' | 'categorias' | 'uso' | 'financiero';

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
    },
    {
      id: 'uso',
      codigo: 'RPT-USO',
      etiqueta: 'Uso por período',
      descripcion: 'Préstamos y devoluciones por período',
      icono: 'query_stats',
      color: 'bg-primary/10 text-primary'
    },
    {
      id: 'financiero',
      codigo: 'RPT-FIN',
      etiqueta: 'Resumen financiero',
      descripcion: 'Recaudación, pendiente y pagos recientes',
      icono: 'account_balance',
      color: 'bg-tertiary/10 text-tertiary'
    }
  ];

  libros: LibroMasPrestadoDetallado[] = [];
  morosos: ReporteMorosidad[] = [];
  inventario: ReporteInventario[] = [];
  vencidos: ReporteVencidos[] = [];
  categorias: ReporteCategoriasDemandadas[] = [];
  usoPeriodo: ReporteUsoPorPeriodo[] = [];
  resumenFinanciero: ResumenFinancieroMultas | null = null;

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

  // Inventario
  busquedaInventario = '';
  estadoStock = '';
  inventarioPage = 0;
  inventarioPageSize = 10;

  // Paginación libros/morosidad/vencidos/categorias/uso/financiero
  librosPage = 0; librosPageSize = 10;
  morosidadPage = 0; morosidadPageSize = 10;
  vencidosPage = 0; vencidosPageSize = 10;
  categoriasPage = 0; categoriasPageSize = 10;
  usoPage = 0; usoPageSize = 10;
  financieroPage = 0; financieroPageSize = 10;

  // Vencidos
  vencidosCorreo = '';
  vencidosLibro = '';
  vencidosIsbn = '';

  // Categorías
  categoriasBusqueda = '';
  limiteCategorias = 10;

  // Uso por período
  usoGranularidad: 'dia' | 'semana' | 'mes' = 'mes';
  usoDesde = '';
  usoHasta = '';

  // Resumen financiero
  finDesde = '';
  finHasta = '';

  cargando = false;
  errorMsg = '';
  descargandoPdf: string | null = null;

  ordenColumnaMor: string = '';
  direccionAscMor: boolean = true;
  ordenColumnaVen: string = '';
  direccionAscVen: boolean = true;
  ordenColumnaCat: string = '';
  direccionAscCat: boolean = true;
  ordenColumnaUso: string = '';
  direccionAscUso: boolean = true;
  ordenColumnaInv: string = '';
  direccionAscInv: boolean = true;

  constructor(private reporteService: ReporteService) {}

  ordenarPorMor(columna: string): void {
    if (this.ordenColumnaMor === columna) {
      this.direccionAscMor = !this.direccionAscMor;
    } else {
      this.ordenColumnaMor = columna;
      this.direccionAscMor = true;
    }
  }

  get morososOrdenados() {
    const col = this.ordenColumnaMor;
    const asc = this.direccionAscMor;
    if (!col) return this.morosos;
    return [...this.morosos].sort((a: any, b: any) => {
      const va = a[col] ?? '';
      const vb = b[col] ?? '';
      const cmp = typeof va === 'number' ? va - vb : String(va).localeCompare(String(vb), 'es');
      return asc ? cmp : -cmp;
    });
  }

  ordenarPorVen(columna: string): void {
    if (this.ordenColumnaVen === columna) {
      this.direccionAscVen = !this.direccionAscVen;
    } else {
      this.ordenColumnaVen = columna;
      this.direccionAscVen = true;
    }
  }

  get vencidosOrdenados() {
    const col = this.ordenColumnaVen;
    const asc = this.direccionAscVen;
    if (!col) return this.vencidos;
    return [...this.vencidos].sort((a: any, b: any) => {
      const va = a[col] ?? '';
      const vb = b[col] ?? '';
      const cmp = typeof va === 'number' ? va - vb : String(va).localeCompare(String(vb), 'es');
      return asc ? cmp : -cmp;
    });
  }

  ordenarPorCat(columna: string): void {
    if (this.ordenColumnaCat === columna) {
      this.direccionAscCat = !this.direccionAscCat;
    } else {
      this.ordenColumnaCat = columna;
      this.direccionAscCat = true;
    }
  }

  get categoriasOrdenadas() {
    const col = this.ordenColumnaCat;
    const asc = this.direccionAscCat;
    if (!col) return this.categorias;
    return [...this.categorias].sort((a: any, b: any) => {
      const va = a[col] ?? '';
      const vb = b[col] ?? '';
      const cmp = typeof va === 'number' ? va - vb : String(va).localeCompare(String(vb), 'es');
      return asc ? cmp : -cmp;
    });
  }

  ordenarPorUso(columna: string): void {
    if (this.ordenColumnaUso === columna) {
      this.direccionAscUso = !this.direccionAscUso;
    } else {
      this.ordenColumnaUso = columna;
      this.direccionAscUso = true;
    }
  }

  get usoOrdenado() {
    const col = this.ordenColumnaUso;
    const asc = this.direccionAscUso;
    if (!col) return this.usoPeriodo;
    return [...this.usoPeriodo].sort((a: any, b: any) => {
      const va = a[col] ?? '';
      const vb = b[col] ?? '';
      const cmp = typeof va === 'number' ? va - vb : String(va).localeCompare(String(vb), 'es');
      return asc ? cmp : -cmp;
    });
  }

  ordenarPorInv(columna: string): void {
    if (this.ordenColumnaInv === columna) {
      this.direccionAscInv = !this.direccionAscInv;
    } else {
      this.ordenColumnaInv = columna;
      this.direccionAscInv = true;
    }
  }

  get inventarioPaginadoOrdenado() {
    const col = this.ordenColumnaInv;
    const asc = this.direccionAscInv;
    if (!col) return this.inventarioPaginado;
    return [...this.inventarioPaginado].sort((a: any, b: any) => {
      const va = a[col] ?? '';
      const vb = b[col] ?? '';
      const cmp = typeof va === 'number' ? va - vb : String(va).localeCompare(String(vb), 'es');
      return asc ? cmp : -cmp;
    });
  }

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
        this.librosPage = 0;
        break;
      case 'morosidad':
        this.morosidadCorreo = '';
        this.morosidadPage = 0;
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
        this.vencidosPage = 0;
        break;
      case 'categorias':
        this.categoriasBusqueda = '';
        this.limiteCategorias = 10;
        this.categoriasPage = 0;
        break;
      case 'uso':
        this.usoGranularidad = 'mes';
        this.usoDesde = '';
        this.usoHasta = '';
        this.usoPage = 0;
        break;
      case 'financiero':
        this.finDesde = '';
        this.finHasta = '';
        this.financieroPage = 0;
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
      case 'uso': this.cargarUso(); break;
      case 'financiero': this.cargarFinanciero(); break;
    }
  }

  private cargarLibros(): void {
    this.cargando = true;
    this.errorMsg = '';
    const rango = this.rangoIso(this.librosDesde, this.librosHasta, this.librosDia, this.librosTipoDias);
    this.reporteService.librosMasPrestadosDetallado(rango.desde, rango.hasta, this.limiteTop).subscribe({
      next: (libros) => {
        this.libros = libros;
        this.librosPage = 0;
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
        this.morosidadPage = 0;
        this.cargando = false;
      },
      error: (err) => this.fallar(err)
    });
  }

  private filtrarMorosidad(lista: ReporteMorosidad[]): ReporteMorosidad[] {
    const correo = this.morosidadCorreo.trim().toLowerCase();
    if (!correo) return lista;
    return lista.filter(m => (m.correo ?? '').toLowerCase().includes(correo));
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

  // ── Paginación genérica para resto de reportes ──
  private paginar<T>(arr: T[], page: number, size: number): T[] {
    const s = page * size;
    return arr.slice(s, s + size);
  }
  private totalPagesFor(arr: unknown[], size: number): number {
    return Math.max(1, Math.ceil(arr.length / size));
  }
  private paginasVisiblesFor(total: number, current: number): number[] {
    const w = 4; let s = Math.max(0, current - 1); let e = Math.min(total, s + w);
    if (e - s < w) s = Math.max(0, e - w);
    return Array.from({ length: e - s }, (_, i) => s + i);
  }

  // Libros
  get librosTotalPages(): number { return this.totalPagesFor(this.libros, this.librosPageSize); }
  get librosPaginado(): LibroMasPrestadoDetallado[] { return this.paginar(this.libros, this.librosPage, this.librosPageSize); }
  get librosPaginasVisibles(): number[] { return this.paginasVisiblesFor(this.librosTotalPages, this.librosPage); }
  get puedeLibrosAnterior(): boolean { return this.librosPage > 0; }
  get puedeLibrosSiguiente(): boolean { return this.librosPage < this.librosTotalPages - 1; }
  irALibrosPage(p: number): void { if (p < 0 || p >= this.librosTotalPages || p === this.librosPage) return; this.librosPage = p; }
  paginaAnteriorLibros(): void { if (this.puedeLibrosAnterior) this.librosPage--; }
  paginaSiguienteLibros(): void { if (this.puedeLibrosSiguiente) this.librosPage++; }
  cambiarTamanoLibros(n: number): void { this.librosPageSize = Number(n); this.librosPage = 0; }

  // Morosidad
  get morosidadTotalPages(): number { return this.totalPagesFor(this.morososOrdenados, this.morosidadPageSize); }
  get morosidadPaginado(): ReporteMorosidad[] { return this.paginar(this.morososOrdenados, this.morosidadPage, this.morosidadPageSize); }
  get morosidadPaginasVisibles(): number[] { return this.paginasVisiblesFor(this.morosidadTotalPages, this.morosidadPage); }
  get puedeMorosidadAnterior(): boolean { return this.morosidadPage > 0; }
  get puedeMorosidadSiguiente(): boolean { return this.morosidadPage < this.morosidadTotalPages - 1; }
  irAMorosidadPage(p: number): void { if (p < 0 || p >= this.morosidadTotalPages || p === this.morosidadPage) return; this.morosidadPage = p; }
  paginaAnteriorMorosidad(): void { if (this.puedeMorosidadAnterior) this.morosidadPage--; }
  paginaSiguienteMorosidad(): void { if (this.puedeMorosidadSiguiente) this.morosidadPage++; }
  cambiarTamanoMorosidad(n: number): void { this.morosidadPageSize = Number(n); this.morosidadPage = 0; }

  // Vencidos
  get vencidosTotalPages(): number { return this.totalPagesFor(this.vencidosOrdenados, this.vencidosPageSize); }
  get vencidosPaginado(): ReporteVencidos[] { return this.paginar(this.vencidosOrdenados, this.vencidosPage, this.vencidosPageSize); }
  get vencidosPaginasVisibles(): number[] { return this.paginasVisiblesFor(this.vencidosTotalPages, this.vencidosPage); }
  get puedeVencidosAnterior(): boolean { return this.vencidosPage > 0; }
  get puedeVencidosSiguiente(): boolean { return this.vencidosPage < this.vencidosTotalPages - 1; }
  irAVencidosPage(p: number): void { if (p < 0 || p >= this.vencidosTotalPages || p === this.vencidosPage) return; this.vencidosPage = p; }
  paginaAnteriorVencidos(): void { if (this.puedeVencidosAnterior) this.vencidosPage--; }
  paginaSiguienteVencidos(): void { if (this.puedeVencidosSiguiente) this.vencidosPage++; }
  cambiarTamanoVencidos(n: number): void { this.vencidosPageSize = Number(n); this.vencidosPage = 0; }

  // Categorias
  get categoriasTotalPages(): number { return this.totalPagesFor(this.categoriasOrdenadas, this.categoriasPageSize); }
  get categoriasPaginado(): ReporteCategoriasDemandadas[] { return this.paginar(this.categoriasOrdenadas, this.categoriasPage, this.categoriasPageSize); }
  get categoriasPaginasVisibles(): number[] { return this.paginasVisiblesFor(this.categoriasTotalPages, this.categoriasPage); }
  get puedeCategoriasAnterior(): boolean { return this.categoriasPage > 0; }
  get puedeCategoriasSiguiente(): boolean { return this.categoriasPage < this.categoriasTotalPages - 1; }
  irACategoriasPage(p: number): void { if (p < 0 || p >= this.categoriasTotalPages || p === this.categoriasPage) return; this.categoriasPage = p; }
  paginaAnteriorCategorias(): void { if (this.puedeCategoriasAnterior) this.categoriasPage--; }
  paginaSiguienteCategorias(): void { if (this.puedeCategoriasSiguiente) this.categoriasPage++; }
  cambiarTamanoCategorias(n: number): void { this.categoriasPageSize = Number(n); this.categoriasPage = 0; }

  // Uso
  get usoTotalPages(): number { return this.totalPagesFor(this.usoOrdenado, this.usoPageSize); }
  get usoPaginado(): ReporteUsoPorPeriodo[] { return this.paginar(this.usoOrdenado, this.usoPage, this.usoPageSize); }
  get usoPaginasVisibles(): number[] { return this.paginasVisiblesFor(this.usoTotalPages, this.usoPage); }
  get puedeUsoAnterior(): boolean { return this.usoPage > 0; }
  get puedeUsoSiguiente(): boolean { return this.usoPage < this.usoTotalPages - 1; }
  irAUsoPage(p: number): void { if (p < 0 || p >= this.usoTotalPages || p === this.usoPage) return; this.usoPage = p; }
  paginaAnteriorUso(): void { if (this.puedeUsoAnterior) this.usoPage--; }
  paginaSiguienteUso(): void { if (this.puedeUsoSiguiente) this.usoPage++; }
  cambiarTamanoUso(n: number): void { this.usoPageSize = Number(n); this.usoPage = 0; }

  // Financiero pagosRecientes
  get financieroPagos(): any[] { return this.resumenFinanciero?.pagosRecientes ?? []; }
  get financieroTotalPages(): number { return this.totalPagesFor(this.financieroPagos, this.financieroPageSize); }
  get financieroPaginado(): any[] { return this.paginar(this.financieroPagos, this.financieroPage, this.financieroPageSize); }
  get financieroPaginasVisibles(): number[] { return this.paginasVisiblesFor(this.financieroTotalPages, this.financieroPage); }
  get puedeFinancieroAnterior(): boolean { return this.financieroPage > 0; }
  get puedeFinancieroSiguiente(): boolean { return this.financieroPage < this.financieroTotalPages - 1; }
  irAFinancieroPage(p: number): void { if (p < 0 || p >= this.financieroTotalPages || p === this.financieroPage) return; this.financieroPage = p; }
  paginaAnteriorFinanciero(): void { if (this.puedeFinancieroAnterior) this.financieroPage--; }
  paginaSiguienteFinanciero(): void { if (this.puedeFinancieroSiguiente) this.financieroPage++; }
  cambiarTamanoFinanciero(n: number): void { this.financieroPageSize = Number(n); this.financieroPage = 0; }

  private cargarVencidos(): void {
    this.cargando = true;
    this.errorMsg = '';
    const busqueda = [this.vencidosCorreo.trim(), this.vencidosLibro.trim(), this.vencidosIsbn.trim()].filter(Boolean).join(' ') || undefined;
    this.reporteService.vencidos(undefined, busqueda).subscribe({
      next: (vencidos) => {
        this.vencidosTodos = vencidos;
        this.vencidos = this.filtrarVencidos(vencidos);
        this.vencidosPage = 0;
        this.cargando = false;
      },
      error: (err) => this.fallar(err)
    });
  }

  private filtrarVencidos(lista: ReporteVencidos[]): ReporteVencidos[] {
    const correo = this.vencidosCorreo.trim().toLowerCase();
    const libro = this.vencidosLibro.trim().toLowerCase();
    const isbn = this.vencidosIsbn.trim().toLowerCase();
    return lista.filter(v => {
      if (correo && !(v.usuarioCorreo ?? '').toLowerCase().includes(correo)) return false;
      if (libro && !(v.libroTitulo ?? '').toLowerCase().includes(libro)) return false;
      if (isbn && !(v.libroIsbn ?? '').toLowerCase().includes(isbn)) return false;
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
        this.categoriasPage = 0;
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

  private cargarUso(): void {
    this.cargando = true;
    this.errorMsg = '';
    this.reporteService.usoPorPeriodo(this.usoGranularidad, this.usoDesde || undefined, this.usoHasta || undefined).subscribe({
      next: (datos) => {
        this.usoPeriodo = datos;
        this.usoPage = 0;
        this.cargando = false;
      },
      error: (err) => this.fallar(err)
    });
  }

  private cargarFinanciero(): void {
    this.cargando = true;
    this.errorMsg = '';
    this.reporteService.resumenFinanciero(this.finDesde || undefined, this.finHasta || undefined).subscribe({
      next: (datos) => {
        this.resumenFinanciero = datos;
        this.financieroPage = 0;
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
    const busqueda = [this.vencidosCorreo.trim(), this.vencidosLibro.trim(), this.vencidosIsbn.trim()].filter(Boolean).join(' ') || undefined;
    this.descargarPdf(
      this.reporteService.vencidosPdf(undefined, busqueda),
      'reporte-vencidos.pdf', 'vencidos-pdf'
    );
  }

  descargarUsoPdf(): void {
    this.descargarPdf(
      this.reporteService.usoPorPeriodoPdf(this.usoGranularidad, this.usoDesde || undefined, this.usoHasta || undefined),
      'reporte-uso-periodo.pdf', 'uso-pdf'
    );
  }

  descargarFinancieroPdf(): void {
    this.descargarPdf(
      this.reporteService.resumenFinancieroPdf(this.finDesde || undefined, this.finHasta || undefined),
      'reporte-resumen-financiero.pdf', 'financiero-pdf'
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

  excelUso(): void {
    const headers = ['Período', 'Préstamos', 'Devoluciones'];
    const rows = this.usoPeriodo.map(u => [u.periodo, u.totalPrestamos, u.totalDevoluciones]);
    this.generarExcel(headers, rows, 'reporte-uso-periodo.xlsx', 'Uso por período');
  }

  excelFinanciero(): void {
    const headers = ['Multa', 'Monto', 'Fecha', 'Usuario', 'Libro'];
    const pagos = this.resumenFinanciero?.pagosRecientes ?? [];
    const rows = pagos.map(p => [p.multaId, p.montoPagado, p.fechaPagada, p.usuarioNombre || p.usuarioCorreo, p.libroTitulo]);
    this.generarExcel(headers, rows, 'reporte-financiero.xlsx', 'Resumen financiero');
  }

  fechaCorta(iso: string): string {
    if (!iso) return '—';
    const fecha = new Date(iso);
    return fecha.toLocaleDateString('es-ES', { day: '2-digit', month: 'short', year: 'numeric' });
  }
}
