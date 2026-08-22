import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';
import { MultaService } from '../core/services/multa.service';
import { PrestamoService } from '../core/services/prestamo.service';
import { AuthService } from '../core/services/auth.service';
import { MultaDetalle, PagoMultaResponse, MultaAccionResponse } from '../core/models/multa.model';
import { UsuarioPrestamos, UsuarioSugerencia } from '../core/models/prestamos-gestion.model';
import { BuscadorUsuarioComponent } from '../shared/buscador-usuario/buscador-usuario.component';

@Component({
  selector: 'app-multas',
  imports: [CommonModule, FormsModule, BuscadorUsuarioComponent],
  templateUrl: './multas.component.html'
})
export class MultasComponent implements OnInit {

  // ── Usuario seleccionado ──────────────────────────────
  usuarioSeleccionado: UsuarioPrestamos | null = null;

  // ── Multas ────────────────────────────────────────────
  multas: MultaDetalle[] = [];
  cargando: boolean = false;
  errorMsg: string = '';
  exitoMsg: string = '';

  // ── Paginación ────────────────────────────────────────
  currentPage: number = 0;
  pageSize: number = 10;
  totalPages: number = 0;

  // ── Filtros ───────────────────────────────────────────
  filtroActivo: 'TODOS' | 'PENDIENTES' | 'PAGADAS' | 'PARCIALES' = 'TODOS';

  // ── Modal pago ────────────────────────────────────────
  modalPagoVisible: boolean = false;
  multaPagoActual: MultaDetalle | null = null;
  montoRecibido: number = 0;

  // ── Confirmación ──────────────────────────────────────
  confirmacionVisible: boolean = false;

  // ── Modal anulación ───────────────────────────────────
  modalAnulacionVisible: boolean = false;
  multaAnulacionActual: MultaDetalle | null = null;
  motivoAnulacion: string = '';
  confirmacionAnulacionVisible: boolean = false;

  private destroy$ = new Subject<void>();

  constructor(
    private multaService: MultaService,
    private prestamoService: PrestamoService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ── Búsqueda de usuario ───────────────────────────────
  onCorreoSeleccionado(correo: string): void {
    this.buscarUsuario(correo);
  }

  onBuscarAhora(correo: string): void {
    this.buscarUsuario(correo);
  }

  private buscarUsuario(correo: string): void {
    this.limpiarEstado();
    this.cargando = true;
    this.errorMsg = '';

    this.prestamoService.buscarUsuarioPorCorreo(correo).subscribe({
      next: (usuario) => {
        this.usuarioSeleccionado = usuario;
        this.cargarMultas(usuario.id);
      },
      error: () => {
        this.cargando = false;
        this.errorMsg = 'No se encontró usuario con ese correo.';
      }
    });
  }

  private cargarMultas(usuarioId: number): void {
    this.cargando = true;
    this.multaService.listarDetallePorUsuario(usuarioId, {
      page: this.currentPage,
      size: this.pageSize,
      sort: 'estadoMultaId,asc'
    }).subscribe({
      next: (data) => {
        this.multas = data.content;
        this.totalPages = data.totalPages;
        this.cargando = false;
      },
      error: () => {
        this.cargando = false;
        this.errorMsg = 'Error al cargar las multas.';
      }
    });
  }

  // ── Filtros ───────────────────────────────────────────
  get multasFiltradas(): MultaDetalle[] {
    switch (this.filtroActivo) {
      case 'PENDIENTES': return this.multas.filter(m => m.estadoMultaId === 1 && m.montoPagado === 0);
      case 'PAGADAS': return this.multas.filter(m => m.estadoMultaId === 2);
      case 'PARCIALES': return this.multas.filter(m => m.montoPagado > 0 && m.estadoMultaId === 1);
      default: return this.multas;
    }
  }

  get totalPendiente(): number {
    return this.multas
      .filter(m => m.estadoMultaId === 1)
      .reduce((sum, m) => sum + m.saldo, 0);
  }

  setFiltro(filtro: 'TODOS' | 'PENDIENTES' | 'PAGADAS' | 'PARCIALES'): void {
    this.filtroActivo = filtro;
  }

  // ── Badges ────────────────────────────────────────────
  claseBadge(multa: MultaDetalle): string {
    if (multa.estadoMultaId === 2) {
      return 'bg-green-100 text-green-700';
    }
    if (multa.montoPagado > 0) {
      return 'bg-amber-100 text-amber-700';
    }
    return 'bg-red-100 text-red-700';
  }

  etiquetaEstado(multa: MultaDetalle): string {
    if (multa.estadoMultaId === 2) return 'Pagada';
    if (multa.montoPagado > 0) return 'Parcial';
    return 'Pendiente';
  }

  // ── Modal pago ────────────────────────────────────────
  abrirModalPago(multa: MultaDetalle): void {
    this.multaPagoActual = multa;
    this.montoRecibido = 0;
    this.modalPagoVisible = true;
  }

  cerrarModalPago(): void {
    this.modalPagoVisible = false;
    this.multaPagoActual = null;
    this.montoRecibido = 0;
  }

  agregarMonto(valor: number): void {
    this.montoRecibido = Math.round((this.montoRecibido + valor) * 100) / 100;
  }

  get cambio(): number {
    if (!this.multaPagoActual) return 0;
    return Math.round((this.montoRecibido - this.multaPagoActual.saldo) * 100) / 100;
  }

  get falta(): number {
    if (!this.multaPagoActual) return 0;
    return Math.round((this.multaPagoActual.saldo - this.montoRecibido) * 100) / 100;
  }

  get esPagoParcial(): boolean {
    return this.montoRecibido > 0 && this.montoRecibido < (this.multaPagoActual?.saldo ?? 0);
  }

  get esExcedente(): boolean {
    return this.montoRecibido > (this.multaPagoActual?.saldo ?? 0);
  }

  // ── Confirmación ──────────────────────────────────────
  abrirConfirmacion(): void {
    if (this.montoRecibido <= 0) return;
    this.confirmacionVisible = true;
  }

  cerrarConfirmacion(): void {
    this.confirmacionVisible = false;
  }

  confirmarPago(): void {
    if (!this.multaPagoActual || this.montoRecibido <= 0) return;
    this.confirmacionVisible = false;
    this.cargando = true;

    this.multaService.pagoParcial(this.multaPagoActual.id, this.montoRecibido).subscribe({
      next: (respuesta: PagoMultaResponse) => {
        this.cargando = false;
        this.modalPagoVisible = false;
        this.multaPagoActual = null;
        this.montoRecibido = 0;
        this.exitoMsg = 'Pago registrado correctamente. Comprobante enviado por correo.';
        if (this.usuarioSeleccionado) {
          this.cargarMultas(this.usuarioSeleccionado.id);
        }
        setTimeout(() => { this.exitoMsg = ''; }, 5000);
      },
      error: () => {
        this.cargando = false;
        this.errorMsg = 'Error al procesar el pago.';
        setTimeout(() => { this.errorMsg = ''; }, 5000);
      }
    });
  }

  // ── Paginación ────────────────────────────────────────
  paginaAnterior(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      if (this.usuarioSeleccionado) this.cargarMultas(this.usuarioSeleccionado.id);
    }
  }

