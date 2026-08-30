import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { LibroService } from '../core/services/libro.service';
import { CategoriaService } from '../core/services/categoria.service';
import { FavoritoService } from '../core/services/favorito.service';
import { ReservacionService } from '../core/services/reservacion.service';
import { ReservacionPendienteService } from '../core/services/reservacion-pendiente.service';
import { AuthService } from '../core/services/auth.service';
import { Libro, LibroSugerencia } from '../core/models/libro.model';
import { Categoria } from '../core/models/categoria.model';
import { PortadaLibroComponent } from '../shared/portada-libro/portada-libro.component';
import { toOffsetDateTime } from '../core/utils/fecha';
import { SuscripcionDisponibilidadService } from '../core/services/suscripcion-disponibilidad.service';
import { ToastService } from '../shared/toast/toast.service';

@Component({
  standalone: true,
  selector: 'app-catalogo',
  imports: [CommonModule, FormsModule, RouterLink, PortadaLibroComponent],
  templateUrl: './catalogo.component.html'
})
export class CatalogoComponent implements OnInit, OnDestroy {
  libros: Libro[] = [];
  categorias: Categoria[] = [];
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
  textoCategoria: string = '';
  categoriaSugerencias: Categoria[] = [];
  mostrarSugerenciasCategoria: boolean = false;
  private categoriaBusqueda$ = new Subject<string>();

  estadoStock: '' | 'disponibles' | 'agotados' = '';

  mostrarModalReserva = false;
  libroParaReservar: Libro | null = null;
  fechaRetiro: string = '';
  minFechaRetiro: string = '';
  maxFechaRetiro: string = '';

  constructor(
    private libroService: LibroService,
    private categoriaService: CategoriaService,
    private favoritoService: FavoritoService,
    private reservacionService: ReservacionService,
    private reservacionesPendientes: ReservacionPendienteService,
    private authService: AuthService,
    private suscripcionService: SuscripcionDisponibilidadService,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    const hoy = new Date();
    this.minFechaRetiro = hoy.toISOString().split('T')[0];
    const max = new Date(hoy);
    max.setDate(max.getDate() + 14);
    this.maxFechaRetiro = max.toISOString().split('T')[0];
    this.fechaRetiro = this.minFechaRetiro;

    this.cargarCategorias();
    this.cargarFavoritos();
    this.cargarReservacionesPendientes();
    this.cargarPagina();

    this.busqueda$.pipe(
      debounceTime(300),
      distinctUntilChanged()
    ).subscribe(texto => {
      this.buscarSugerencias(texto);
      this.currentPage = 0;
      this.cargarPagina();
    });

    this.categoriaBusqueda$.pipe(
      debounceTime(300),
      distinctUntilChanged()
    ).subscribe(texto => this.buscarCategorias(texto));
  }

  ngOnDestroy(): void {
    this.busqueda$.complete();
    this.categoriaBusqueda$.complete();
    if (this.toastTimer) clearTimeout(this.toastTimer);
  }

  private cargarReservacionesPendientes(): void {
    this.reservacionesPendientes.cargar().subscribe({
      error: () => {}
    });
  }

  estaReservado(libroId: number): boolean {
    return this.reservacionesPendientes.esPendiente(libroId);
  }

  clasesBotonReservar(libro: Libro): string {
    return libro.stockDisponible > 0
      ? 'w-full mt-auto h-8 rounded bg-primary text-on-primary font-label-sm text-[12px] flex items-center justify-center gap-1 hover:bg-on-primary-fixed-variant cursor-pointer'
      : 'w-full mt-auto h-8 rounded border border-primary text-primary font-label-sm text-[12px] flex items-center justify-center gap-1 hover:bg-primary/5 cursor-pointer';
  }

  reservarLibro(event: Event, libro: Libro): void {
    event.preventDefault();
    event.stopPropagation();
    if (libro.stockDisponible <= 0) {
      this.suscribirDisponibilidad(libro);
      return;
    }
    const usuarioId = this.authService.getUserId();
    if (usuarioId === null) {
      this.errorMsg = 'Inicia sesion para reservar';
      return;
    }
    this.libroParaReservar = libro;
    this.fechaRetiro = this.minFechaRetiro;
    this.mostrarModalReserva = true;
  }

  private suscribirDisponibilidad(libro: Libro): void {
    const usuarioId = this.authService.getUserId();
    if (usuarioId === null) {
      this.errorMsg = 'Inicia sesion para usar Notificarme';
      this.toast.warning('Aviso', this.errorMsg);
      return;
    }
    this.suscripcionService.suscribir(libro.id).subscribe({
      next: () => {
        const msg = `Te avisaremos cuando "${libro.titulo}" este disponible — reservalo antes que otros.`;
        this.mostrarToast(msg);
        this.toast.success('Suscripcion', msg);
      },
      error: (err: any) => {
        const detail = (err?.error as { detail?: string })?.detail;
        this.errorMsg = detail ?? 'No se pudo suscribir. Intenta nuevamente.';
        this.toast.error('Error', this.errorMsg);
      }
    });
  }

  cancelarReserva(): void {
    this.mostrarModalReserva = false;
    this.libroParaReservar = null;
  }

