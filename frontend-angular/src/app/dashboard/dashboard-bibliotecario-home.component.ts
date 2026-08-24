import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ReporteService, LibroMasPrestado, ReporteMorosidad } from '../core/services/reporte-gerencial.service';
import { ReservacionService } from '../core/services/reservacion.service';
import { ReservacionHoy } from '../core/models/reservacion.model';

@Component({
  selector: 'app-dashboard-bibliotecario-home',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './dashboard-bibliotecario-home.component.html',
  styles: [`
    .kpi-card { background: linear-gradient(135deg, rgba(0,54,148,0.04), rgba(0,54,148,0)); }
  `]
})
export class DashboardBibliotecarioHomeComponent implements OnInit {
  librosMasPrestados: LibroMasPrestado[] = [];
  usuariosEnMora: ReporteMorosidad[] = [];
  reservacionesHoy: ReservacionHoy[] = [];

  cargandoLibros = true;
  errorLibros = '';
  cargandoMorosidad = true;
  errorMorosidad = '';
  cargandoReservacionesHoy = true;
  errorReservacionesHoy = '';

  readonly barColors = ['#003694', '#2c57c1', '#1e4db7', '#59dbc7', '#76f4e0'];
  readonly barTextColors = ['#003694', '#2c57c1', '#1e4db7', '#006b5f', '#006b5f'];

  get top5PorDeuda(): ReporteMorosidad[] {
    return [...this.usuariosEnMora]
      .sort((a, b) => b.montoTotalAdeudado - a.montoTotalAdeudado)
      .slice(0, 5);
  }

  get maxDeudaUsuario(): number {
    return Math.max(...this.usuariosEnMora.map(u => u.montoTotalAdeudado), 1);
  }

  private maxPrestamos = 1;

  constructor(
    private reporteService: ReporteService,
    private reservacionService: ReservacionService
  ) {}

  ngOnInit(): void {
    this.reporteService.librosMasPrestados().subscribe({
      next: (libros) => {
        this.librosMasPrestados = libros.slice(0, 5);
        this.maxPrestamos = Math.max(...this.librosMasPrestados.map(l => l.totalPrestamos), 1);
        this.cargandoLibros = false;
      },
      error: () => {
        this.errorLibros = 'No se pudo cargar el reporte de libros mas prestados.';
        this.cargandoLibros = false;
      }
    });

    this.reporteService.morosidad().subscribe({
      next: (usuarios) => {
        this.usuariosEnMora = usuarios;
        this.cargandoMorosidad = false;
      },
      error: () => {
        this.errorMorosidad = 'No se pudo cargar el reporte de morosidad.';
        this.cargandoMorosidad = false;
      }
    });

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

  get libroMasPrestado(): LibroMasPrestado | null {
    return this.librosMasPrestados.length > 0 ? this.librosMasPrestados[0] : null;
  }

  get totalPrestamosTop5(): number {
    return this.librosMasPrestados.reduce((sum, l) => sum + l.totalPrestamos, 0);
  }

  get montoTotalAdeudado(): number {
    return this.usuariosEnMora.reduce((sum, u) => sum + u.montoTotalAdeudado, 0);
  }

  maxBarWidth(totalPrestamos: number): number {
    return Math.round((totalPrestamos / this.maxPrestamos) * 460);
  }

  getIniciales(nombre: string, apellido: string): string {
    const n = nombre?.charAt(0) ?? '';
    const a = apellido?.charAt(0) ?? '';
    return `${n}${a}`.toUpperCase() || '??';
  }

  claseSeveridad(dias: number): string {
    if (dias >= 15) return 'bg-error-container text-on-error-container';
    if (dias >= 7) return 'bg-warning/20 text-tertiary';
    return 'bg-success/15 text-success';
  }
}
