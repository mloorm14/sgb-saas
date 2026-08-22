import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';
import { PrestamoService } from '../core/services/prestamo.service';
import { DevolucionService } from '../core/services/devolucion.service';
import { UsuarioSugerencia, HistorialPrestamo } from '../core/models/prestamos-gestion.model';
import {
  TipoDano,
  DanoItem,
  DevolucionRequest,
  DevolucionCompletaResponse,
  DevolucionHistorial
} from '../core/models/devoluciones.model';

@Component({
  selector: 'app-devoluciones',
  imports: [CommonModule, FormsModule],
  templateUrl: './devoluciones.component.html'
})
export class DevolucionesComponent implements OnInit, OnDestroy {

  // ── Busqueda por correo (autocompletado) ─────────────
  correoBusqueda: string = '';
  mostrarSugerencias: boolean = false;
  sugerencias: UsuarioSugerencia[] = [];
  buscandoSugerencias: boolean = false;
  placeholderCorreo: string = 'Correo del usuario';
  indiceActivo: number = -1;
  private busqueda$ = new Subject<string>();
  private destroy$ = new Subject<void>();

  // ── Prestamos activos del usuario ────────────────────
  usuarioIdSeleccionado: number | null = null;
  prestamosActivos: HistorialPrestamo[] = [];
  cargandoPrestamos: boolean = false;
  prestamoSeleccionado: HistorialPrestamo | null = null;

  // ── Tipos de dano (catalogo) ────────────────────────
  tiposDano: TipoDano[] = [];
  cargandoTipos: boolean = false;

  // ── Formulario de devolucion ─────────────────────────
  estadoDevolucion: string = 'BUEN_ESTADO';
  descripcionDano: string = '';
  danosSeleccionados: DanoItem[] = [];
  mostrarFormDano: boolean = false;

  // ── Historial de devoluciones recientes ──────────────
  historial: DevolucionHistorial[] = [];
  cargandoHistorial: boolean = false;

  // ── Estados ──────────────────────────────────────────
  procesando: boolean = false;
  exitoMsg: string = '';
  errorMsg: string = '';

  // ── Resultado de la devolucion ──────────────────────
  resultadoDevolucion: DevolucionCompletaResponse | null = null;

  constructor(
    private prestamoService: PrestamoService,
    private devolucionService: DevolucionService
  ) {
    this.busqueda$.pipe(
      debounceTime(1300),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(texto => this.buscarSugerencias(texto));
  }

  ngOnInit(): void {
    this.cargarTiposDano();
    this.cargarHistorial();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.busqueda$.complete();
  }

  // ── Autocompletado de correo ──────────────────────────
  onInputCorreo(): void {
    this.correoBusqueda = this.correoBusqueda.replace(/\s/g, '');
    this.indiceActivo = -1;
    const texto = this.correoBusqueda.trim();
    if (texto.length < 2) {
      this.sugerencias = [];
      this.mostrarSugerencias = false;
      this.placeholderCorreo = 'Correo del usuario';
      return;
    }
    this.busqueda$.next(texto);
  }

  private buscarSugerencias(texto: string): void {
    if (texto.length < 2) {
      this.sugerencias = [];
      this.mostrarSugerencias = false;
      return;
    }
    this.buscandoSugerencias = true;
    this.prestamoService.sugerenciasUsuarios(texto).subscribe({
      next: (lista) => {
        this.sugerencias = lista;
        this.mostrarSugerencias = lista.length > 0;
        this.buscandoSugerencias = false;
        if (lista.length > 0) {
          this.placeholderCorreo = lista[0].correo;
        } else {
          this.placeholderCorreo = 'Correo del usuario';
        }
      },
      error: () => {
        this.sugerencias = [];
        this.mostrarSugerencias = false;
        this.buscandoSugerencias = false;
      }
    });
  }

  onKeydownCorreo(event: KeyboardEvent): void {
    if (!this.mostrarSugerencias || this.sugerencias.length === 0) {
      if (event.key === 'Enter') {
        event.preventDefault();
        this.buscarPrestamos();
      }
      return;
    }
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.indiceActivo = Math.min(this.indiceActivo + 1, this.sugerencias.length - 1);
        this.placeholderCorreo = this.sugerencias[this.indiceActivo].correo;
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.indiceActivo = Math.max(this.indiceActivo - 1, -1);
        this.placeholderCorreo = this.indiceActivo >= 0
          ? this.sugerencias[this.indiceActivo].correo
          : 'Correo del usuario';
        break;
      case 'Tab':
        event.preventDefault();
        if (this.indiceActivo >= 0 && this.indiceActivo < this.sugerencias.length) {
          this.seleccionarSugerencia(this.sugerencias[this.indiceActivo]);
        } else if (this.sugerencias.length > 0) {
          this.seleccionarSugerencia(this.sugerencias[0]);
        }
        break;
      case 'Enter':
        event.preventDefault();
        if (this.indiceActivo >= 0 && this.indiceActivo < this.sugerencias.length) {
          this.seleccionarSugerencia(this.sugerencias[this.indiceActivo]);
        } else {
          this.buscarPrestamos();
        }
        break;
      case 'Escape':
        this.mostrarSugerencias = false;
        this.indiceActivo = -1;
        break;
    }
  }

  seleccionarSugerencia(sugerencia: UsuarioSugerencia): void {
    this.correoBusqueda = sugerencia.correo;
    this.usuarioIdSeleccionado = sugerencia.id;
    this.sugerencias = [];
    this.mostrarSugerencias = false;
    this.placeholderCorreo = sugerencia.correo;
    this.buscarPrestamos();
  }

