import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { LibroService } from '../core/services/libro.service';
import { CategoriaService } from '../core/services/categoria.service';
import { AutorService } from '../core/services/autor.service';
import { FavoritoService } from '../core/services/favorito.service';
import { ReservacionService } from '../core/services/reservacion.service';
import { ReservacionPendienteService } from '../core/services/reservacion-pendiente.service';
import { AuthService } from '../core/services/auth.service';
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
// Reservar (mockup 16): el boton "Reservar"/"Reservar en cola" llama a
// ReservacionService.crear con el id del token (AuthService.getUserId) y
// el estado "Ya reservado" sale de ReservacionPendienteService (un solo
// listarPorUsuario compartido con el detalle). El backend NO valida stock
// en ReservacionService.crear (verificado en backend-springboot): reservar
// con stock 0 queda en cola, por eso el boton sigue habilitado.
// TODO(frontend/estudiante-chatbot): la Rama D montara el widget de chatbot
// flotante sobre este componente raiz del catalogo.
@Component({
  standalone: true,
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
  toastMsg: string | null = null;
  private toastTimer: ReturnType<typeof setTimeout> | undefined;

  textoBusqueda: string = '';
  sugerencias: LibroSugerencia[] = [];
  buscandoSugerencias: boolean = false;
  private busqueda$ = new Subject<string>();

  categoriaId: number | null = null;
  autorId: number | null = null;

  mostrarModalReserva = false;
  libroParaReservar: Libro | null = null;
  fechaRetiro: string = '';
  minFechaRetiro: string = '';

  constructor(
    private libroService: LibroService,
    private categoriaService: CategoriaService,
    private autorService: AutorService,
    private favoritoService: FavoritoService,
    private reservacionService: ReservacionService,
    private reservacionesPendientes: ReservacionPendienteService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    const hoy = new Date();
    this.minFechaRetiro = hoy.toISOString().split('T')[0];
    this.fechaRetiro = this.minFechaRetiro;

    this.cargarCategorias();
    this.cargarAutores();
    this.cargarFavoritos();
    this.cargarReservacionesPendientes();
    this.cargarPagina();

    this.busqueda$.pipe(
      debounceTime(300),
      distinctUntilChanged()
    ).subscribe(texto => this.buscarSugerencias(texto));
  }

  ngOnDestroy(): void {
    this.busqueda$.complete();
    if (this.toastTimer) clearTimeout(this.toastTimer);
  }

  private cargarReservacionesPendientes(): void {
    this.reservacionesPendientes.cargar().subscribe({
      error: () => {} // sin el set, el boton solo permite reservar
    });
  }

  estaReservado(libroId: number): boolean {
    return this.reservacionesPendientes.esPendiente(libroId);
  }

  // Igual patron que estiloIconoFavorito: las clases del boton segun stock
  // (relleno si hay stock, outline si queda en cola) sin condicionales
  // complejos en el template. mt-auto ancla el boton abajo de la tarjeta
  // (tarjeta flex-col) para que quede al mismo alto en todo el grid.
  clasesBotonReservar(libro: Libro): string {
    return libro.stockDisponible > 0
      ? 'w-full mt-auto h-8 rounded bg-primary text-on-primary font-label-sm text-[12px] flex items-center justify-center gap-1 hover:bg-on-primary-fixed-variant cursor-pointer'
      : 'w-full mt-auto h-8 rounded border border-primary text-primary font-label-sm text-[12px] flex items-center justify-center gap-1 hover:bg-primary/5 cursor-pointer';
  }

  reservarLibro(event: Event, libro: Libro): void {
    event.preventDefault();
    event.stopPropagation();
    const usuarioId = this.authService.getUserId();
    if (usuarioId === null) {
      this.errorMsg = 'Inicia sesión para reservar';
      return;
    }
    this.libroParaReservar = libro;
    this.fechaRetiro = this.minFechaRetiro;
    this.mostrarModalReserva = true;
  }

  cancelarReserva(): void {
    this.mostrarModalReserva = false;
    this.libroParaReservar = null;
  }

  confirmarReserva(): void {
    if (!this.libroParaReservar) return;
    const libro = this.libroParaReservar;
    this.mostrarModalReserva = false;
    const usuarioId = this.authService.getUserId();
    const fechaRetiroISO = this.fechaRetiro ? this.fechaRetiro + 'T00:00:00' : undefined;
    this.reservacionService.crear({ usuarioId: usuarioId!, libroId: libro.id, fechaRetiro: fechaRetiroISO }).subscribe({
      next: (r) => {
        this.reservacionesPendientes.marcarReservada(libro.id);
        this.libroParaReservar = null;
        this.mostrarToast(
          `Reservado. Tienes hasta el ${this.formatearFecha(r.fechaLimiteRetiro)} para retirarlo.`
        );
      },
      error: () => {
        this.libroParaReservar = null;
        this.errorMsg = 'Error al reservar el libro';
      }
    });
  }

  private mostrarToast(mensaje: string): void {
    this.toastMsg = mensaje;
    if (this.toastTimer) clearTimeout(this.toastTimer);
    this.toastTimer = setTimeout(() => (this.toastMsg = null), 3000);
  }

  private formatearFecha(iso: string): string {
    return new Date(iso).toLocaleDateString('es-ES', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric'
    });
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

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.mostrarModalReserva) {
      this.cancelarReserva();
    }
  }
}