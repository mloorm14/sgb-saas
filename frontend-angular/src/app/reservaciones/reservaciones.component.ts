import { Component, HostListener, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ReactiveFormsModule, FormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../core/services/auth.service';
import { ReservacionService } from '../core/services/reservacion.service';
import { PrestamoService } from '../core/services/prestamo.service';
import { LibroService } from '../core/services/libro.service';
import { Reservacion, ReservacionRequest } from '../core/models/reservacion.model';
import { UsuarioReservaciones, HistorialReservacion } from '../core/models/reservaciones-gestion.model';
import { UsuarioSugerencia } from '../core/models/prestamos-gestion.model';
import { Libro, LibroSugerencia } from '../core/models/libro.model';
import { BuscadorLibroComponent } from '../shared/buscador-libro/buscador-libro.component';
import { PortadaLibroComponent } from '../shared/portada-libro/portada-libro.component';
import { toOffsetDateTime } from '../core/utils/fecha';
import { ToastService } from '../shared/toast/toast.service';

@Component({
  standalone: true,
  selector: 'app-reservaciones',
  imports: [
    CommonModule, ReactiveFormsModule, FormsModule, RouterLink,
    BuscadorLibroComponent, PortadaLibroComponent
  ],
  templateUrl: './reservaciones.component.html'
})
export class ReservacionesComponent implements OnInit, OnDestroy {
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
  accionandoId: number | null = null;

  readonly estadosReservacion: Record<number, string> = {
    1: 'Pendiente',
    2: 'Lista para retiro',
    3: 'Retirada',
    4: 'Expirada',
    5: 'Cancelada'
  };

  private titulosLibros = new Map<number, string>();
  private titulosEnCarga = new Set<number>();

  correoBusqueda: string = '';
  buscando: boolean = false;
  errorBusqueda: string = '';
  usuario: UsuarioReservaciones | null = null;

  sugerenciasCorreo: UsuarioSugerencia[] = [];
  mostrarSugerenciasCorreo: boolean = false;
  buscandoSugerenciasCorreo: boolean = false;
  placeholderCorreo: string = 'Ingresa un correo';
  indiceActivoCorreo: number = -1;
  private busquedaCorreo$ = new Subject<string>();
  private destroy$ = new Subject<void>();

  libroSeleccionado: LibroSugerencia | null = null;
  libroCompleto: Libro | null = null;
  cargandoLibro: boolean = false;

  fechaRetiro: string = '';
  minFechaRetiro: string = '';

  historial: HistorialReservacion[] = [];
  cargandoHistorial: boolean = false;

  exitoMsg: string = '';
  errorMsgAccion: string = '';
  creandoReservacion: boolean = false;

  portadaModalVisible: boolean = false;
  portadaModalUrl: string | null = null;
  portadaModalCargando: boolean = false;

  constructor(
    private reservacionService: ReservacionService,
    private prestamoService: PrestamoService,
    private libroService: LibroService,
    private fb: FormBuilder,
    private authService: AuthService,
    private toast: ToastService
  ) {
    this.formCrear = this.fb.group({
      usuarioId: ['', [Validators.required]],
      libroId: ['', [Validators.required]]
    });

    const hoy = new Date();
    this.minFechaRetiro = hoy.toISOString().split('T')[0];
    this.fechaRetiro = this.minFechaRetiro;
  }

  ngOnInit(): void {
    this.esLector = this.authService.hasRole('LECTOR') && !this.authService.hasRole('BIBLIOTECARIO', 'GERENTE', 'ADMIN');

    if (this.esLector) {
      this.usuarioIdBusqueda = this.authService.getUserId();
      this.cargarReservacionesLector();
    } else {
      this.busquedaCorreo$.pipe(
        debounceTime(1300),
        distinctUntilChanged(),
        takeUntil(this.destroy$)
      ).subscribe(texto => this.buscarSugerenciasCorreo(texto));
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.busquedaCorreo$.complete();
  }

  private cargarReservacionesLector(): void {
    if (!this.usuarioIdBusqueda) return;
    this.cargando = true;
    this.reservacionService.listarPorUsuario(this.usuarioIdBusqueda, {
      page: 0, size: 100, sort: 'id,desc'
    }).subscribe({
      next: (data) => { this.reservaciones = data.content; this.cargando = false; },
      error: () => { this.errorMsg = 'Error al buscar las reservaciones'; this.cargando = false; }
    });
  }

  get pendientesDeRetiro(): Reservacion[] {
    return this.reservaciones.filter(r => r.estadoReservacionId === 1 || r.estadoReservacionId === 2);
  }

  get historialLector(): Reservacion[] {
    return this.reservaciones.filter(r => r.estadoReservacionId !== 1 && r.estadoReservacionId !== 2);
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
    return `Libro #${libroId}`;
  }

  claseEstadoReservacion(estadoId: number): string {
    switch (estadoId) {
      case 1: return 'bg-tertiary-fixed text-on-tertiary-fixed';
      case 2: return 'bg-secondary-container text-on-secondary-container';
      case 3: return 'bg-surface-container-low text-on-surface-variant';
      case 4: return 'bg-error-container text-on-error-container';
      case 5: return 'bg-error-container text-on-error-container';
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

  cancelarReservacion(r: Reservacion): void {
    if (this.accionandoId !== null || r.estadoReservacionId !== 1) return;
    if (!confirm('¿Seguro que quieres cancelar esta reserva?')) return;
    this.cambiarEstadoReservacion(r, 'CANCELADA');
    if (this.esLector) {
      setTimeout(() => this.cargarReservacionesLector(), 500);
    }
  }

  cancelarHistorial(r: HistorialReservacion): void {
    if (!confirm('¿Seguro que quieres cancelar esta reserva?')) return;
    this.accionandoId = r.reservacionId;
    this.reservacionService.cambiarEstado(r.reservacionId, { nuevoEstado: 'CANCELADA' }).subscribe({
      next: () => {
        this.toast.success('Cancelada', 'Reserva cancelada correctamente');
        if (this.usuario) this.cargarHistorial(this.usuario.id);
      },
      error: (err: any) => {
        const detail = (err?.error as { detail?: string })?.detail ?? 'No se pudo cancelar';
        this.errorMsgAccion = detail;
        this.toast.error('Error', detail);
        this.accionandoId = null;
      },
      complete: () => { this.accionandoId = null; }
    });
  }

  cambiarEstadoReservacion(r: Reservacion, nuevoEstado: 'LISTA_PARA_RETIRO' | 'CANCELADA'): void {
    if (this.accionandoId !== null || r.estadoReservacionId !== 1) return;
    this.accionandoId = r.id;
    this.errorMsg = '';
    this.reservacionService.cambiarEstado(r.id, { nuevoEstado }).subscribe({
      next: () => {
        if (this.esLector) {
          this.cargarReservacionesLector();
          this.toast.success('Cancelada', 'Reserva cancelada correctamente');
        } else {
          this.cargarPagina();
          this.toast.success('Actualizado', nuevoEstado === 'LISTA_PARA_RETIRO' ? 'Reserva marcada lista para retiro' : 'Reserva cancelada');
        }
      },
      error: (err) => {
        this.errorMsg = (err as { error?: { detail?: string } })?.error?.detail
          ?? 'No se pudo cambiar el estado de la reservación';
        this.toast.error('Error', this.errorMsg);
        this.accionandoId = null;
      },
      complete: () => { this.accionandoId = null; }
    });
  }

  private cargarPagina(): void {
    this.cargando = true;
    this.reservacionService.listarPorUsuario(this.usuarioIdBusqueda!, {
      page: this.currentPage, size: this.pageSize, sort: 'id,desc'
    }).subscribe({
      next: (data) => { this.reservaciones = data.content; this.totalPages = data.totalPages; this.cargando = false; },
      error: () => { this.errorMsg = 'Error al buscar las reservaciones'; this.cargando = false; }
    });
  }

  paginaAnterior(): void {
    if (this.currentPage > 0) { this.currentPage--; this.cargarPagina(); }
  }

  paginaSiguiente(): void {
    if (this.currentPage < this.totalPages - 1) { this.currentPage++; this.cargarPagina(); }
  }

  onInputCorreo(): void {
    this.correoBusqueda = this.correoBusqueda.replace(/\s/g, '');
    this.indiceActivoCorreo = -1;
    const texto = this.correoBusqueda.trim();
    if (texto.length < 2) {
      this.sugerenciasCorreo = [];
      this.mostrarSugerenciasCorreo = false;
      this.placeholderCorreo = 'Ingresa un correo';
      return;
    }
    this.busquedaCorreo$.next(texto);
  }

  private buscarSugerenciasCorreo(texto: string): void {
    if (texto.length < 2) {
      this.sugerenciasCorreo = [];
      this.mostrarSugerenciasCorreo = false;
      return;
    }
    this.buscandoSugerenciasCorreo = true;
    this.prestamoService.sugerenciasUsuarios(texto).subscribe({
      next: (lista) => {
        this.sugerenciasCorreo = lista;
        this.mostrarSugerenciasCorreo = lista.length > 0;
        this.buscandoSugerenciasCorreo = false;
        if (lista.length > 0) {
          this.placeholderCorreo = lista[0].correo;
        } else {
          this.placeholderCorreo = 'Ingresa un correo';
        }
      },
      error: () => {
        this.sugerenciasCorreo = [];
        this.mostrarSugerenciasCorreo = false;
        this.buscandoSugerenciasCorreo = false;
      }
    });
  }

  onKeydownCorreo(event: KeyboardEvent): void {
    if (!this.mostrarSugerenciasCorreo || this.sugerenciasCorreo.length === 0) {
      if (event.key === 'Enter') {
        event.preventDefault();
        this.buscarUsuario();
      }
      return;
    }
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.indiceActivoCorreo = Math.min(this.indiceActivoCorreo + 1, this.sugerenciasCorreo.length - 1);
        this.placeholderCorreo = this.sugerenciasCorreo[this.indiceActivoCorreo].correo;
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.indiceActivoCorreo = Math.max(this.indiceActivoCorreo - 1, -1);
        if (this.indiceActivoCorreo >= 0) {
          this.placeholderCorreo = this.sugerenciasCorreo[this.indiceActivoCorreo].correo;
        } else {
          this.placeholderCorreo = 'Ingresa un correo';
        }
        break;
      case 'Tab':
        event.preventDefault();
        if (this.indiceActivoCorreo >= 0) {
          this.seleccionarSugerenciaCorreo(this.sugerenciasCorreo[this.indiceActivoCorreo]);
        } else if (this.sugerenciasCorreo.length > 0) {
          this.seleccionarSugerenciaCorreo(this.sugerenciasCorreo[0]);
        }
        break;
      case 'Enter':
        event.preventDefault();
        if (this.indiceActivoCorreo >= 0) {
          this.seleccionarSugerenciaCorreo(this.sugerenciasCorreo[this.indiceActivoCorreo]);
        } else {
          this.buscarUsuario();
        }
        break;
      case 'Escape':
        this.mostrarSugerenciasCorreo = false;
        this.indiceActivoCorreo = -1;
        break;
    }
  }

  onFocusCorreo(): void {
    if (this.sugerenciasCorreo.length > 0 && this.correoBusqueda.trim().length >= 2) {
      this.mostrarSugerenciasCorreo = true;
    }
  }

  onBlurCorreo(): void {
    setTimeout(() => {
      this.mostrarSugerenciasCorreo = false;
      this.indiceActivoCorreo = -1;
    }, 200);
  }

  seleccionarSugerenciaCorreo(sugerencia: UsuarioSugerencia): void {
    this.correoBusqueda = sugerencia.correo;
    this.sugerenciasCorreo = [];
    this.mostrarSugerenciasCorreo = false;
    this.placeholderCorreo = sugerencia.correo;
    this.buscarUsuario();
  }

  buscarUsuario(): void {
    if (this.correoBusqueda.trim().length < 2) {
      this.errorBusqueda = 'Ingresa un correo electronico valido';
      return;
    }
    this.buscando = true;
    this.errorBusqueda = '';
    this.mostrarSugerenciasCorreo = false;
    this.limpiarResultado();
    this.reservacionService.buscarUsuarioPorCorreo(this.correoBusqueda).subscribe({
      next: (usuario) => {
        this.usuario = usuario;
        this.buscando = false;
        this.cargarHistorial(usuario.id);
      },
      error: (err: HttpErrorResponse) => {
        this.buscando = false;
        if (err.status === 404) {
          this.errorBusqueda = 'No se encontro ningun usuario con este correo';
        } else {
          this.errorBusqueda = 'Error al buscar el usuario. Intenta nuevamente.';
        }
      }
    });
  }

  limpiarBusqueda(): void {
    this.correoBusqueda = '';
    this.errorBusqueda = '';
    this.placeholderCorreo = 'Ingresa un correo';
    this.limpiarResultado();
  }

  private limpiarResultado(): void {
    this.usuario = null;
    this.libroSeleccionado = null;
    this.libroCompleto = null;
    this.cargandoLibro = false;
    this.historial = [];
    this.cargandoHistorial = false;
    this.exitoMsg = '';
    this.errorMsgAccion = '';
  }

  onLibroSeleccionado(libro: LibroSugerencia | null): void {
    this.libroSeleccionado = libro;
    this.libroCompleto = null;
    if (libro) {
      this.cargandoLibro = true;
      this.libroService.obtener(libro.id).subscribe({
        next: (libroCompleto) => {
          this.libroCompleto = libroCompleto;
          this.cargandoLibro = false;
        },
        error: () => {
          this.cargandoLibro = false;
        }
      });
    }
  }

  private cargarHistorial(usuarioId: number): void {
    this.cargandoHistorial = true;
    this.historial = [];
    this.reservacionService.historialReservaciones(usuarioId).subscribe({
      next: (lista) => {
        this.historial = lista;
        this.cargandoHistorial = false;
      },
      error: () => {
        this.cargandoHistorial = false;
      }
    });
  }

  get puedeReservar(): boolean {
    return this.usuario !== null
      && this.libroSeleccionado !== null
      && this.fechaRetiro !== ''
      && !this.creandoReservacion;
  }

  crearReservacion(): void {
    if (!this.usuario || !this.libroSeleccionado || !this.fechaRetiro) return;
    this.creandoReservacion = true;
    this.exitoMsg = '';
    this.errorMsgAccion = '';

    const request: ReservacionRequest = {
      usuarioId: this.usuario.id,
      libroId: this.libroSeleccionado.id,
      fechaRetiro: toOffsetDateTime(this.fechaRetiro)
    };

    this.reservacionService.crear(request).subscribe({
      next: () => {
        this.exitoMsg = 'Reservacion creada exitosamente';
        this.toast.success('Reserva', this.exitoMsg);
        this.creandoReservacion = false;
        this.libroSeleccionado = null;
        this.libroCompleto = null;
        this.fechaRetiro = this.minFechaRetiro;
        if (this.usuario) {
          this.cargarHistorial(this.usuario.id);
          this.reservacionService.buscarUsuarioPorCorreo(this.usuario.correo).subscribe({
            next: (u) => this.usuario = u,
            error: () => {}
          });
        }
      },
      error: (err: HttpErrorResponse) => {
        this.creandoReservacion = false;
        const problem = (err?.error as { detail?: string })?.detail;
        this.errorMsgAccion = problem ?? 'No se pudo crear la reservacion';
        if (problem?.includes('máximo') || problem?.includes('maximo')) this.toast.warning('Limite alcanzado', this.errorMsgAccion);
        else if (problem?.includes('multa') || problem?.includes('deuda') || problem?.includes('bloquead')) this.toast.warning('Aviso', this.errorMsgAccion);
        else this.toast.error('Error', this.errorMsgAccion);
      }
    });
  }

  iniciales(nombreCompleto: string): string {
    const partes = nombreCompleto.trim().split(/\s+/).filter(Boolean);
    if (partes.length === 0) return '??';
    if (partes.length === 1) return partes[0].substring(0, 2).toUpperCase();
    return (partes[0][0] + partes[partes.length - 1][0]).toUpperCase();
  }

  claseEstadoCuenta(estado: string): string {
    switch (estado) {
      case 'ACTIVO': return 'bg-success text-white';
      case 'BLOQUEADO_POR_MULTA': return 'bg-error text-on-error';
      case 'PENDIENTE_VERIFICACION': return 'bg-warning text-tertiary';
      default: return 'bg-surface-container-highest text-on-surface-variant';
    }
  }

  etiquetaEstadoCuenta(estado: string): string {
    switch (estado) {
      case 'ACTIVO': return 'Activo';
      case 'BLOQUEADO_POR_MULTA': return 'Suspendido';
      case 'PENDIENTE_VERIFICACION': return 'Pendiente de verificación';
      case 'INACTIVO': return 'Inactivo';
      default: return estado;
    }
  }

  claseEstadoReservacionHistorial(estadoId: number): string {
    switch (estadoId) {
      case 1: return 'bg-blue-100 text-blue-700';
      case 2: return 'bg-blue-100 text-blue-700';
      case 3: return 'bg-green-100 text-green-700';
      case 4: return 'bg-red-100 text-red-700';
      case 5: return 'bg-red-50 text-red-600';
      default: return 'bg-gray-100 text-gray-600';
    }
  }

  textoEstadoReservacion(estadoId: number): string {
    switch (estadoId) {
      case 1: return 'Pendiente';
      case 2: return 'Pendiente';
      case 3: return 'Completada';
      case 4: return 'Vencida';
      case 5: return 'Cancelada';
      default: return 'Desconocido';
    }
  }

  formatearFecha(iso: string | null): string {
    if (!iso) return '---';
    const fecha = new Date(iso);
    const meses = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
    return `${fecha.getDate()} ${meses[fecha.getMonth()]} ${fecha.getFullYear()}`;
  }

  abrirPortada(libroId: number, tienePortada: boolean): void {
    if (!tienePortada) return;
    this.portadaModalVisible = true;
    this.portadaModalCargando = true;
    this.portadaModalUrl = null;
    this.libroService.obtenerPortada(libroId).subscribe({
      next: (blob) => {
        this.portadaModalUrl = URL.createObjectURL(blob);
        this.portadaModalCargando = false;
      },
      error: () => {
        this.portadaModalCargando = false;
      }
    });
  }

  cerrarPortada(): void {
    if (this.portadaModalUrl) {
      URL.revokeObjectURL(this.portadaModalUrl);
    }
    this.portadaModalVisible = false;
    this.portadaModalUrl = null;
    this.portadaModalCargando = false;
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.portadaModalVisible) {
      this.cerrarPortada();
    }
  }
}