  confirmarReserva(): void {
    if (!this.libroParaReservar) return;
    const hoyStr = new Date().toISOString().split('T')[0];
    if (this.fechaRetiro === hoyStr) {
      const ahora = new Date();
      if (ahora.getHours() >= 18) {
        if (!confirm('Ya paso la hora limite (18:00). ¿Quieres retirarlo mañana hasta las 18:00?')) return;
        const manana = new Date(ahora); manana.setDate(manana.getDate()+1);
        this.fechaRetiro = manana.toISOString().split('T')[0];
      }
    }
    const libro = this.libroParaReservar;
    this.mostrarModalReserva = false;
    const usuarioId = this.authService.getUserId();
    const fechaRetiroISO = this.fechaRetiro ? toOffsetDateTime(this.fechaRetiro) : undefined;
    this.reservacionService.crear({ usuarioId: usuarioId!, libroId: libro.id, fechaRetiro: fechaRetiroISO }).subscribe({
      next: (r) => {
        this.reservacionesPendientes.marcarReservada(libro.id);
        this.libroParaReservar = null;
        const msg = `Reserva creada. Puedes retirarlo hasta el ${this.formatearFecha(r.fechaLimiteRetiro)}.`;
        this.mostrarToast(msg);
        this.toast.success('Reserva', msg);
      },
      error: (err: any) => {
        this.libroParaReservar = null;
        const detail = (err?.error as { detail?: string })?.detail ?? '';
        if (detail.includes('máximo') || detail.includes('maximo')) {
          this.errorMsg = detail;
          this.toast.warning('Limite alcanzado', detail);
        } else if (detail.includes('multa') || detail.includes('deuda') || detail.includes('bloquead')) {
          this.errorMsg = detail;
          this.toast.warning('Aviso', detail);
        } else if (detail.includes('Sin stock') || detail.includes('stock')) {
          this.errorMsg = detail;
          this.toast.warning('Sin stock', detail);
        } else if (detail) {
          this.errorMsg = detail;
          this.toast.error('Error', detail);
        } else {
          this.errorMsg = 'No se pudo reservar el libro';
          this.toast.error('Error', this.errorMsg);
        }
      }
    });
  }

  private mostrarToast(mensaje: string): void {
    this.toastMsg = mensaje;
    if (this.toastTimer) clearTimeout(this.toastTimer);
    this.toastTimer = setTimeout(() => (this.toastMsg = null), 4000);
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
      error: () => {}
    });
  }

  onCategoriaInputChange(): void {
    const texto = this.textoCategoria.trim();
    if (texto.length === 0) {
      this.categoriaSugerencias = this.categorias.slice(0, 5);
      this.mostrarSugerenciasCategoria = this.categoriaSugerencias.length > 0;
      return;
    }
    this.categoriaBusqueda$.next(texto);
  }

  private buscarCategorias(texto: string): void {
    if (texto.length < 1) {
      this.categoriaSugerencias = this.categorias.slice(0, 5);
      this.mostrarSugerenciasCategoria = true;
      return;
    }
    this.categoriaService.buscar(texto).subscribe({
      next: (cats) => {
        this.categoriaSugerencias = cats;
        this.mostrarSugerenciasCategoria = cats.length > 0;
      },
      error: () => {
        this.categoriaSugerencias = [];
        this.mostrarSugerenciasCategoria = false;
      }
    });
  }

  seleccionarCategoria(cat: Categoria): void {
    this.categoriaId = cat.id;
    this.textoCategoria = cat.nombre;
    this.mostrarSugerenciasCategoria = false;
    this.currentPage = 0;
    this.cargarPagina();
  }

  limpiarFiltroCategoria(): void {
    this.categoriaId = null;
    this.textoCategoria = '';
    this.mostrarSugerenciasCategoria = false;
    this.currentPage = 0;
    this.cargarPagina();
  }

  onCategoriaBlur(): void {
    setTimeout(() => this.mostrarSugerenciasCategoria = false, 200);
  }

  onEstadoStockChange(event: Event): void {
    const v = (event.target as HTMLSelectElement).value as '' | 'disponibles' | 'agotados';
    this.estadoStock = v;
    this.currentPage = 0;
    this.cargarPagina();
  }

  private cargarFavoritos(): void {
    this.favoritoService.listar().subscribe({
      next: (favoritos) => {
        this.favoritosIds = new Set(favoritos.map(f => f.libroId));
      },
      error: () => {}
    });
  }

  esFavorito(libroId: number): boolean {
    return this.favoritosIds.has(libroId);
  }

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

  autoresTexto(libro: Libro): string {
    return libro.autores && libro.autores.length ? libro.autores.join(', ') : '—';
  }

  cargarPagina(): void {
    this.cargando = true;
    const disponible = this.estadoStock === 'disponibles' ? true : this.estadoStock === 'agotados' ? false : undefined;
    this.libroService.listar({
      page: this.currentPage,
      size: this.pageSize,
      sort: 'titulo,asc',
      q: this.textoBusqueda.trim() || undefined,
      categoriaId: this.categoriaId ?? undefined,
      disponible
    }).subscribe({
      next: (data) => {
        this.libros = data.content;
        this.totalPages = data.totalPages;
        this.cargando = false;
      },
      error: () => {
        this.errorMsg = 'Error al cargar el catalogo';
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

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.mostrarModalReserva) {
      this.cancelarReserva();
    }
    this.mostrarSugerenciasCategoria = false;
  }
}
