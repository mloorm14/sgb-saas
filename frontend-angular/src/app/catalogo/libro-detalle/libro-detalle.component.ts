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
import { toOffsetDateTime } from '../../core/utils/fecha';
import { SuscripcionDisponibilidadService } from '../../core/services/suscripcion-disponibilidad.service';
import { ToastService } from '../../shared/toast/toast.service';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';

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
  maxFechaRetiro: string = '';
  notificarMsg: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private libroService: LibroService,
    private favoritoService: FavoritoService,
    private reservacionService: ReservacionService,
    private reservacionesPendientes: ReservacionPendienteService,
    private authService: AuthService,
    private suscripcionService: SuscripcionDisponibilidadService,
    private toast: ToastService,
    private confirmDialog: ConfirmDialogService
  ) {}

  ngOnInit(): void {
    const hoy = new Date();
    this.minFechaRetiro = hoy.toISOString().split('T')[0];
    const max = new Date(hoy); max.setDate(max.getDate()+14);
    this.maxFechaRetiro = max.toISOString().split('T')[0];
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
    const hoyStr = new Date().toISOString().split('T')[0];
    if (this.fechaRetiro === hoyStr && new Date().getHours() >= 18) {
      this.confirmDialog.confirm({
        title: 'Hora limite superada',
        message: 'Ya paso la hora limite (18:00). ¿Quieres retirarlo mañana hasta las 18:00?',
        confirmText: 'Retirar mañana',
        cancelText: 'Cancelar',
        variant: 'default'
      }).subscribe((ok: boolean) => {
        if (!ok) return;
        const manana = new Date(); manana.setDate(manana.getDate()+1);
        this.fechaRetiro = manana.toISOString().split('T')[0];
        this.ejecutarReserva();
      });
      return;
    }
    this.ejecutarReserva();
  }

  private ejecutarReserva(): void {
    if (!this.libro) return;
    this.mostrarModalReserva = false;
    const usuarioId = this.authService.getUserId();
    const fechaRetiroISO = this.fechaRetiro ? toOffsetDateTime(this.fechaRetiro) : undefined;
    this.reservacionService.crear({ usuarioId: usuarioId!, libroId: this.libro.id, fechaRetiro: fechaRetiroISO }).subscribe({
      next: (r) => {
        this.reservacionesPendientes.marcarReservada(this.libro!.id);
        this.reservaCreada = r;
        this.toast.success('Reserva', 'Reserva creada correctamente');
      },
      error: (err: any) => {
        const detail = (err?.error as { detail?: string })?.detail;
        this.errorMsg = detail ?? 'No se pudo reservar el libro';
        if (detail?.includes('máximo') || detail?.includes('maximo')) this.toast.warning('Limite alcanzado', this.errorMsg);
        else if (detail?.includes('multa') || detail?.includes('deuda')) this.toast.warning('Aviso', this.errorMsg);
        else this.toast.error('Error', this.errorMsg);
      }
    });
  }

  reservarLibro(): void {
    if (!this.libro) return;
    if (this.libro.stockDisponible <= 0) {
      this.notificarDisponibilidad();
      return;
    }
    this.abrirModalReserva();
  }

  notificarDisponibilidad(): void {
    if (!this.libro) return;
    const uid = this.authService.getUserId();
    if (uid === null) { this.errorMsg = 'Inicia sesion para usar Notificarme'; this.toast.warning('Aviso', this.errorMsg); return; }
    this.suscripcionService.suscribir(this.libro.id).subscribe({
      next: () => {
        this.notificarMsg = `Te avisaremos cuando "${this.libro!.titulo}" este disponible — reservalo antes que otros.`;
        this.toast.success('Suscripcion', this.notificarMsg);
      },
      error: (err: any) => {
        this.errorMsg = (err?.error as any)?.detail ?? 'No se pudo suscribir';
        this.toast.error('Error', this.errorMsg);
      }
    });
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