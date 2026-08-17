import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ReactiveFormsModule, FormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { AuthService } from '../core/services/auth.service';
import { ReservacionService } from '../core/services/reservacion.service';
import { LibroService } from '../core/services/libro.service';
import { Reservacion, ReservacionRequest } from '../core/models/reservacion.model';

// Dos roles en un mismo componente. Desde el mockup 18, el LECTOR ya no
// crea reservaciones acá (se hacen desde el catálogo/detalle): su vista
// tiene solo "Pendientes de retiro" + "Historial" con link al catálogo.
// BIBLIOTECARIO/GERENTE conservan "Gestión de reservaciones" con el
// formulario usuarioId/libroId manual tal cual (no hay endpoint de
// búsqueda de usuarios, gap documentado del roadmap).
@Component({
  selector: 'app-reservaciones',
  imports: [CommonModule, ReactiveFormsModule, FormsModule, RouterLink],
  templateUrl: './reservaciones.component.html'
})
export class ReservacionesComponent implements OnInit {
  esLector: boolean = false;

  formCrear: FormGroup;
  errorMsgCrear: string = '';

  usuarioIdBusqueda: number | null = null;
  reservaciones: Reservacion[] = [];
  totalPages: number = 0;
  currentPage: number = 0;
  pageSize: number = 10;
  cargando: boolean = false;
  errorMsg: string = '';

  // Mapa local de estados: EstadoReservacion existe como entidad +
  // repository en el backend, pero ningún controller expone el catálogo
  // (gap del roadmap, mismo criterio que con tipoNotificacionId).
  // Workaround temporal hasta que exista un endpoint de catálogo.
  // IDs/nombres confirmados contra db/seed.sql y
  // V10__seed_catalogos_y_admin.sql (SERIAL: 1 PENDIENTE, 2
  // LISTA_PARA_RETIRO, 3 RETIRADA, 4 EXPIRADA, 5 CANCELADA).
  readonly estadosReservacion: Record<number, string> = {
    1: 'Pendiente',
    2: 'Lista para retiro',
    3: 'Retirada',
    4: 'Expirada',
    5: 'Cancelada'
  };

  // Cache de títulos por libroId: ReservacionResponseDTO solo trae
  // libroId; se resuelve con LibroService.obtener() UNA vez por libro.
  private titulosLibros = new Map<number, string>();
  private titulosEnCarga = new Set<number>();

  constructor(
    private reservacionService: ReservacionService,
    private libroService: LibroService,
    private fb: FormBuilder,
    private authService: AuthService
  ) {
    this.formCrear = this.fb.group({
      usuarioId: ['', [Validators.required]],
      libroId: ['', [Validators.required]]
    });
  }

  ngOnInit(): void {
    this.esLector = this.authService.hasRole('LECTOR') && !this.authService.hasRole('BIBLIOTECARIO', 'GERENTE', 'ADMIN');

    if (this.esLector) {
      // El lector ya no crea acá: ve solo lo suyo (mockup 18)
      this.usuarioIdBusqueda = this.authService.getUserId();
      this.cargarReservacionesLector();
    }
  }

  // LECTOR: un solo fetch (size 100, escala demo) y la vista divide en
  // "Pendientes de retiro" vs "Historial" con los getters de abajo. La
  // paginación del backend queda solo para el modo gestión.
  private cargarReservacionesLector(): void {
    if (!this.usuarioIdBusqueda) return;
    this.cargando = true;
    this.reservacionService.listarPorUsuario(this.usuarioIdBusqueda, {
      page: 0,
      size: 100,
      sort: 'id,desc'
    }).subscribe({
      next: (data) => {
        this.reservaciones = data.content;
        this.cargando = false;
      },
      error: () => {
        this.errorMsg = 'Error al buscar las reservaciones';
        this.cargando = false;
      }
    });
  }

  // "Pendientes de retiro" = PENDIENTE(1) o LISTA_PARA_RETIRO(2), mismo
  // criterio de reserva vigente que PrestamoService.renovar(); el resto
  // (RETIRADA/EXPIRADA/CANCELADA) es Historial.
  get pendientesDeRetiro(): Reservacion[] {
    return this.reservaciones.filter(
      r => r.estadoReservacionId === 1 || r.estadoReservacionId === 2
    );
  }

  get historial(): Reservacion[] {
    return this.reservaciones.filter(
      r => r.estadoReservacionId !== 1 && r.estadoReservacionId !== 2
    );
  }

  crearReservacion(): void {
    if (this.formCrear.invalid) return;
    this.errorMsgCrear = '';
    this.reservacionService.crear(this.formCrear.value as ReservacionRequest).subscribe({
      next: () => {
        this.formCrear.patchValue({ libroId: '' });
        if (this.usuarioIdBusqueda === Number(this.formCrear.value.usuarioId)) {
          this.cargarPagina();
        }
      },
      error: () => { this.errorMsgCrear = 'Error al crear la reservación'; }
    });
  }

  tituloLibro(libroId: number): string {
    const cacheado = this.titulosLibros.get(libroId);
    if (cacheado) return cacheado;
    if (!this.titulosEnCarga.has(libroId)) {
      this.titulosEnCarga.add(libroId);
      this.libroService.obtener(libroId).subscribe({
        next: (libro) => this.titulosLibros.set(libroId, libro.titulo),
        error: () => this.titulosLibros.set(libroId, `Libro #${libroId}`)
      });
    }
    return `Libro #${libroId}`; // placeholder mientras llega la respuesta
  }

  claseEstadoReservacion(estadoId: number): string {
    switch (estadoId) {
      case 1: return 'bg-tertiary-fixed text-on-tertiary-fixed';            // Pendiente
      case 2: return 'bg-secondary-container text-on-secondary-container';  // Lista para retiro
      case 3: return 'bg-surface-container-low text-on-surface-variant';    // Retirada
      case 4: return 'bg-error-container text-on-error-container';          // Expirada
      case 5: return 'bg-error-container text-on-error-container';          // Cancelada
      default: return 'bg-surface-container-low text-on-surface-variant';
    }
  }

  buscarReservaciones(): void {
    if (!this.usuarioIdBusqueda) {
      this.errorMsg = 'Ingresá un ID de usuario para buscar';
      return;
    }
    this.errorMsg = '';
    this.currentPage = 0;
    this.cargarPagina();
  }

  private cargarPagina(): void {
    this.cargando = true;
    this.reservacionService.listarPorUsuario(this.usuarioIdBusqueda!, {
      page: this.currentPage,
      size: this.pageSize,
      sort: 'id,desc'
    }).subscribe({
      next: (data) => {
        this.reservaciones = data.content;
        this.totalPages = data.totalPages;
        this.cargando = false;
      },
      error: () => {
        this.errorMsg = 'Error al buscar las reservaciones';
        this.cargando = false;
      }
    });
  }

  paginaAnterior(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.cargarPagina();
    }
  }

  paginaSiguiente(): void {
    if (this.currentPage < this.totalPages - 1) {
      this.currentPage++;
      this.cargarPagina();
    }
  }
}