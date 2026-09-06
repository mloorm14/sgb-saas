import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReservacionService } from '../core/services/reservacion.service';
import { ReservacionHoy } from '../core/models/reservacion.model';
import { ReservacionesHoyCardComponent } from '../shared/reservaciones-hoy-card/reservaciones-hoy-card.component';

@Component({
  selector: 'app-dashboard-bibliotecario-home',
  standalone: true,
  imports: [CommonModule, FormsModule, ReservacionesHoyCardComponent],
  templateUrl: './dashboard-bibliotecario-home.component.html'
})
export class DashboardBibliotecarioHomeComponent implements OnInit {
  reservacionesHoy: ReservacionHoy[] = [];
  cargandoReservacionesHoy = true;
  errorReservacionesHoy = '';

  reservacionesProximasLista: ReservacionHoy[] = [];
  cargandoReservacionesProximas = true;
  errorReservacionesProximas = '';

  paginaProximas = 0;
  tamanoPaginaProximas = 10;

  get proximasPaginadas(): ReservacionHoy[] {
    const inicio = this.paginaProximas * this.tamanoPaginaProximas;
    return this.reservacionesProximasLista.slice(inicio, inicio + this.tamanoPaginaProximas);
  }
  get paginasProximas(): number[] {
    return Array.from({ length: this.totalPaginasProximas }, (_, i) => i);
  }
  get totalPaginasProximas(): number {
    return Math.max(1, Math.ceil(this.reservacionesProximasLista.length / this.tamanoPaginaProximas));
  }
  get puedeAnteriorProximas(): boolean { return this.paginaProximas > 0; }
  get puedeSiguienteProximas(): boolean { return this.paginaProximas < this.totalPaginasProximas - 1; }
  irAPaginaProximas(p: number): void {
    if (p < 0 || p >= this.totalPaginasProximas || p === this.paginaProximas) return;
    this.paginaProximas = p;
  }
  paginaAnteriorProximas(): void { if (this.puedeAnteriorProximas) this.paginaProximas--; }
  paginaSiguienteProximas(): void { if (this.puedeSiguienteProximas) this.paginaProximas++; }
  cambiarTamanoProximas(n: number): void { this.tamanoPaginaProximas = Number(n); this.paginaProximas = 0; }

  constructor(private reservacionService: ReservacionService) {}

  ngOnInit(): void {
    this.cargarHoy();
    this.cargarProximas();
  }

  cargarHoy(): void {
    this.reservacionService.reservacionesDeHoy().subscribe({
      next: (reservas) => {
        this.reservacionesHoy = reservas;
        this.cargandoReservacionesHoy = false;
      },
      error: () => {
        this.errorReservacionesHoy = 'No se pudieron cargar las reservaciones de hoy.';
        this.cargandoReservacionesHoy = false;
      }
    });
  }

  cargarProximas(): void {
    this.reservacionService.reservacionesProximas().subscribe({
      next: (reservas) => {
        this.reservacionesProximasLista = reservas;
        this.cargandoReservacionesProximas = false;
      },
      error: () => {
        this.errorReservacionesProximas = 'No se pudieron cargar las reservaciones próximas.';
        this.cargandoReservacionesProximas = false;
      }
    });
  }

  marcarListaParaRetiro(id: number): void {
    this.reservacionService.cambiarEstado(id, { nuevoEstado: 'LISTA_PARA_RETIRO' }).subscribe({
      next: () => {
        this.cargarHoy();
        this.cargarProximas();
      },
      error: () => {}
    });
  }
}
