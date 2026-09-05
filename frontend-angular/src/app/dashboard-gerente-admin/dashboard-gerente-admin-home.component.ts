import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ReporteService, LibroMasPrestado, ReporteMorosidad } from '../core/services/reporte-gerencial.service';
import { ReservacionService } from '../core/services/reservacion.service';
import { AuthService } from '../core/services/auth.service';
import { ReservacionHoy } from '../core/models/reservacion.model';
import { ReservacionesHoyCardComponent } from '../shared/reservaciones-hoy-card/reservaciones-hoy-card.component';

@Component({
  selector: 'app-dashboard-gerente-admin-home',
  standalone: true,
  imports: [CommonModule, FormsModule, ReservacionesHoyCardComponent],
  templateUrl: './dashboard-gerente-admin-home.component.html',
  styles: [`
    .kpi-card { background: linear-gradient(135deg, rgba(0,54,148,0.04), rgba(0,54,148,0)); }
  `]
})
export class DashboardGerenteAdminHomeComponent implements OnInit {
  librosMasPrestados: LibroMasPrestado[] = [];
  usuariosEnMora: ReporteMorosidad[] = [];
  reservacionesHoy: ReservacionHoy[] = [];

  cargandoLibros = true;
  errorLibros = '';
  cargandoMorosidad = true;
  errorMorosidad = '';
  cargandoReservacionesHoy = true;
  errorReservacionesHoy = '';

  limiteLibros = 5;

  // Paginacion tabla usuarios con deudas
  paginaDeudas = 0;
  tamanoPaginaDeudas = 10;

  readonly barColors = ['#003694', '#2c57c1', '#1e4db7', '#59dbc7', '#76f4e0'];
  readonly barTextColors = ['#003694', '#2c57c1', '#1e4db7', '#006b5f', '#006b5f'];

  get top10PorDeuda(): ReporteMorosidad[] {
    return [...this.usuariosEnMora]
      .sort((a, b) => b.montoTotalAdeudado - a.montoTotalAdeudado)
      .slice(0, 10);
  }

  get deudasTotalPages(): number {
    return Math.max(1, Math.ceil(this.usuariosEnMora.length / this.tamanoPaginaDeudas));
  }

  get deudasPaginadas(): ReporteMorosidad[] {
    const inicio = this.paginaDeudas * this.tamanoPaginaDeudas;
    return this.usuariosEnMora.slice(inicio, inicio + this.tamanoPaginaDeudas);
  }

  get deudasPaginasVisibles(): number[] {
    const ventana = 4;
    let inicio = Math.max(0, this.paginaDeudas - 1);
    let fin = Math.min(this.deudasTotalPages, inicio + ventana);
    if (fin - inicio < ventana) inicio = Math.max(0, fin - ventana);
    return Array.from({ length: fin - inicio }, (_, i) => inicio + i);
  }

  get puedeDeudasAnterior(): boolean {
    return this.paginaDeudas > 0;
  }

  get puedeDeudasSiguiente(): boolean {
    return this.paginaDeudas < this.deudasTotalPages - 1;
  }

  irADeudasPagina(pagina: number): void {
    if (pagina < 0 || pagina >= this.deudasTotalPages || pagina === this.paginaDeudas) return;
    this.paginaDeudas = pagina;
  }

  paginaAnteriorDeudas(): void {
    if (this.puedeDeudasAnterior) this.paginaDeudas--;
  }

  paginaSiguienteDeudas(): void {
    if (this.puedeDeudasSiguiente) this.paginaDeudas++;
  }

  cambiarTamanoDeudas(tamano: number): void {
    this.tamanoPaginaDeudas = Number(tamano);
    this.paginaDeudas = 0;
  }

  get maxDeudaUsuario(): number {
    return Math.max(...this.usuariosEnMora.map(u => u.montoTotalAdeudado), 1);
  }

  private maxPrestamos = 1;

  constructor(
    private reporteService: ReporteService,
    private reservacionService: ReservacionService,
    private authService: AuthService,
    private router: Router
  ) {}

  get tituloBienvenida(): string {
    if (this.authService.hasRole('ADMIN')) return 'Bienvenido, Administración';
    return 'Bienvenido, Gerencia';
  }

  ngOnInit(): void {
    this.cambiarLimiteLibros(this.limiteLibros);

    this.reporteService.morosidad().subscribe({
      next: (res: any) => {
        const usuarios = Array.isArray(res) ? res : res.content ?? [];
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

  cambiarLimiteLibros(limite: number): void {
    this.limiteLibros = limite;
    this.cargandoLibros = true;
    this.reporteService.librosMasPrestados(undefined, undefined, limite).subscribe({
      next: (libros) => {
        this.librosMasPrestados = libros;
        this.maxPrestamos = Math.max(...libros.map(l => l.totalPrestamos), 1);
        this.cargandoLibros = false;
      },
      error: () => {
        this.errorLibros = 'No se pudo cargar el reporte de libros más prestados.';
        this.cargandoLibros = false;
      }
    });
  }

  irAInventario(): void {
    this.router.navigate(['/dashboard-admin/libros']);
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

  get totalPrestamosTopN(): number {
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
