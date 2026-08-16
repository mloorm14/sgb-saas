import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { LibroService } from '../core/services/libro.service';
import { CategoriaService } from '../core/services/categoria.service';
import { AutorService } from '../core/services/autor.service';
import { FavoritoService } from '../core/services/favorito.service';
import { Libro, LibroSugerencia } from '../core/models/libro.model';
import { Categoria } from '../core/models/categoria.model';
import { Autor } from '../core/models/autor.model';
import { PortadaLibroComponent } from '../shared/portada-libro/portada-libro.component';

// Catalogo del consumidor (Rama B del roadmap). El grid usa
// LibroService.listar (Page<Libro>, sort por titulo), los filtros salen de
// CategoriaService/AutorService y el buscador predictivo de
// LibroService.sugerencias (el backend exige minimo 2 caracteres).
// El estado de favoritos se resuelve con FavoritoService.listar al montar
// (FavoritoResponseDTO no trae stock ni portada: para saber si una tarjeta
// esta marcada hay que conocer el set completo).
// TODO(frontend/estudiante-chatbot): la Rama D montara el widget de chatbot
// flotante sobre este componente raiz del catalogo.
@Component({
  selector: 'app-catalogo',
  imports: [CommonModule, FormsModule, RouterLink, PortadaLibroComponent],
  templateUrl: './catalogo.component.html'
})
export class CatalogoComponent implements OnInit, OnDestroy {
  libros: Libro[] = [];
  categorias: Categoria[] = [];
  autores: Autor[] = [];
  favoritosIds = new Set<number>();
  totalPages: number = 0;
  currentPage: number = 0;
  pageSize: number = 10;
  cargando: boolean = false;
  errorMsg: string = '';

  textoBusqueda: string = '';
  sugerencias: LibroSugerencia[] = [];
  buscandoSugerencias: boolean = false;
  private busqueda$ = new Subject<string>();

  categoriaId: number | null = null;
  autorId: number | null = null;

  constructor(
    private libroService: LibroService,
    private categoriaService: CategoriaService,
    private autorService: AutorService,
    private favoritoService: FavoritoService
  ) {}

  ngOnInit(): void {
    this.cargarCategorias();
    this.cargarAutores();
    this.cargarFavoritos();
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
    this.libroService.sugerencias(texto).subscribe({
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

  private cargarCategorias(): void {
    this.categoriaService.listar().subscribe({
      next: (c) => (this.categorias = c),
      error: () => {} // el grid no se bloquea si los filtros fallan
    });
  }

  private cargarAutores(): void {
    this.autorService.listar().subscribe({
      next: (a) => (this.autores = a),
      error: () => {}
    });
  }

  private cargarFavoritos(): void {
    this.favoritoService.listar().subscribe({
      next: (favoritos) => {
        this.favoritosIds = new Set(favoritos.map(f => f.libroId));
      },
      error: () => {} // sin el set, el boton solo permite agregar
    });
  }

  esFavorito(libroId: number): boolean {
    return this.favoritosIds.has(libroId);
  }

  // El icono Material star se rellena con font-variation-settings 'FILL' 1.
  // Se devuelve desde el componente para no meter comillas internas en el
  // binding de estilo del template (el parser HTML no las acepta).
  estiloIconoFavorito(libroId: number): string {
    return this.esFavorito(libroId) ? '"FILL" 1' : '"FILL" 0';
  }

  alternarFavorito(event: Event, libro: Libro): void {
    event.preventDefault();
    event.stopPropagation();
    if (this.esFavorito(libro.id)) {
      this.favoritoService.quitar(libro.id).subscribe({
        next: () => this.favoritosIds.delete(libro.id),
        error: () => (this.errorMsg = 'Error al quitar de favoritos')
      });
    } else {
      this.favoritoService.agregar(libro.id).subscribe({
        next: () => this.favoritosIds.add(libro.id),
        error: () => (this.errorMsg = 'Error al agregar a favoritos')
      });
    }
  }

  onCategoriaChange(event: Event): void {
    const valor = (event.target as HTMLSelectElement).value;
    this.categoriaId = valor ? Number(valor) : null;
    this.currentPage = 0;
    this.cargarPagina();
  }

  onAutorChange(event: Event): void {
    const valor = (event.target as HTMLSelectElement).value;
    this.autorId = valor ? Number(valor) : null;
    this.currentPage = 0;
    this.cargarPagina();
  }

  autoresTexto(libro: Libro): string {
    return libro.autores && libro.autores.length ? libro.autores.join(', ') : '—';
  }

  cargarPagina(): void {
    this.cargando = true;
    this.libroService.listar({
      page: this.currentPage,
      size: this.pageSize,
      sort: 'titulo,asc',
      categoriaId: this.categoriaId ?? undefined,
      autorId: this.autorId ?? undefined
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

  // Paginacion numerada como el mockup 04: rango de 5 paginas centrado en
  // la actual (con menos de 5 paginas totales se muestran todas).
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