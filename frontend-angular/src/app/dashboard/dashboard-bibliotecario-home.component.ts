import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReservacionService } from '../core/services/reservacion.service';
import { ReservacionHoy } from '../core/models/reservacion.model';

@Component({
  selector: 'app-dashboard-bibliotecario-home',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard-bibliotecario-home.component.html'
})
export class DashboardBibliotecarioHomeComponent implements OnInit {
  reservacionesHoy: ReservacionHoy[] = [];
  cargandoReservacionesHoy = true;
  errorReservacionesHoy = '';

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
