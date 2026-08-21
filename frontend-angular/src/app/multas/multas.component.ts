import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../core/services/auth.service';
import { MultaService } from '../core/services/multa.service';
import { Multa } from '../core/models/multa.model';

@Component({
    selector: 'app-multas',
    imports: [CommonModule, FormsModule],
    templateUrl: './multas.component.html'
})
export class MultasComponent implements OnInit {
  esLector: boolean = false;
  puedeGestionar: boolean = false; // BIBLIOTECARIO/GERENTE: pagar
  puedeAnular: boolean = false;    // GERENTE/ADMIN: anular

  usuarioIdBusqueda: number | null = null;
  multas: Multa[] = [];
  totalPages: number = 0;
  currentPage: number = 0;
  pageSize: number = 10;
  cargando: boolean = false;
  errorMsg: string = '';

  // Modal de anulacion
  mostrarModalAnular: boolean = false;
  multaSeleccionadaId: number | null = null;
  motivoAnulacion: string = '';

  // Catálogo local de estados de multa (ids desde db/seed.sql: 1=PENDIENTE, 2=PAGADA, 3=ANULADA)
  readonly estadosMulta: Record<number, string> = {
    1: 'Pendiente',
    2: 'Pagada',
    3: 'Anulada'
  };

  constructor(
    private multaService: MultaService,
    private authService: AuthService,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.puedeGestionar = this.authService.hasRole('BIBLIOTECARIO', 'GERENTE', 'ADMIN');
    this.puedeAnular = this.authService.hasRole('GERENTE', 'ADMIN');
    this.esLector = !this.puedeGestionar;

    if (this.esLector) {
      this.usuarioIdBusqueda = this.authService.getUserId();
      this.cargarPagina();
      return;
    }

    // Llegada desde Préstamos ("Gestionar Multas"): prefiltra por el usuario.
    const usuarioIdParam = Number(this.route.snapshot.queryParamMap.get('usuarioId'));
    if (Number.isInteger(usuarioIdParam) && usuarioIdParam > 0) {
      this.usuarioIdBusqueda = usuarioIdParam;
      this.cargarPagina();
    }
  }

  buscarMultas(): void {
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
    this.multaService.listarPorUsuario(this.usuarioIdBusqueda!, {
      page: this.currentPage,
      size: this.pageSize,
      sort: 'id,desc'
    }).subscribe({
      next: (data) => {
        this.multas = data.content;
        this.totalPages = data.totalPages;
        this.cargando = false;
      },
      error: () => {
        this.errorMsg = 'Error al buscar las multas';
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

  // Devuelve la clase Tailwind para el badge de estado, coherente con
  // el patrón de ReservacionesComponent.claseEstadoReservacion
  claseEstadoMulta(estadoId: number): string {
    switch (estadoId) {
      case 1: return 'bg-tertiary-fixed text-on-tertiary-fixed';      // Pendiente
      case 2: return 'bg-secondary-container text-on-secondary-container'; // Pagada
      case 3: return 'bg-surface-container-low text-on-surface-variant';   // Anulada
      default: return 'bg-surface-container-low text-on-surface-variant';
    }
  }

  // Formato corto de fecha ISO (OffsetDateTime), igual que en otros componentes
  formatearFecha(iso: string): string {
    if (!iso) return '—';
    const fecha = new Date(iso);
    const meses = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
    return `${fecha.getDate()} ${meses[fecha.getMonth()]} ${fecha.getFullYear()}`;
  }

  // Paginación numerada (mismo patrón que libros/reservaciones)
  get paginasVisibles(): number[] {
    if (this.totalPages <= 5) {
      return Array.from({ length: this.totalPages }, (_, i) => i);
    }
    const inicio = Math.max(0, Math.min(this.currentPage - 2, this.totalPages - 5));
    return Array.from({ length: 5 }, (_, i) => inicio + i);
  }

  irAPagina(pagina: number): void {
    if (pagina >= 0 && pagina < this.totalPages && pagina !== this.currentPage) {
      this.currentPage = pagina;
      this.cargarPagina();
    }
  }

  pagarMulta(multaId: number): void {
    if (!confirm('¿Confirmar el pago de esta multa?')) return;
    this.multaService.pagar(multaId).subscribe({
      next: () => { this.cargarPagina(); },
      error: () => { this.errorMsg = 'Error al procesar el pago'; }
    });
  }

  abrirModalAnular(multaId: number): void {
    this.multaSeleccionadaId = multaId;
    this.motivoAnulacion = '';
    this.mostrarModalAnular = true;
  }

  cerrarModalAnular(): void {
    this.mostrarModalAnular = false;
    this.multaSeleccionadaId = null;
    this.motivoAnulacion = '';
  }

  confirmarAnulacion(): void {
    if (!this.motivoAnulacion.trim() || !this.multaSeleccionadaId) return;
    this.multaService.anular(this.multaSeleccionadaId, this.motivoAnulacion).subscribe({
      next: () => {
        this.cerrarModalAnular();
        this.cargarPagina();
      },
      error: () => { this.errorMsg = 'Error al anular la multa'; }
    });
  }
}