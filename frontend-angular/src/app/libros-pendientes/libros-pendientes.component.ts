import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';
import { LibroService } from '../core/services/libro.service';
import { EstadoLibroService } from '../core/services/estado-libro.service';
import { Libro } from '../core/models/libro.model';
import { EstadoLibro } from '../core/models/estado-libro.model';

@Component({
  standalone: true,
  selector: 'app-libros-pendientes',
  imports: [CommonModule, FormsModule],
  templateUrl: './libros-pendientes.component.html'
})
export class LibrosPendientesComponent implements OnInit, OnDestroy {
  libros: Libro[] = [];
  totalPages = 0;
  currentPage = 0;
  pageSize = 10;
  cargando = false;
  errorMsg = '';

  ordenColumna: string = '';
  direccionAsc: boolean = true;

  q: string = '';
  anioFiltro: string = '';
  estadoFiltro: number | null = null;
  estados: EstadoLibro[] = [];

  private readonly estadosGestion = ['DADO_DE_BAJA', 'PENDIENTE', 'EN_REPARACION', 'PERDIDO'];

  private filtro$ = new Subject<string>();
  private destroy$ = new Subject<void>();

  get paginasVisibles(): number[] {
    const windowSize = 4;
    let start = Math.max(0, this.currentPage - 1);
    let end = Math.min(this.totalPages, start + windowSize);
    if (end - start < windowSize) {
      start = Math.max(0, end - windowSize);
    }
    return Array.from({ length: end - start }, (_, i) => start + i);
  }

  get puedeAnterior(): boolean {
    return this.currentPage > 0;
  }

  get puedeSiguiente(): boolean {
    return this.currentPage < this.totalPages - 1;
  }

  constructor(
    private libroService: LibroService,
    private estadoService: EstadoLibroService,
    private router: Router
  ) {}

  ordenarPor(columna: string): void {
    if (this.ordenColumna === columna) {
      this.direccionAsc = !this.direccionAsc;
    } else {
      this.ordenColumna = columna;
      this.direccionAsc = true;
    }
  }

  get datosOrdenados() {
    const col = this.ordenColumna;
    const asc = this.direccionAsc;
    if (!col) return this.libros;
    return [...this.libros].sort((a: any, b: any) => {
      const va = a[col] ?? '';
      const vb = b[col] ?? '';
      const cmp = typeof va === 'number' ? va - vb : String(va).localeCompare(String(vb), 'es');
      return asc ? cmp : -cmp;
    });
  }

  ngOnInit(): void {
    this.estadoService.listar().subscribe({
      next: (e) => this.estados = e.filter(est => this.estadosGestion.includes(est.nombre)),
      error: () => {}
    });
    this.cargar();
    this.filtro$.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.currentPage = 0;
      this.cargar();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onFiltroChange(): void {
    const snapshot = JSON.stringify({ q: this.q.trim(), anio: this.anioFiltro, estado: this.estadoFiltro });
    this.filtro$.next(snapshot);
  }

  cargar(): void {
    this.cargando = true;
    this.errorMsg = '';
    const anioNum = this.anioFiltro ? Number(this.anioFiltro) : undefined;
    this.libroService.listarPendientes({
      q: this.q.trim() || undefined,
      anioPublicacion: anioNum,
      estadoIds: this.estadoFiltro ? [this.estadoFiltro] : undefined,
      page: this.currentPage,
      size: this.pageSize
    }).subscribe({
      next: (data) => {
        this.libros = data.content;
        this.totalPages = data.totalPages;
        this.cargando = false;
      },
      error: (err) => {
        const detail = err?.error?.detail ?? err?.error?.title ?? '';
        this.errorMsg = detail || 'Error al cargar libros pendientes';
        this.cargando = false;
      }
    });
  }

  formatarEstado(n: string): string {
    return n ? n.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase()) : '';
  }

  abrirRevision(libro: Libro): void {
    const current = this.router.url;
    let base = '/dashboard-bibliotecario/libros';
    if (current.includes('dashboard-admin')) base = '/dashboard-admin/libros';
    else if (current.includes('/libros') && !current.includes('dashboard')) base = '/libros';
    this.router.navigate([base], { queryParams: { revision: libro.id } });
  }

  cambiarTamano(nuevo: number): void {
    this.pageSize = Number(nuevo);
    this.currentPage = 0;
    this.cargar();
  }

  paginaAnterior(): void {
    if (this.currentPage > 0) { this.currentPage--; this.cargar(); }
  }
  paginaSiguiente(): void {
    if (this.currentPage < this.totalPages - 1) { this.currentPage++; this.cargar(); }
  }
  irAPagina(p: number): void {
    this.currentPage = p; this.cargar();
  }
}