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

  q: string = '';
  anioFiltro: string = '';
  estadoFiltro: number | null = null;
  estados: EstadoLibro[] = [];

  private filtro$ = new Subject<void>();
  private destroy$ = new Subject<void>();

  get paginasVisibles(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i);
  }

  constructor(
    private libroService: LibroService,
    private estadoService: EstadoLibroService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.estadoService.listar().subscribe({
      next: (e) => this.estados = e,
      error: () => {}
    });
    this.cargar();
    this.filtro$.pipe(
      debounceTime(2000),
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
    this.filtro$.next();
  }

  cargar(): void {
    this.cargando = true;
    this.errorMsg = '';
    const anioNum = this.anioFiltro ? Number(this.anioFiltro) : undefined;
    this.libroService.listarPendientes({
      q: this.q.trim() || undefined,
      anioPublicacion: anioNum,
      page: this.currentPage,
      size: this.pageSize
    }).subscribe({
      next: (data) => {
        let content = data.content;
        if (this.estadoFiltro !== null) {
          content = content.filter(l => l.estadoId === this.estadoFiltro);
        }
        this.libros = content;
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
    // Navega al formulario de libros con query param revision
    // Detectar si estamos en dashboard-bibliotecario o admin
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
