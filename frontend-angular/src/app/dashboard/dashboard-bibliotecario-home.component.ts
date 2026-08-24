import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ReporteService, LibroMasPrestado, ReporteMorosidad } from '../core/services/reporte-gerencial.service';

@Component({
  selector: 'app-dashboard-bibliotecario-home',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <main class="max-w-container-max mx-auto px-md md:px-lg py-lg">

      <div class="mb-lg">
        <h1 class="font-headline-lg-mobile md:font-headline-lg text-headline-lg-mobile md:text-headline-lg text-on-background mb-xs">
          Bienvenido, Bibliotecario
        </h1>
        <p class="font-body-sm text-body-sm text-on-surface-variant">
          Resumen del estado actual de la biblioteca — datos en vivo
        </p>
      </div>

      <!-- KPIs -->
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-md mb-lg">
        <div class="kpi-card rounded-xl border border-outline-variant p-lg">
          <div class="flex items-center justify-between mb-sm">
            <span class="material-symbols-outlined text-[26px] text-primary">star</span>
          </div>
          <p class="font-label-sm text-[11px] text-on-surface-variant uppercase tracking-wider mb-1">Libro más prestado</p>
          @if (cargandoLibros) {
            <div class="h-6 w-32 bg-surface-container-high rounded animate-pulse"></div>
          } @else if (errorLibros) {
            <p class="font-body-sm text-[12px] text-on-surface-variant mt-1">N/D</p>
          } @else {
            <p class="font-headline-md text-[17px] text-on-background leading-tight truncate">{{ libroMasPrestado?.titulo ?? 'N/D' }}</p>
            <p class="font-body-sm text-[12px] text-on-surface-variant mt-1">{{ libroMasPrestado?.totalPrestamos ?? 0 }} prestamos</p>
          }
        </div>

        <div class="kpi-card rounded-xl border border-outline-variant p-lg">
          <div class="flex items-center justify-between mb-sm">
            <span class="material-symbols-outlined text-[26px] text-secondary">menu_book</span>
          </div>
          <p class="font-label-sm text-[11px] text-on-surface-variant uppercase tracking-wider mb-1">Prestamos (Top 5)</p>
          @if (cargandoLibros) {
            <div class="h-8 w-16 bg-surface-container-high rounded animate-pulse"></div>
          } @else {
            <p class="font-headline-lg text-[28px] text-on-background leading-tight">{{ totalPrestamosTop5 }}</p>
            <p class="font-body-sm text-[12px] text-on-surface-variant mt-1">ultimos 30 dias</p>
          }
        </div>

        <div class="rounded-xl border border-outline-variant bg-white p-lg">
          <div class="flex items-center justify-between mb-sm">
            <span class="material-symbols-outlined text-[26px] text-warning">group</span>
          </div>
          <p class="font-label-sm text-[11px] text-on-surface-variant uppercase tracking-wider mb-1">Usuarios en mora</p>
          @if (cargandoMorosidad) {
            <div class="h-8 w-8 bg-surface-container-high rounded animate-pulse"></div>
          } @else {
            <p class="font-headline-lg text-[28px] text-on-background leading-tight">{{ usuariosEnMora.length }}</p>
            <p class="font-body-sm text-[12px] text-on-surface-variant mt-1">con multas pendientes</p>
          }
        </div>

        <div class="rounded-xl border border-outline-variant bg-white p-lg">
          <div class="flex items-center justify-between mb-sm">
            <span class="material-symbols-outlined text-[26px] text-error">payments</span>
          </div>
          <p class="font-label-sm text-[11px] text-on-surface-variant uppercase tracking-wider mb-1">Monto adeudado</p>
          @if (cargandoMorosidad) {
            <div class="h-8 w-20 bg-surface-container-high rounded animate-pulse"></div>
          } @else {
            <p class="font-headline-lg text-[28px] text-error leading-tight">\${{ montoTotalAdeudado.toFixed(2) }}</p>
            <p class="font-body-sm text-[12px] text-on-surface-variant mt-1">pendiente de cobro</p>
          }
        </div>
      </div>

      <!-- Fila de graficas -->
      <div class="grid lg:grid-cols-5 gap-lg mb-lg">

        <!-- Grafica de barras: Top 5 libros mas prestados -->
        <div class="lg:col-span-3 rounded-xl border border-outline-variant bg-white p-lg">
          <div class="flex items-center justify-between mb-lg">
            <h2 class="font-headline-md text-headline-md text-on-background">Libros mas prestados</h2>
            <span class="font-label-sm text-[11px] text-on-surface-variant px-sm py-1 rounded-full bg-surface-container-low">Top 5</span>
          </div>

          @if (cargandoLibros) {
            <div class="space-y-md">
              @for (i of [1,2,3,4,5]; track i) {
                <div class="h-5 bg-surface-container-high rounded animate-pulse" [style.width.%]="100 - i * 10"></div>
              }
            </div>
          } @else if (errorLibros) {
            <p class="font-body-sm text-body-sm text-on-surface-variant text-center py-lg">{{ errorLibros }}</p>
          } @else if (librosMasPrestados.length === 0) {
            <p class="font-body-sm text-body-sm text-on-surface-variant text-center py-lg">No hay datos disponibles.</p>
          } @else {
            <svg viewBox="0 0 500 220" class="w-full h-auto" role="img" aria-label="Grafico de barras de libros mas prestados">
              @for (libro of librosMasPrestados; track libro.libroId; let i = $index) {
                <text x="0" [attr.y]="i * 44 + 16" class="fill-on-surface" font-size="12" font-family="Inter">{{ libro.titulo.length > 30 ? libro.titulo.substring(0, 30) + '...' : libro.titulo }}</text>
                <rect x="0" [attr.y]="i * 44 + 22" [attr.width]="maxBarWidth(libro.totalPrestamos)" height="20" rx="4" [attr.fill]="barColors[i % barColors.length]"/>
                <text [attr.x]="maxBarWidth(libro.totalPrestamos) + 6" [attr.y]="i * 44 + 37" font-size="12" font-family="Inter" font-weight="600" [attr.fill]="barTextColors[i % barTextColors.length]">{{ libro.totalPrestamos }}</text>
              }
            </svg>
          }

          <a routerLink="/dashboard-bibliotecario/reportes" class="inline-flex items-center gap-xs mt-md font-label-sm text-label-sm text-primary hover:underline">
            Ver reporte completo <span class="material-symbols-outlined text-[16px]">arrow_forward</span>
          </a>
        </div>

        <!-- Donut: distribucion del monto adeudado por usuario en mora -->
        <div class="lg:col-span-2 rounded-xl border border-outline-variant bg-white p-lg flex flex-col">
          <h2 class="font-headline-md text-headline-md text-on-background mb-md">Deuda por usuario</h2>

          @if (cargandoMorosidad) {
            <div class="flex-1 flex items-center justify-center">
              <div class="w-40 h-40 rounded-full bg-surface-container-high animate-pulse"></div>
            </div>
          } @else if (errorMorosidad) {
            <p class="font-body-sm text-body-sm text-on-surface-variant text-center py-lg">{{ errorMorosidad }}</p>
          } @else if (usuariosEnMora.length === 0) {
            <div class="flex-1 flex items-center justify-center">
              <p class="font-body-sm text-body-sm text-on-surface-variant text-center">No hay usuarios en mora.</p>
            </div>
          } @else {
            <div class="flex items-center justify-center flex-1">
              <svg viewBox="0 0 120 120" class="w-40 h-40 -rotate-90">
                <circle cx="60" cy="60" r="50" fill="none" stroke="#e6eeff" stroke-width="16"/>
                @for (seg of donutSegments; track seg.offset) {
                  <circle cx="60" cy="60" r="50" fill="none" [attr.stroke]="seg.color" stroke-width="16"
                    [attr.stroke-dasharray]="seg.dasharray"
                    [attr.stroke-dashoffset]="seg.offset"
                    stroke-linecap="round"/>
                }
              </svg>
            </div>

            <div class="space-y-xs mt-md">
              @for (entry of donutLegend; track entry.label) {
                <div class="flex items-center justify-between">
                  <span class="flex items-center gap-xs font-body-sm text-[12px] text-on-surface">
                    <span class="w-2.5 h-2.5 rounded-full inline-block" [style.background]="entry.color"></span>
                    {{ entry.label }}
                  </span>
                  <span class="font-label-sm text-[12px] font-semibold text-on-surface">\${{ entry.amount.toFixed(2) }}</span>
                </div>
              }
            </div>
          }
        </div>
      </div>

      <!-- Tabla: usuarios en mora -->
      <div class="rounded-xl border border-outline-variant bg-white p-lg">
        <h2 class="font-headline-md text-headline-md text-on-background mb-md">Usuarios en mora</h2>

        @if (cargandoMorosidad) {
          <div class="space-y-sm">
            @for (i of [1,2,3]; track i) {
              <div class="h-12 bg-surface-container-high rounded animate-pulse"></div>
            }
          </div>
        } @else if (errorMorosidad) {
          <p class="font-body-sm text-body-sm text-on-surface-variant text-center py-lg">{{ errorMorosidad }}</p>
        } @else if (usuariosEnMora.length === 0) {
          <p class="font-body-sm text-body-sm text-on-surface-variant text-center py-lg">No hay usuarios en mora.</p>
        } @else {
          <div class="overflow-x-auto">
            <table class="w-full text-left">
              <thead>
                <tr class="font-label-sm text-[11px] text-on-surface-variant uppercase tracking-wider border-b border-outline-variant">
                  <th class="pb-sm pr-md">Usuario</th>
                  <th class="pb-sm pr-md">Multas pendientes</th>
                  <th class="pb-sm pr-md">Dias de atraso (prom.)</th>
                  <th class="pb-sm">Monto adeudado</th>
                </tr>
              </thead>
              <tbody class="font-body-sm text-body-sm text-on-surface">
                @for (u of usuariosEnMora; track u.usuarioId) {
                  <tr class="border-b border-outline-variant/50">
                    <td class="py-sm pr-md">
                      <div class="flex items-center gap-sm">
                        <div class="w-8 h-8 rounded-full bg-primary/10 flex items-center justify-center text-primary font-bold text-[12px]">
                          {{ getIniciales(u.nombre, u.apellido) }}
                        </div>
                        <div>
                          <p class="font-medium">{{ u.nombre }} {{ u.apellido }}</p>
                          <p class="font-body-sm text-[11px] text-on-surface-variant">{{ u.correo }}</p>
                        </div>
                      </div>
                    </td>
                    <td class="py-sm pr-md">{{ u.cantidadMultasPendientes }}</td>
                    <td class="py-sm pr-md">
                      <span class="inline-flex items-center px-sm py-1 rounded-full font-label-sm text-[11px] font-semibold"
                        [class]="claseSeveridad(u.diasAtrasoPromedio)">
                        {{ u.diasAtrasoPromedio | number:'1.0-0' }} dias
                      </span>
                    </td>
                    <td class="py-sm font-semibold text-error">\${{ u.montoTotalAdeudado.toFixed(2) }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          <a routerLink="/dashboard-bibliotecario/multas" class="inline-flex items-center gap-xs mt-md font-label-sm text-label-sm text-primary hover:underline">
            Ver reporte de morosidad completo <span class="material-symbols-outlined text-[16px]">arrow_forward</span>
          </a>
        }
      </div>

    </main>
  `,
  styles: [`
    .kpi-card { background: linear-gradient(135deg, rgba(0,54,148,0.04), rgba(0,54,148,0)); }
  `]
})
export class DashboardBibliotecarioHomeComponent implements OnInit {
  librosMasPrestados: LibroMasPrestado[] = [];
  usuariosEnMora: ReporteMorosidad[] = [];

  cargandoLibros = true;
  errorLibros = '';
  cargandoMorosidad = true;
  errorMorosidad = '';

  readonly barColors = ['#003694', '#2c57c1', '#1e4db7', '#59dbc7', '#76f4e0'];
  readonly barTextColors = ['#003694', '#2c57c1', '#1e4db7', '#006b5f', '#006b5f'];
  readonly donutColors = ['#ba1a1a', '#fec004', '#2c57c1', '#006b5f', '#76f4e0', '#1e4db7', '#fabd00', '#59dbc7'];

  private maxPrestamos = 1;

  constructor(private reporteService: ReporteService) {}

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

  get donutSegments(): { color: string; dasharray: string; offset: string }[] {
    if (this.usuariosEnMora.length === 0) return [];
    const circ = 2 * Math.PI * 50; // ~314
    const total = this.montoTotalAdeudado || 1;
    let cumulative = 0;
    return this.usuariosEnMora.slice(0, 8).map((u, i) => {
      const pct = u.montoTotalAdeudado / total;
      const dash = pct * circ;
      const offset = -(cumulative / total) * circ;
      cumulative += u.montoTotalAdeudado;
      return {
        color: this.donutColors[i % this.donutColors.length],
        dasharray: `${dash} ${circ}`,
        offset: `${offset}`
      };
    });
  }

  get donutLegend(): { label: string; amount: number; color: string }[] {
    if (this.usuariosEnMora.length === 0) return [];
    const maxLegend = 3;
    const sorted = [...this.usuariosEnMora].sort((a, b) => b.montoTotalAdeudado - a.montoTotalAdeudado);
    const shown = sorted.slice(0, maxLegend);
    const remaining = sorted.slice(maxLegend);
    const result: { label: string; amount: number; color: string }[] = shown.map((u, i) => ({
      label: `${u.nombre} ${u.apellido}`,
      amount: u.montoTotalAdeudado,
      color: this.donutColors[i % this.donutColors.length]
    }));
    if (remaining.length > 0) {
      const sum = remaining.reduce((s, u) => s + u.montoTotalAdeudado, 0);
      result.push({
        label: `otros (${remaining.length})`,
        amount: sum,
        color: this.donutColors[shown.length % this.donutColors.length]
      });
    }
    return result;
  }
}
