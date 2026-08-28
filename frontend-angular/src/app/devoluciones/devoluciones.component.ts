import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { PrestamoService } from '../core/services/prestamo.service';
import { DevolucionService } from '../core/services/devolucion.service';
import { LibroService } from '../core/services/libro.service';
import { HistorialPrestamo } from '../core/models/prestamos-gestion.model';
import {
  TipoDano,
  DanoItem,
  DevolucionRequest,
  DevolucionCompletaResponse,
  DevolucionHistorial
} from '../core/models/devoluciones.model';

export interface EvidenciaLocal {
  id: number;
  archivo: File;
  previewUrl: string;
}
import { BuscadorUsuarioComponent } from '../shared/buscador-usuario/buscador-usuario.component';
import { PortadaLibroComponent } from '../shared/portada-libro/portada-libro.component';

@Component({
  standalone: true,
  selector: 'app-devoluciones',
  imports: [CommonModule, FormsModule, BuscadorUsuarioComponent, PortadaLibroComponent],
  templateUrl: './devoluciones.component.html'
})
export class DevolucionesComponent implements OnInit, OnDestroy {

  // ── Préstamos activos del usuario ────────────────────
  prestamosActivos: HistorialPrestamo[] = [];
  cargandoPrestamos: boolean = false;
  prestamoSeleccionado: HistorialPrestamo | null = null;

  // ── Catálogo de tipos de daño ────────────────────────
  tiposDano: TipoDano[] = [];
  cargandoTipos: boolean = false;

  // ── Formulario de devolución ─────────────────────────
  estadoDevolucion: string = 'BUEN_ESTADO';
  descripcionDano: string = '';
  danosSeleccionados: DanoItem[] = [];

  // ── Historial de devoluciones recientes ──────────────
  historial: DevolucionHistorial[] = [];
  cargandoHistorial: boolean = false;

  // ── Estados ──────────────────────────────────────────
  procesando: boolean = false;
  exitoMsg: string = '';
  errorMsg: string = '';

  // ── Resultado de la devolución ──────────────────────
  resultadoDevolucion: DevolucionCompletaResponse | null = null;

  // ── Modal portada ────────────────────────────────────
  portadaModalVisible: boolean = false;
  portadaModalUrl: string | null = null;
  portadaModalCargando: boolean = false;

  // ── Evidencia fotográfica ────────────────────────────
  evidencias: EvidenciaLocal[] = [];
  evidenciaPreviewUrl: string | null = null;
  evidenciaPreviewVisible: boolean = false;
  evidenciaPreviewNombre: string = '';
  maxTamanoEvidenciaMb: number = 2;
  tiposEvidenciaPermitidos: string = 'image/jpeg,image/png,image/webp,image/avif';
  private evidenciaIdCounter: number = 0;

  private destroy$ = new Subject<void>();

  constructor(
    private prestamoService: PrestamoService,
    private devolucionService: DevolucionService,
    private libroService: LibroService
  ) {}

