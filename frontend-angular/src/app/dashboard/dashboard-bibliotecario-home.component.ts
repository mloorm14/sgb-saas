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

  paginaProximas = 0;
  tamanoPaginaProximas = 10;

  get proximasPaginadas(): ReservacionHoy[] {
    const inicio = this.paginaProximas * this.tamanoPaginaProximas;
    return this.reservacionesHoy.slice(inicio, inicio + this.tamanoPaginaProximas);
  }
  get paginasProximas(): number[] {
    return Array.from({ length: this.totalPaginasProximas }, (_, i) => i);
  }
  get totalPaginasProximas(): number {
    return Math.max(1, Math.ceil(this.reservacionesHoy.length / this.tamanoPaginaProximas));
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

  marcarListaParaRetiro(id: number): void {
    this.reservacionService.cambiarEstado(id, { nuevoEstado: 'LISTA_PARA_RETIRO' }).subscribe({
      next: () => {
        this.reservacionService.reservacionesDeHoy().subscribe({
          next: (reservas) => { this.reservacionesHoy = reservas; },
          error: () => {}
        });
      },
      error: () => {}
    });
  }
}
