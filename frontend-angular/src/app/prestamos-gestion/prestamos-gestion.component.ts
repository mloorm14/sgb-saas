import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { PrestamoService } from '../core/services/prestamo.service';
import { LibroService } from '../core/services/libro.service';
import { Libro, LibroSugerencia } from '../core/models/libro.model';
import { PrestamoRequest, DevolucionResponse } from '../core/models/prestamo.model';
import {
  HistorialPrestamo,
  ReservaActiva,
  UsuarioPrestamos
} from '../core/models/prestamos-gestion.model';

// Ventanilla de préstamos del BIBLIOTECARIO (módulo "Préstamos" del
// sidebar). Flujo según la especificación del módulo:
// 1. Estado vacío hasta ingresar una cédula válida y buscar.
// 2. Tarjeta de identificación del usuario encontrado.
// 3. Caso A: reserva vigente -> "Confirmar Entrega" (convierte la reserva
//    en préstamo vía reservacionId).
// 4. Caso B: sin reserva -> préstamo directo con buscador de libros.
// 5. Caso C: usuario bloqueado (estado != ACTIVO o multas pendientes) ->
//    alerta con el motivo exacto y acceso a Multas filtrado por el usuario.
// 6. Historial reciente como línea de tiempo (siempre visible tras buscar).
@Component({
  selector: 'app-prestamos-gestion',
  imports: [CommonModule, FormsModule],
  templateUrl: './prestamos-gestion.component.html'
})
export class PrestamosGestionComponent {

  // ── Búsqueda por cédula ─────────────────────────────────
  cedulaBusqueda: string = '';
  buscando: boolean = false;
  errorCedula: string = '';
  usuario: UsuarioPrestamos | null = null;

  // ── Caso A: reserva activa ──────────────────────────────
  reserva: ReservaActiva | null = null;
  cargandoReserva: boolean = false;
  diasReserva: number | null = null;
  confirmandoEntrega: boolean = false;

  // ── Caso B: préstamo directo ────────────────────────────
  textoLibro: string = '';
  sugerenciasLibro: LibroSugerencia[] = [];
  mostrarSugerencias: boolean = false;
  buscandoSugerencias: boolean = false;
  libroSeleccionado: Libro | null = null;
  advertenciaStock: string = '';
  diasDirecto: number | null = null;
  registrandoDirecto: boolean = false;

  // ── Historial reciente ──────────────────────────────────
  historial: HistorialPrestamo[] = [];
  cargandoHistorial: boolean = false;

  // Mensajes de las acciones (confirmar entrega / registrar directo)
  exitoMsg: string = '';
  errorMsgAccion: string = '';

  // ── Devolución de préstamo ─────────────────────────────
  devolviendoPrestamoId: number | null = null;
  avisoDevolucion: string = '';

  constructor(
    private prestamoService: PrestamoService,
    private libroService: LibroService,
    private router: Router
  ) {}

  // Solo números, formato de cédula: exactamente 10 dígitos.
  onInputCedula(): void {
    this.cedulaBusqueda = this.cedulaBusqueda.replace(/\D/g, '').slice(0, 10);
  }

  get esCedulaValida(): boolean {
    return /^[0-9]{10}$/.test(this.cedulaBusqueda);
  }

