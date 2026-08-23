import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { LibroPublicoService } from '../core/services/libro-publico.service';
import { Libro, LibroSugerencia } from '../core/models/libro.model';
import { Categoria } from '../core/models/categoria.model';

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
  // Arranca en true (no false): ngOnInit siempre llama cargarPagina() de
  // forma síncrona, así que con el valor inicial en false el primer
  // render de Angular alcanza a pintar la rama @empty ("No hay libros
  // para mostrar") antes de que cargarPagina() ponga cargando=true --
  // un salto real de "vacío" a "grid esqueleto" que causaba el layout
  // shift dominante incluso con el esqueleto ya agregado (detectado
  // verificando la corrida local: CLS empeoró a 0.466 con el esqueleto
  // solo, en vez de bajar). Arrancar en true pinta el esqueleto desde
  // el primer frame, sin el salto intermedio.
  cargando: boolean = true;
  errorMsg: string = '';

  // Tarjetas esqueleto mientras carga la primera página (misma cantidad
  // que pageSize): reservan el alto real del grid para evitar el layout
  // shift medido en producción (CLS ~0.23, ver
  // docs/mediciones/lighthouse/REPORT.md) cuando "Cargando catálogo…"
  // (una sola línea) era reemplazado de golpe por el grid completo.
  readonly skeletonSlots: number[] = Array.from({ length: this.pageSize }, (_, i) => i);

  textoBusqueda: string = '';
  sugerencias: LibroSugerencia[] = [];
  buscandoSugerencias: boolean = false;
  private busqueda$ = new Subject<string>();

  categorias: Categoria[] = [];
  categoriaSeleccionada: number | null = null;

  // El icono menu_book del nav se rellena con font-variation-settings
  // 'FILL' 1; se devuelve como propiedad para no meter comillas internas
  // en el binding de estilo del template (igual que en catalogo.component).
  navIconoFilled: string = '"FILL" 1';

  constructor(private libroPublicoService: LibroPublicoService) {}

  ngOnInit(): void {
    this.cargarPagina();
    this.cargarCategorias();
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

  buscarPorNombre(): void {
    this.currentPage = 0;
    this.sugerencias = [];
    this.cargarPagina();
  }

  onCategoriaChange(categoriaId: number | null): void {
    this.categoriaSeleccionada = categoriaId;
    this.currentPage = 0;
    this.cargarPagina();
  }

  private cargarCategorias(): void {
    this.libroPublicoService.categorias().subscribe({
      next: (cats) => this.categorias = cats,
      error: () => this.categorias = []
    });
  }

  cargarPagina(): void {
    this.cargando = true;
    this.libroPublicoService.listar({
      page: this.currentPage,
      size: this.pageSize,
      sort: 'titulo,asc',
      q: this.textoBusqueda.trim() || undefined,
      categoriaId: this.categoriaSeleccionada ?? undefined
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