  paginaSiguiente(): void {
    if (this.currentPage < this.totalPages - 1) {
      this.currentPage++;
      if (this.usuarioSeleccionado) this.cargarMultas(this.usuarioSeleccionado.id);
    }
  }

  irAPagina(pagina: number): void {
    this.currentPage = pagina;
    if (this.usuarioSeleccionado) this.cargarMultas(this.usuarioSeleccionado.id);
  }

  get paginasVisibles(): number[] {
    const total = Math.min(this.totalPages, 5);
    const start = Math.max(0, Math.min(this.currentPage - 2, this.totalPages - total));
    return Array.from({ length: total }, (_, i) => start + i);
  }

  // ── Utilidades ────────────────────────────────────────
  getIniciales(): string {
    if (!this.usuarioSeleccionado) return '';
    return this.usuarioSeleccionado.nombreCompleto
      .split(' ')
      .map(p => p.charAt(0))
      .join('')
      .substring(0, 2)
      .toUpperCase();
  }

  claseEstado(estado: string): string {
    switch (estado) {
      case 'ACTIVA': return 'bg-green-100 text-green-700';
      case 'SUSPENDIDA': return 'bg-amber-100 text-amber-700';
      case 'BLOQUEADA': return 'bg-red-100 text-red-700';
      default: return 'bg-surface-container-low text-on-surface-variant';
    }
  }

  etiquetaEstadoCuenta(estado: string): string {
    switch (estado) {
      case 'ACTIVA': return 'Activa';
      case 'SUSPENDIDA': return 'Suspendida';
      case 'BLOQUEADA': return 'Bloqueada';
      default: return estado;
    }
  }

  // ── Anulación ────────────────────────────────────────
  puedeAnular(): boolean {
    return this.authService.hasRole('GERENTE', 'ADMIN');
  }

  abrirModalAnulacion(multa: MultaDetalle): void {
    this.multaAnulacionActual = multa;
    this.motivoAnulacion = '';
    this.modalAnulacionVisible = true;
  }

  cerrarModalAnulacion(): void {
    this.modalAnulacionVisible = false;
    this.multaAnulacionActual = null;
    this.motivoAnulacion = '';
  }

  abrirConfirmacionAnulacion(): void {
    if (!this.motivoAnulacion.trim()) return;
    this.confirmacionAnulacionVisible = true;
  }

  cerrarConfirmacionAnulacion(): void {
    this.confirmacionAnulacionVisible = false;
  }

  confirmarAnulacion(): void {
    if (!this.multaAnulacionActual || !this.motivoAnulacion.trim()) return;
    this.confirmacionAnulacionVisible = false;
    this.cargando = true;

    this.multaService.anular(this.multaAnulacionActual.id, this.motivoAnulacion.trim()).subscribe({
      next: (respuesta: MultaAccionResponse) => {
        this.cargando = false;
        this.modalAnulacionVisible = false;
        this.multaAnulacionActual = null;
        this.motivoAnulacion = '';
        this.exitoMsg = 'Multa anulada correctamente.';
        if (this.usuarioSeleccionado) {
          this.cargarMultas(this.usuarioSeleccionado.id);
        }
        setTimeout(() => { this.exitoMsg = ''; }, 5000);
      },
      error: () => {
        this.cargando = false;
        this.errorMsg = 'Error al anular la multa.';
        setTimeout(() => { this.errorMsg = ''; }, 5000);
      }
    });
  }

  limpiarEstado(): void {
    this.multas = [];
    this.currentPage = 0;
    this.totalPages = 0;
    this.filtroActivo = 'TODOS';
    this.errorMsg = '';
    this.exitoMsg = '';
  }
}
