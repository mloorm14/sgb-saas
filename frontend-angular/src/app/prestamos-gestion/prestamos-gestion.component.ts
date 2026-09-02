import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';
import { PrestamoService } from '../core/services/prestamo.service';
import { LibroService } from '../core/services/libro.service';
import { AuthService } from '../core/services/auth.service';
import { PrestamoRequest } from '../core/models/prestamo.model';
import {
  HistorialPrestamo,
  ReservaActiva,
  UsuarioPrestamos,
  UsuarioSugerencia
} from '../core/models/prestamos-gestion.model';
import { PortadaLibroComponent } from '../shared/portada-libro/portada-libro.component';

@Component({
  standalone: true,
  selector: 'app-prestamos-gestion',
  imports: [CommonModule, FormsModule, PortadaLibroComponent],
  templateUrl: './prestamos-gestion.component.html'
})
export class PrestamosGestionComponent {

  showSinPermisosModal = false;

  // ── Búsqueda por correo (con autocompletado) ─────────────
  correoBusqueda: string = '';
  buscando: boolean = false;
  errorBusqueda: string = '';
  usuario: UsuarioPrestamos | null = null;

  sugerenciasCorreo: UsuarioSugerencia[] = [];
  mostrarSugerenciasCorreo: boolean = false;
  buscandoSugerenciasCorreo: boolean = false;
  placeholderCorreo: string = 'Correo electrónico';
  indiceActivoCorreo: number = -1;
  private busquedaCorreo$ = new Subject<string>();
  private destroy$ = new Subject<void>();

  // ── Reserva activa ──────────────────────────────────────
  reserva: ReservaActiva | null = null;
  cargandoReserva: boolean = false;
  diasReserva: number | null = null;
  confirmandoEntrega: boolean = false;

  // ── Historial reciente ──────────────────────────────────
  historial: HistorialPrestamo[] = [];
  cargandoHistorial: boolean = false;

  pagina = 0;
  tamanoPagina = 10;

  get totalPaginas(): number {
    return Math.max(1, Math.ceil(this.historial.length / this.tamanoPagina));
  }
  get datosPaginados() {
    const start = this.pagina * this.tamanoPagina;
    return this.historial.slice(start, start + this.tamanoPagina);
  }
  get paginasVisibles(): number[] {
    const windowSize = 4;
    let start = Math.max(0, this.pagina - 1);
    let end = Math.min(this.totalPaginas, start + windowSize);
    if (end - start < windowSize) start = Math.max(0, end - windowSize);
    return Array.from({ length: end - start }, (_, i) => start + i);
  }
  get puedeAnterior(): boolean { return this.pagina > 0; }
  get puedeSiguiente(): boolean { return this.pagina < this.totalPaginas - 1; }
  irAPagina(p: number): void {
    if (p < 0 || p >= this.totalPaginas || p === this.pagina) return;
    this.pagina = p;
  }
  paginaAnterior(): void { if (this.puedeAnterior) this.pagina--; }
  paginaSiguiente(): void { if (this.puedeSiguiente) this.pagina++; }
  cambiarTamano(n: number): void {
    this.tamanoPagina = Number(n);
    this.pagina = 0;
  }

  exitoMsg: string = '';
  errorMsgAccion: string = '';

  // ── Popup portada del libro ─────────────────────────────
  portadaModalVisible: boolean = false;
  portadaModalUrl: string | null = null;
  portadaModalCargando: boolean = false;

