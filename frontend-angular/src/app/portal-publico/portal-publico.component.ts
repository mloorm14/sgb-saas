import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { LibroPublicoService } from '../core/services/libro-publico.service';
import { Libro, LibroSugerencia } from '../core/models/libro.model';

// Portal público de catálogo (Rama C, mockup 12). Es la raíz de la app y NO
// pide sesión: navega sin authGuard y usa LibroPublicoService (/api/publico).
// Sin botones de favorito ni de reservar (requieren cuenta): el grid es el
// del mockup 04 sin esas acciones, con <img> directo a la portada pública.
// TODO(frontend/estudiante-chatbot): la Rama D montara el widget de chatbot
// flotante sobre este componente raiz.
@Component({
  selector: 'app-portal-publico',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './portal-publico.component.html'
})
export class PortalPublicoComponent implements OnInit, OnDestroy {
  libros: Libro[] = [];
  totalPages: number = 0;
  currentPage: number = 0;
  pageSize: number = 10;
  cargando: boolean = false;
  errorMsg: string = '';

  textoBusqueda: string = '';
  sugerencias: LibroSugerencia[] = [];
  buscandoSugerencias: boolean = false;
  private busqueda$ = new Subject<string>();

  // El icono menu_book del nav se rellena con font-variation-settings
  // 'FILL' 1; se devuelve como propiedad para no meter comillas internas
  // en el binding de estilo del template (igual que en catalogo.component).
  navIconoFilled: string = '"FILL" 1';

  constructor(private libroPublicoService: LibroPublicoService) {}

  ngOnInit(): void {
    this.cargarPagina();
    this.busqueda$.pipe(
      debounceTime(300),
      distinctUntilChanged()
    ).subscribe(texto => this.buscarSugerencias(texto));
  }

  ngOnDestroy(): void {
    this.busqueda$.complete();
  }

  onBusquedaChange(): void {
    this.busqueda$.next(this.textoBusqueda.trim());
  }

  private buscarSugerencias(texto: string): void {
    if (texto.length < 2) {
      this.sugerencias = [];
      this.buscandoSugerencias = false;
      return;
    }
    this.buscandoSugerencias = true;
    this.libroPublicoService.sugerencias(texto).subscribe({
      next: (s) => {
        this.sugerencias = s;
        this.buscandoSugerencias = false;
      },
      error: () => {
        this.sugerencias = [];
        this.buscandoSugerencias = false;
      }
    });
  }

  portadaUrl(libroId: number): string {
    return this.libroPublicoService.portadaUrl(libroId);
  }

  autoresTexto(libro: Libro): string {
    return libro.autores && libro.autores.length ? libro.autores.join(', ') : '—';
  }

  cargarPagina(): void {
    this.cargando = true;
    this.libroPublicoService.listar({
      page: this.currentPage,
      size: this.pageSize,
      sort: 'titulo,asc'
    }).subscribe({
      next: (data) => {
        this.libros = data.content;
        this.totalPages = data.totalPages;
        this.cargando = false;
      },
      error: () => {
        this.errorMsg = 'Error al cargar el catálogo';
        this.cargando = false;
      }
    });
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

  paginaAnterior(): void {
    this.irAPagina(this.currentPage - 1);
  }

  paginaSiguiente(): void {
    this.irAPagina(this.currentPage + 1);
  }
}