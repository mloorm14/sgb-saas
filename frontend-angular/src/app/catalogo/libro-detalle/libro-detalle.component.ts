import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { LibroService } from '../../core/services/libro.service';
import { FavoritoService } from '../../core/services/favorito.service';
import { ReservacionService } from '../../core/services/reservacion.service';
import { ReservacionPendienteService } from '../../core/services/reservacion-pendiente.service';
import { AuthService } from '../../core/services/auth.service';
import { Libro } from '../../core/models/libro.model';
import { Reservacion } from '../../core/models/reservacion.model';
import { PortadaLibroComponent } from '../../shared/portada-libro/portada-libro.component';

// Detalle de libro del consumidor (Rama B). El estado de favoritos se
// resuelve con FavoritoService.listar al montar, igual que en el catálogo.
// El link "Sugerir adquisición" va siempre visible (el mockup 05 corrigió
// que solo aparezca con stock 0) y prellena el título del formulario.
// Reservar (mockup 17): el estado "Ya reservado" sale del mismo
// ReservacionPendienteService que el catálogo (sin duplicar la lógica) y
// tras reservar se muestra el bloque de confirmación con la
// fechaLimiteRetiro que devuelve ReservacionService.crear (campo real de
// ReservacionResponseDTO).
@Component({
  standalone: true,
  selector: 'app-libro-detalle',
  imports: [CommonModule, FormsModule, RouterLink, PortadaLibroComponent],
  templateUrl: './libro-detalle.component.html'
})
export class LibroDetalleComponent implements OnInit {
  libro: Libro | null = null;
  favoritosIds = new Set<number>();
  errorMsg: string = '';
  reservaCreada: Reservacion | null = null;

  mostrarModalReserva = false;
  fechaRetiro: string = '';
  minFechaRetiro: string = '';

  constructor(
    private route: ActivatedRoute,
    private libroService: LibroService,
    private favoritoService: FavoritoService,
    private reservacionService: ReservacionService,
    private reservacionesPendientes: ReservacionPendienteService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    const hoy = new Date();
    this.minFechaRetiro = hoy.toISOString().split('T')[0];
    this.fechaRetiro = this.minFechaRetiro;

    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!id) {
      this.errorMsg = 'Libro no encontrado';
      return;
    }
    this.libroService.obtener(id).subscribe({
      next: (libro) => (this.libro = libro),
      error: () => (this.errorMsg = 'Error al cargar el libro')
    });
    this.favoritoService.listar().subscribe({
      next: (favoritos) => {
        this.favoritosIds = new Set(favoritos.map(f => f.libroId));
      },
      error: () => {}
    });
    this.reservacionesPendientes.cargar().subscribe({
      error: () => {} // sin el set, el boton solo permite reservar
    });
  }

  esFavorito(): boolean {
    return this.libro !== null && this.favoritosIds.has(this.libro.id);
  }

  // El icono Material star se rellena con font-variation-settings 'FILL' 1
  // (ver catalogo.component: sin comillas internas en el binding del template).
  estiloIconoFavorito(): string {
    return this.esFavorito() ? '"FILL" 1' : '"FILL" 0';
  }

  estaReservado(): boolean {
    return this.libro !== null && this.reservacionesPendientes.esPendiente(this.libro.id);
  }

  abrirModalReserva(): void {
    if (!this.libro) return;
    const usuarioId = this.authService.getUserId();
    if (usuarioId === null) {
      this.errorMsg = 'Inicia sesión para reservar';
      return;
    }
    this.fechaRetiro = this.minFechaRetiro;
    this.mostrarModalReserva = true;
  }

  cancelarReserva(): void {
    this.mostrarModalReserva = false;
  }

  confirmarReserva(): void {
    if (!this.libro) return;
    this.mostrarModalReserva = false;
    const usuarioId = this.authService.getUserId();
    const fechaRetiroISO = this.fechaRetiro ? this.fechaRetiro + 'T00:00:00' : undefined;
    this.reservacionService.crear({ usuarioId: usuarioId!, libroId: this.libro.id, fechaRetiro: fechaRetiroISO }).subscribe({
      next: (r) => {
        this.reservacionesPendientes.marcarReservada(this.libro!.id);
        this.reservaCreada = r;
      },
      error: () => (this.errorMsg = 'Error al reservar el libro')
    });
  }

  reservarLibro(): void {
    this.abrirModalReserva();
  }

  alternarFavorito(): void {
    if (!this.libro) return;
    if (this.esFavorito()) {
      this.favoritoService.quitar(this.libro.id).subscribe({
        next: () => this.favoritosIds.delete(this.libro!.id),
        error: () => (this.errorMsg = 'Error al quitar de favoritos')
      });
    } else {
      this.favoritoService.agregar(this.libro.id).subscribe({
        next: () => this.favoritosIds.add(this.libro!.id),
        error: () => (this.errorMsg = 'Error al agregar a favoritos')
      });
    }
  }

  categoriasTexto(): string {
    return this.libro?.categorias?.length ? this.libro.categorias.join(' · ') : '';
  }

  autoresTexto(): string {
    return this.libro?.autores?.length ? this.libro.autores.join(', ') : '—';
  }
}