  ngOnInit(): void {
    this.cargarTiposDano();
    this.cargarHistorial();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ── Eventos del BuscadorUsuarioComponent ──────────────
  onCorreoSeleccionado(correo: string): void {
    this.buscarPrestamosPorCorreo(correo);
  }

  onBuscarAhora(correo: string): void {
    this.buscarPrestamosPorCorreo(correo);
  }

  private buscarPrestamosPorCorreo(correo: string): void {
    this.limpiarResultado();
    this.prestamoSeleccionado = null;
    this.cargandoPrestamos = true;
    this.prestamosActivos = [];

    this.prestamoService.buscarUsuarioPorCorreo(correo).subscribe({
      next: (usuario) => {
        this.prestamoService.historial(usuario.id).subscribe({
          next: (historial) => {
            this.prestamosActivos = historial.filter(p =>
              !p.fechaDevolucionReal && p.estadoNombre === 'ACTIVO'
            );
            this.cargandoPrestamos = false;
            if (this.prestamosActivos.length === 0) {
              this.errorMsg = 'No se encontraron préstamos activos para este usuario.';
            }
          },
          error: () => {
            this.cargandoPrestamos = false;
            this.errorMsg = 'Error al buscar préstamos.';
          }
        });
      },
      error: () => {
        this.cargandoPrestamos = false;
        this.errorMsg = 'No se encontró usuario con ese correo.';
      }
    });
  }

  seleccionarPrestamo(prestamo: HistorialPrestamo): void {
    this.prestamoSeleccionado = prestamo;
    this.limpiarResultado();
    this.estadoDevolucion = 'BUEN_ESTADO';
    this.descripcionDano = '';
    this.danosSeleccionados = [];
  }

  // ── Catálogo de tipos de daño ──────────────────────
  private cargarTiposDano(): void {
    this.cargandoTipos = true;
    this.devolucionService.listarTiposDano().subscribe({
      next: (tipos) => {
        this.tiposDano = tipos;
        this.cargandoTipos = false;
      },
      error: () => { this.cargandoTipos = false; }
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
      error: () => { this.cargandoHistorial = false; }
    });
  }

  // ── Cambio de estado de devolución ──────────────────
  onCambioEstado(): void {
    if (this.estadoDevolucion !== 'CON_DANO') {
      this.danosSeleccionados = [];
      this.descripcionDano = '';
    }
  }

  // ── Gestión de daños ────────────────────────────────
  toggleDano(tipoDano: TipoDano, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    if (checked) {
      this.danosSeleccionados.push({
        tipoDanoId: tipoDano.id,
        nombreCustom: null,
        precioCobrado: tipoDano.valor
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

  eliminarDano(index: number): void {
    this.danosSeleccionados.splice(index, 1);
  }

  // ── Cálculos ────────────────────────────────────────
  get totalDano(): number {
    return this.danosSeleccionados.reduce((sum, d) => sum + d.precioCobrado, 0);
  }

  get diasRetraso(): number {
    if (!this.prestamoSeleccionado) return 0;
    const vencimiento = new Date(this.prestamoSeleccionado.fechaDevolucionEstimada);
    const hoy = new Date();
    const diffMs = hoy.getTime() - vencimiento.getTime();
    return Math.max(0, Math.floor(diffMs / (1000 * 60 * 60 * 24)));
  }

  get estaVencido(): boolean {
    return this.diasRetraso > 0;
  }

  // ── Enviar devolución ────────────────────────────────
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
      next: async (resultado) => {
        if (resultado.registroDanoId && this.evidencias.length > 0) {
          await this.subirEvidenciasAlServidor(resultado.registroDanoId);
        }
        this.evidencias.forEach(e => URL.revokeObjectURL(e.previewUrl));
        this.evidencias = [];
        this.procesando = false;
        this.resultadoDevolucion = resultado;
        this.exitoMsg = 'Devolución registrada correctamente.';
        this.prestamoSeleccionado = null;
        this.prestamosActivos = [];
        this.cargarHistorial();
      },
      error: (err) => {
        this.procesando = false;
        this.errorMsg = err.message || 'Error al registrar la devolución.';
      }
    });
  }

  // ── Utilidades ────────────────────────────────────────
  limpiarResultado(): void {
    this.exitoMsg = '';
    this.errorMsg = '';
    this.resultadoDevolucion = null;
  }

  etiquetaEstadoDevolucion(estado: string): string {
    switch (estado) {
      case 'BUEN_ESTADO': return 'Buen estado';
      case 'CON_DANO': return 'Con daño';
      case 'PERDIDO': return 'Perdido';
      default: return estado;
    }
  }

  claseEstadoHistorial(estado: string): string {
    switch (estado) {
      case 'BUEN_ESTADO': return 'text-success';
      case 'CON_DANO': return 'text-warning';
      case 'PERDIDO': return 'text-error';
      default: return 'text-on-surface-variant';
    }
  }

  getNombreDano(dano: DanoItem): string {
    const tipo = this.tiposDano.find(t => t.id === dano.tipoDanoId);
    return tipo?.nombre ?? 'Daño';
  }

  iniciales(nombre: string): string {
    if (!nombre) return '??';
    return nombre.split(' ').map(p => p.charAt(0)).join('').substring(0, 2).toUpperCase();
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

  // ── Modal portada ────────────────────────────────────
  abrirPortada(libroId: number): void {
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
    this.portadaModalUrl = null;
    this.portadaModalVisible = false;
  }

  // ── Evidencia fotográfica ────────────────────────────
  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) return;

    const maxBytes = this.maxTamanoEvidenciaMb * 1024 * 1024;

    for (let i = 0; i < input.files.length; i++) {
      const archivo = input.files[i];
      if (archivo.size > maxBytes) {
        this.errorMsg = `"${archivo.name}" excede el tamaño máximo de ${this.maxTamanoEvidenciaMb} MB.`;
        continue;
      }
      this.evidenciaIdCounter++;
      const evidencia: EvidenciaLocal = {
        id: this.evidenciaIdCounter,
        archivo,
        previewUrl: URL.createObjectURL(archivo)
      };
      this.evidencias.push(evidencia);
    }
    input.value = '';
  }

  abrirPreviewEvidencia(evidencia: EvidenciaLocal): void {
    this.evidenciaPreviewVisible = true;
    this.evidenciaPreviewNombre = evidencia.archivo.name;
    this.evidenciaPreviewUrl = evidencia.previewUrl;
  }

  cerrarPreviewEvidencia(): void {
    this.evidenciaPreviewUrl = null;
    this.evidenciaPreviewVisible = false;
  }

  eliminarEvidencia(evidencia: EvidenciaLocal): void {
    URL.revokeObjectURL(evidencia.previewUrl);
    this.evidencias = this.evidencias.filter(e => e.id !== evidencia.id);
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.evidenciaPreviewVisible) {
      this.cerrarPreviewEvidencia();
    } else if (this.portadaModalVisible) {
      this.cerrarPortada();
    }
  }

  private async subirEvidenciasAlServidor(registroDanoId: number): Promise<void> {
    for (const ev of this.evidencias) {
      try {
        await this.devolucionService.subirEvidencia(registroDanoId, ev.archivo).toPromise();
      } catch (err) {
        console.error('Error subiendo evidencia:', ev.archivo.name, err);
      }
    }
  }
}