  // ── Buscar prestamos activos del usuario ────────────
  buscarPrestamos(): void {
    const usuarioId = this.usuarioIdSeleccionado;
    if (!usuarioId) return;

    this.limpiarResultado();
    this.prestamoSeleccionado = null;
    this.cargandoPrestamos = true;
    this.prestamosActivos = [];

    this.prestamoService.historial(usuarioId).subscribe({
      next: (historial) => {
        this.prestamosActivos = historial.filter(p =>
          !p.fechaDevolucionReal && p.estadoNombre === 'ACTIVO'
        );
        this.cargandoPrestamos = false;
        if (this.prestamosActivos.length === 0) {
          this.errorMsg = 'No se encontraron prestamos activos para este usuario.';
        }
      },
      error: () => {
        this.cargandoPrestamos = false;
        this.errorMsg = 'Error al buscar prestamos.';
      }
    });
  }

  seleccionarPrestamo(prestamo: HistorialPrestamo): void {
    this.prestamoSeleccionado = prestamo;
    this.limpiarResultado();
    this.estadoDevolucion = 'BUEN_ESTADO';
    this.descripcionDano = '';
    this.danosSeleccionados = [];
    this.mostrarFormDano = false;
  }

  // ── Catalogo de tipos de dano ──────────────────────
  private cargarTiposDano(): void {
    this.cargandoTipos = true;
    this.devolucionService.listarTiposDano().subscribe({
      next: (tipos) => {
        this.tiposDano = tipos;
        this.cargandoTipos = false;
      },
      error: () => {
        this.cargandoTipos = false;
      }
    });
  }

  // ── Historial de devoluciones recientes ──────────────
  private cargarHistorial(): void {
    this.cargandoHistorial = true;
    this.devolucionService.historialDevoluciones().subscribe({
      next: (historial) => {
        this.historial = historial;
        this.cargandoHistorial = false;
      },
      error: () => {
        this.cargandoHistorial = false;
      }
    });
  }

  // ── Cambio de estado de devolucion ──────────────────
  onCambioEstado(): void {
    this.mostrarFormDano = this.estadoDevolucion === 'CON_DANO';
    if (this.estadoDevolucion !== 'CON_DANO') {
      this.danosSeleccionados = [];
      this.descripcionDano = '';
    }
  }

  // ── Gestion de danos ────────────────────────────────
  toggleDano(tipoDano: TipoDano, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    if (checked) {
      this.danosSeleccionados.push({
        tipoDanoId: tipoDano.id,
        nombreCustom: null,
        precioCobrado: tipoDano.precio
      });
    } else {
      this.danosSeleccionados = this.danosSeleccionados.filter(
        d => d.tipoDanoId !== tipoDano.id
      );
    }
  }

  isDanoSeleccionado(tipoDanoId: number): boolean {
    return this.danosSeleccionados.some(d => d.tipoDanoId === tipoDanoId);
  }

  agregarDanoCustom(): void {
    this.danosSeleccionados.push({
      tipoDanoId: null,
      nombreCustom: '',
      precioCobrado: 0
    });
  }

  eliminarDanoCustom(index: number): void {
    this.danosSeleccionados.splice(index, 1);
  }

  // ── Calcular totales ────────────────────────────────
  get totalDano(): number {
    return this.danosSeleccionados.reduce((sum, d) => sum + d.precioCobrado, 0);
  }

  get montoTotalMulta(): number {
    return this.totalDano;
  }

  // ── Enviar devolucion ────────────────────────────────
  registrarDevolucion(): void {
    if (!this.prestamoSeleccionado) return;

    this.procesando = true;
    this.errorMsg = '';
    this.limpiarResultado();

    const dto: DevolucionRequest = {
      estadoDevolucion: this.estadoDevolucion,
      descripcion: this.descripcionDano || null,
      danos: this.estadoDevolucion === 'CON_DANO' && this.danosSeleccionados.length > 0
        ? this.danosSeleccionados
        : null
    };

    this.devolucionService.registrarDevolucion(
      this.prestamoSeleccionado.prestamoId, dto
    ).subscribe({
      next: (resultado) => {
        this.procesando = false;
        this.resultadoDevolucion = resultado;
        this.exitoMsg = 'Devolucion registrada correctamente.';
        this.prestamoSeleccionado = null;
        this.correoBusqueda = '';
        this.prestamosActivos = [];
        this.cargarHistorial();
      },
      error: (err) => {
        this.procesando = false;
        this.errorMsg = err.message || 'Error al registrar la devolucion.';
      }
    });
  }

  // ── Utilidades ────────────────────────────────────────
  limpiarResultado(): void {
    this.exitoMsg = '';
    this.errorMsg = '';
    this.resultadoDevolucion = null;
  }

  claseEstadoDevolucion(estado: string): string {
    switch (estado) {
      case 'BUEN_ESTADO': return 'text-success';
      case 'CON_DANO': return 'text-warning';
      case 'PERDIDO': return 'text-error';
      default: return '';
    }
  }

  etiquetaEstadoDevolucion(estado: string): string {
    switch (estado) {
      case 'BUEN_ESTADO': return 'Buen estado';
      case 'CON_DANO': return 'Con dano';
      case 'PERDIDO': return 'Perdido';
      default: return estado;
    }
  }

  fechaRelativa(fechaStr: string): string {
    if (!fechaStr) return '-';
    const fecha = new Date(fechaStr);
    const ahora = new Date();
    const diffMs = ahora.getTime() - fecha.getTime();
    const diffDias = Math.floor(diffMs / (1000 * 60 * 60 * 24));
    if (diffDias === 0) return 'Hoy';
    if (diffDias === 1) return 'Ayer';
    return `Hace ${diffDias} dias`;
  }

  private obtainInicial(nombre: string): string {
    if (!nombre) return '?';
    return nombre.charAt(0).toUpperCase();
  }
}