  // Caso C: suspendido/bloqueado O con multas pendientes de pago.
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
    if (!this.esCedulaValida) {
      this.errorCedula = 'Ingresa una cédula válida de 10 dígitos';
      return;
    }
    this.buscando = true;
    this.errorCedula = '';
    this.limpiarResultado();
    this.prestamoService.buscarUsuarioPorCedula(this.cedulaBusqueda).subscribe({
      next: (usuario) => {
        this.usuario = usuario;
        this.buscando = false;
        // Días de préstamo prellenados según la configuración del sistema.
        this.diasReserva = usuario.diasPrestamoSugerido;
        this.diasDirecto = usuario.diasPrestamoSugerido;
        this.cargarHistorial(usuario.id);
        // Caso C: no tiene sentido consultar reserva ni ofrecer formularios.
        if (this.estaBloqueado) return;
        this.consultarReservaActiva(usuario.id);
      },
      error: (err: HttpErrorResponse) => {
        this.buscando = false;
        if (err.status === 404) {
          this.errorCedula = 'No se encontró ningún usuario con esta cédula';
        } else {
          this.errorCedula = 'Error al buscar el usuario. Intenta nuevamente.';
        }
      }
    });
  }

  limpiarBusqueda(): void {
    this.cedulaBusqueda = '';
    this.errorCedula = '';
    this.limpiarResultado();
  }

  private limpiarResultado(): void {
    this.usuario = null;
    this.reserva = null;
    this.cargandoReserva = false;
    this.historial = [];
    this.cargandoHistorial = false;
    this.limpiarFormularioLibro();
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
        // 404 = sin reserva vigente -> Caso B (préstamo directo). No es error.
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
        this.cargandoHistorial = false;
      },
      error: () => {
        this.cargandoHistorial = false;
      }
    });
  }

  // Reconsulta reserva e historial tras crear un préstamo (la reserva pasa
  // a RETIRADA y el historial gana una entrada).
  private refrescarTrasAccion(): void {
    if (!this.usuario) return;
    this.consultarReservaActiva(this.usuario.id);
    this.cargarHistorial(this.usuario.id);
  }

  // ── Caso A: confirmar entrega de la reserva ─────────────
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

  // ── Caso B: préstamo directo ────────────────────────────
  buscarSugerencias(): void {
    const texto = this.textoLibro.trim();
    this.advertenciaStock = '';
    if (texto.length < 2) {
      this.sugerenciasLibro = [];
      this.mostrarSugerencias = false;
      return;
    }
    this.buscandoSugerencias = true;
    this.libroService.sugerencias(texto).subscribe({
      next: (lista) => {
        this.sugerenciasLibro = lista;
        this.mostrarSugerencias = true;
        this.buscandoSugerencias = false;
      },
      error: () => {
        this.buscandoSugerencias = false;
      }
    });
  }

  seleccionarLibro(sugerencia: LibroSugerencia): void {
    this.mostrarSugerencias = false;
    this.libroSeleccionado = null;
    this.advertenciaStock = '';
    this.libroService.obtener(sugerencia.id).subscribe({
      next: (libro) => {
        this.libroSeleccionado = libro;
        // Validación de ejemplares disponibles: sin stock se bloquea el botón.
        if (libro.stockDisponible <= 0) {
          this.advertenciaStock = 'Este libro no tiene ejemplares disponibles.';
        }
      },
      error: () => {
        this.advertenciaStock = 'No se pudo cargar el detalle del libro seleccionado.';
      }
    });
  }

  limpiarFormularioLibro(): void {
    this.textoLibro = '';
    this.sugerenciasLibro = [];
    this.mostrarSugerencias = false;
    this.libroSeleccionado = null;
    this.advertenciaStock = '';
  }

  get puedeRegistrarDirecto(): boolean {
    return !!this.libroSeleccionado
      && !!this.diasDirecto && this.diasDirecto >= 1
      && (this.libroSeleccionado?.stockDisponible ?? 0) > 0
      && !this.registrandoDirecto;
  }

  registrarPrestamoDirecto(): void {
    if (!this.usuario || !this.puedeRegistrarDirecto) return;
    this.registrandoDirecto = true;
    this.exitoMsg = '';
    this.errorMsgAccion = '';
    const request: PrestamoRequest = {
      usuarioId: this.usuario.id,
      libroId: this.libroSeleccionado!.id,
      diasPrestamo: this.diasDirecto!
    };
    this.prestamoService.crear(request).subscribe({
      next: () => {
        this.registrandoDirecto = false;
        this.exitoMsg = `Préstamo registrado: "${this.libroSeleccionado!.titulo}" prestado por ${this.diasDirecto} día(s).`;
        this.limpiarFormularioLibro();
        this.refrescarTrasAccion();
      },
      error: (err: HttpErrorResponse) => {
        this.registrandoDirecto = false;
        this.errorMsgAccion = detalleDeError(err, 'No se pudo registrar el préstamo.');
      }
    });
  }

  // ── Caso C: gestionar multas en el módulo existente ─────
  // Lleva al módulo de Multas ya existente, filtrado por este usuario. Si
  // la pantalla corre dentro del panel del bibliotecario se navega a la
  // ruta hija (mantiene el sidebar); si no, a la ruta suelta /multas.
  gestionarMultas(): void {
    if (!this.usuario) return;
    const dentroDelPanel = this.router.url.includes('/dashboard-bibliotecario');
    const ruta = dentroDelPanel ? ['/dashboard-bibliotecario', 'multas'] : ['/multas'];
    this.router.navigate(ruta, { queryParams: { usuarioId: this.usuario.id } });
  }

  // ── Devolución de préstamo activo ──────────────────────
  registrarDevolucion(prestamoId: number): void {
    if (!confirm('¿Confirmás la devolución de este préstamo?')) return;
    this.devolviendoPrestamoId = prestamoId;
    this.exitoMsg = '';
    this.errorMsgAccion = '';
    this.avisoDevolucion = '';
    this.prestamoService.devolver(prestamoId).subscribe({
      next: (resp: DevolucionResponse) => {
        this.devolviendoPrestamoId = null;
        if (resp.huboMulta) {
          this.avisoDevolucion = `Devuelto tarde — Multa pendiente ($${resp.montoMulta.toFixed(2)})`;
        } else {
          this.exitoMsg = 'Préstamo devuelto correctamente.';
        }
        this.refrescarTrasAccion();
      },
      error: (err: HttpErrorResponse) => {
        this.devolviendoPrestamoId = null;
        this.errorMsgAccion = detalleDeError(err, 'No se pudo registrar la devolución.');
      }
    });
  }

  // ── Presentación ────────────────────────────────────────

  iniciales(nombreCompleto: string): string {
    const partes = nombreCompleto.trim().split(/\s+/).filter(Boolean);
    if (partes.length === 0) return '??';
    if (partes.length === 1) return partes[0].substring(0, 2).toUpperCase();
    return (partes[0][0] + partes[partes.length - 1][0]).toUpperCase();
  }

  tiposUsuarioTexto(tipos: string[]): string {
    return tipos.length > 0 ? tipos.join(', ') : '—';
  }

  claseEstadoCuenta(estado: string): string {
    switch (estado) {
      case 'ACTIVO': return 'bg-success text-white';                       // verde
      case 'BLOQUEADO_POR_MULTA': return 'bg-error text-on-error';         // rojo
      case 'PENDIENTE_VERIFICACION': return 'bg-warning text-tertiary';    // ámbar
      default: return 'bg-surface-container-highest text-on-surface-variant'; // INACTIVO u otro
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

  // Fecha de devolución estimada en tiempo real: hoy + días de préstamo.
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

  // Días de atraso de un préstamo: contra la devolución real si ya se
  // devolvió, o contra hoy si sigue pendiente.
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

  claseIconoHistorial(item: HistorialPrestamo): string {
    if (item.multaPendiente || this.estaVencido(item)) return 'text-error bg-error-container';
    if (item.fechaDevolucionReal) return 'text-success bg-success/10';
    return 'text-primary bg-primary-fixed';
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
}

// Extrae el detalle legible de un ProblemDetail (RFC 7807) del backend;
// cae al mensaje genérico si la respuesta no lo trae.
function detalleDeError(err: HttpErrorResponse, fallback: string): string {
  const problem = err?.error as { detail?: string } | undefined;
  return problem?.detail ?? fallback;
}
