import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../core/services/auth.service';
import { environment } from '../../environments/environment';

@Component({
  selector: 'app-multas',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './multas.component.html'
})
export class MultasComponent implements OnInit {
  esLector: boolean = false;
  puedeGestionar: boolean = false; // BIBLIOTECARIO/GERENTE: pagar
  puedeAnular: boolean = false;    // GERENTE/ADMIN: anular

  usuarioIdBusqueda: number | null = null;
  multas: any[] = [];
  totalPages: number = 0;
  currentPage: number = 0;
  pageSize: number = 10;
  cargando: boolean = false;
  errorMsg: string = '';

  // Modal de anulacion
  mostrarModalAnular: boolean = false;
  multaSeleccionadaId: number | null = null;
  motivoAnulacion: string = '';

  private apiUrl = environment.apiUrl + '/v1';

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.puedeGestionar = this.authService.hasRole('BIBLIOTECARIO', 'GERENTE', 'ADMIN');
    this.puedeAnular = this.authService.hasRole('GERENTE', 'ADMIN');
    this.esLector = !this.puedeGestionar;

    if (this.esLector) {
      this.usuarioIdBusqueda = this.authService.getUserId();
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
    this.http.get<any>(
      `${this.apiUrl}/multas/usuario/${this.usuarioIdBusqueda}?page=${this.currentPage}&size=${this.pageSize}&sort=id,desc`
    ).subscribe({
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

  pagarMulta(multaId: number): void {
    if (!confirm('¿Confirmar el pago de esta multa?')) return;
    this.http.post(`${this.apiUrl}/multas/${multaId}/pago`, {}).subscribe({
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
    this.http.post(`${this.apiUrl}/multas/${this.multaSeleccionadaId}/anulacion`, { motivo: this.motivoAnulacion }).subscribe({
      next: () => {
        this.cerrarModalAnular();
        this.cargarPagina();
      },
      error: () => { this.errorMsg = 'Error al anular la multa'; }
    });
  }
}