  constructor(
    private prestamoService: PrestamoService,
    private libroService: LibroService,
    private router: Router,
    private authService: AuthService
  ) {
    this.busquedaCorreo$.pipe(
      debounceTime(1300),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(texto => this.buscarSugerenciasCorreo(texto));
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.busquedaCorreo$.complete();
  }

  // ── Autocompletado de correo ──────────────────────────────
  onInputCorreo(): void {
    this.correoBusqueda = this.correoBusqueda.replace(/\s/g, '');
    this.indiceActivoCorreo = -1;
    const texto = this.correoBusqueda.trim();
    if (texto.length < 2) {
      this.sugerenciasCorreo = [];
      this.mostrarSugerenciasCorreo = false;
      this.placeholderCorreo = 'Ingresa el correo del usuario';
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
          this.placeholderCorreo = 'Ingresa el correo del usuario';
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
          this.placeholderCorreo = 'Ingresa el correo del usuario';
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

  // ── Búsqueda de usuario ────────────────────────────────
  get estaBloqueado(): boolean {
    if (!this.usuario) return false;
    return this.usuario.estadoCuenta !== 'ACTIVO'
      || this.usuario.montoMultasPendientes > 0;
  }

  get motivoBloqueo(): string {
    const u = this.usuario!;
    const motivos: string[] = [];
    if (u.estadoCuenta === 'BLOQUEADO_POR_MULTA') {
      motivos.push('se encuentra bloqueado por multas');
    } else if (u.estadoCuenta === 'INACTIVO') {
      motivos.push('su cuenta se encuentra inactiva');
    } else if (u.estadoCuenta === 'PENDIENTE_VERIFICACION') {
      motivos.push('su cuenta está pendiente de verificación');
    }
    if (u.montoMultasPendientes > 0) {
      motivos.push(`tiene multas pendientes de pago ($${u.montoMultasPendientes.toFixed(2)})`);
    }
    return `El usuario no puede realizar nuevos préstamos debido a que ${motivos.join(' y ')}.`;
  }

  buscarUsuario(): void {
    if (this.correoBusqueda.trim().length < 2) {
      this.errorBusqueda = 'Ingresa un correo electrónico válido';
      return;
    }
    this.buscando = true;
    this.errorBusqueda = '';
    this.mostrarSugerenciasCorreo = false;
    this.limpiarResultado();
    this.prestamoService.buscarUsuarioPorCorreo(this.correoBusqueda).subscribe({
      next: (usuario) => {
        this.usuario = usuario;
        this.buscando = false;
        this.diasReserva = usuario.diasPrestamoSugerido;
        this.cargarHistorial(usuario.id);
        if (this.estaBloqueado) return;
        this.consultarReservaActiva(usuario.id);
      },
      error: (err: HttpErrorResponse) => {
        this.buscando = false;
        if (err.status === 404) {
          this.errorBusqueda = 'No se encontró ningún usuario con este correo';
        } else {
          this.errorBusqueda = 'Error al buscar el usuario. Intenta nuevamente.';
        }
      }
    });
  }

  limpiarBusqueda(): void {
    this.correoBusqueda = '';
    this.errorBusqueda = '';
    this.placeholderCorreo = 'Ingresa el correo del usuario';
    this.limpiarResultado();
  }

  private limpiarResultado(): void {
    this.usuario = null;
    this.reserva = null;
    this.cargandoReserva = false;
    this.historial = [];
    this.cargandoHistorial = false;
    this.exitoMsg = '';
    this.errorMsgAccion = '';
  }

  private consultarReservaActiva(usuarioId: number): void {
    this.cargandoReserva = true;
    this.reserva = null;
    this.prestamoService.reservaActiva(usuarioId).subscribe({
      next: (reserva) => {
        this.reserva = reserva;
        this.diasReserva = reserva.diasPrestamoSugerido;
        this.cargandoReserva = false;
      },
      error: (err: HttpErrorResponse) => {
        this.cargandoReserva = false;
        if (err.status !== 404) {
          this.errorMsgAccion = 'Error al consultar la reserva del usuario.';
        }
      }
    });
  }

  private cargarHistorial(usuarioId: number): void {
    this.cargandoHistorial = true;
    this.historial = [];
    this.prestamoService.historial(usuarioId).subscribe({
      next: (lista) => {
        this.historial = lista;
        this.pagina = 0;
        this.cargandoHistorial = false;
      },
      error: () => {
        this.cargandoHistorial = false;
      }
    });
  }

  private refrescarTrasAccion(): void {
    if (!this.usuario) return;
    this.consultarReservaActiva(this.usuario.id);
    this.cargarHistorial(this.usuario.id);
  }

  // ── Confirmar entrega de reserva ────────────────────────
  confirmarEntrega(): void {
    if (!this.usuario || !this.reserva || !this.diasReserva || this.diasReserva < 1) return;
    this.confirmandoEntrega = true;
    this.exitoMsg = '';
    this.errorMsgAccion = '';
    const request: PrestamoRequest = {
      usuarioId: this.usuario.id,
      libroId: this.reserva.libroId,
      diasPrestamo: this.diasReserva,
      reservacionId: this.reserva.reservacionId
    };
    this.prestamoService.crear(request).subscribe({
      next: () => {
        this.confirmandoEntrega = false;
        this.exitoMsg = 'Entrega confirmada: el préstamo quedó registrado y la reserva marcada como retirada.';
        this.refrescarTrasAccion();
      },
      error: (err: HttpErrorResponse) => {
        this.confirmandoEntrega = false;
        this.errorMsgAccion = detalleDeError(err, 'No se pudo confirmar la entrega.');
      }
    });
  }

  // ── Caso C ──────────────────────────────────────────────
  gestionarMultas(): void {
    if (!this.usuario) return;
    const url = this.router.url;
    let ruta: string[];
    if (url.includes('/dashboard-bibliotecario')) {
      ruta = ['/dashboard-bibliotecario', 'multas'];
    } else if (url.includes('/dashboard-admin')) {
      ruta = ['/dashboard-admin', 'multas'];
    } else if (url.includes('/dashboard-lector')) {
      ruta = ['/dashboard-lector', 'multas'];
    } else if (this.authService.hasRole('BIBLIOTECARIO')) {
      ruta = ['/dashboard-bibliotecario', 'multas'];
    } else if (this.authService.hasRole('GERENTE') || this.authService.hasRole('ADMIN')) {
      ruta = ['/dashboard-admin', 'multas'];
    } else {
      ruta = ['/multas'];
    }
    this.router.navigate(ruta, { queryParams: { usuarioId: this.usuario.id } });
  }

  cerrarSinPermisosModal(): void {
    this.showSinPermisosModal = false;
  }

  // ── Presentación ────────────────────────────────────────
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

  iconoEstadoCuenta(estado: string): string {
    switch (estado) {
      case 'ACTIVO': return 'check_circle';
      case 'BLOQUEADO_POR_MULTA': return 'block';
      case 'PENDIENTE_VERIFICACION': return 'hourglass_top';
      default: return 'person_off';
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

  fechaDevolucionEstimada(dias: number | null): string {
    if (!dias || dias < 1) return '—';
    const fecha = new Date();
    fecha.setDate(fecha.getDate() + dias);
    return this.formatearFecha(fecha.toISOString());
  }

  formatearFecha(iso: string | null): string {
    if (!iso) return '—';
    const fecha = new Date(iso);
    const meses = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
    return `${fecha.getDate()} ${meses[fecha.getMonth()]} ${fecha.getFullYear()}`;
  }

  formatearFechaHora(iso: string | null): string {
    if (!iso) return '—';
    const fecha = new Date(iso);
    const hh = String(fecha.getHours()).padStart(2, '0');
    const mm = String(fecha.getMinutes()).padStart(2, '0');
    return `${this.formatearFecha(iso)} ${hh}:${mm}`;
  }

  diasDeAtraso(item: HistorialPrestamo): number {
    const fin = item.fechaDevolucionReal ? new Date(item.fechaDevolucionReal) : new Date();
    const esperada = new Date(item.fechaDevolucionEstimada);
    const dias = Math.ceil((fin.getTime() - esperada.getTime()) / 86400000);
    return Math.max(dias, 0);
  }

  estaVencido(item: HistorialPrestamo): boolean {
    return !item.fechaDevolucionReal && (
      item.multaPendiente
      || item.estadoNombre === 'VENCIDO'
      || this.diasDeAtraso(item) > 0
    );
  }

  iconoHistorial(item: HistorialPrestamo): string {
    if (item.multaPendiente || this.estaVencido(item)) return 'warning';
    if (item.fechaDevolucionReal) return 'check_circle';
    return 'menu_book';
  }

  textoSecundarioHistorial(item: HistorialPrestamo): string {
    if (item.multaPendiente && item.fechaDevolucionReal) {
      return `Devuelto tarde (${this.formatearFecha(item.fechaDevolucionReal)}) — Multa pendiente ($${item.montoMultaPendiente.toFixed(2)})`;
    }
    if (item.multaPendiente) {
      return `Vencido por ${this.diasDeAtraso(item)} día(s) — Multa pendiente ($${item.montoMultaPendiente.toFixed(2)})`;
    }
    if (item.fechaDevolucionReal) {
      return `Devuelto el ${this.formatearFecha(item.fechaDevolucionReal)}`;
    }
    if (this.estaVencido(item)) {
      return `Vencido por ${this.diasDeAtraso(item)} día(s)`;
    }
    return `Prestado hasta ${this.formatearFecha(item.fechaDevolucionEstimada)}`;
  }

  // ── Popup portada del libro ─────────────────────────────
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
}

function detalleDeError(err: HttpErrorResponse, fallback: string): string {
  const problem = err?.error as { detail?: string } | undefined;
  return problem?.detail ?? fallback;
}
