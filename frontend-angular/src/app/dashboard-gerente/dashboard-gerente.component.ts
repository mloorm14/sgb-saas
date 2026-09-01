import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ReporteService, LibroMasPrestado, ReporteMorosidad } from '../core/services/reporte-gerencial.service';
import { AuthService } from '../core/services/auth.service';
import { ThemeService } from '../core/services/theme.service';
import { MultaService } from '../core/services/multa.service';
import { ResumenFinancieroMultas } from '../core/models/multa.model';
import { AuditoriaService } from '../core/services/auditoria.service';
import { EventoAuditoria } from '../core/models/evento-auditoria.model';

// Dashboard del GERENTE (mockup docs/mockups/rama-b/24-dashboard-gerente.html).
// Secciones con datos reales detrás: "Bienvenida, Gerencia" + "Libros más
// prestados" (GET /prestamos/reportes/libros-mas-prestados, reutiliza
// ReporteService) + "Accesos rápidos" + 3 widgets agregados a pedido de
// Cajas (dashboard GERENTE/ADMIN ampliado, priorizado a 4 métricas
// alcanzables de una lista más larga por el plazo de 2 días):
// resumen financiero (nuevo GET /multas/reportes/resumen-financiero),
// morosidad (reutiliza ReporteService.morosidad(), ya existente) y
// actividad de auditoría reciente (reutiliza AuditoriaService, ya
// existente). Los KPIs numéricos del mockup original ($142.50, 7, 4, 312)
// seguían sin endpoint real hasta ahora; el de recaudado/pendiente sí
// queda cubierto por el nuevo endpoint.
@Component({
  selector: 'app-dashboard-gerente',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './dashboard-gerente.component.html'
})
export class DashboardGerenteComponent implements OnInit {
  librosMasPrestados: LibroMasPrestado[] = [];
  cargando = true;
  error = '';

  // Resumen financiero (multas): recaudado vs pendiente de cobro.
  resumenFinanciero: ResumenFinancieroMultas | null = null;
  cargandoFinanciero = true;
  errorFinanciero = '';

  // Morosidad: la guía original hablaba de una "tasa" de morosidad, pero
  // el endpoint real (verificado en ReporteMorosidadResponseDTO/
  // ReporteMorosidad) no devuelve un porcentaje -- devuelve una lista de
  // usuarios con multas pendientes (top 10 por defecto en el backend, ver
  // PrestamoService.LIMITE_REPORTE_DEFAULT). Se deriva acá un resumen real
  // a partir de esa lista (cantidad + monto total adeudado) en vez de
  // inventar un porcentaje que el backend no respalda.
  usuariosEnMora: ReporteMorosidad[] = [];
  cargandoMorosidad = true;
  errorMorosidad = '';

  // Actividad de auditoría reciente: últimos 5 eventos (lista compacta,
  // no la tabla completa -- para eso está /auditoria).
  eventosAuditoria: EventoAuditoria[] = [];
  cargandoAuditoria = true;
  errorAuditoria = '';

  constructor(
    private reporteService: ReporteService,
    private authService: AuthService,
    public themeService: ThemeService,
    private multaService: MultaService,
    private auditoriaService: AuditoriaService
  ) {}

  ngOnInit(): void {
    // "Libros màs prestados" y "Morosidad" usan endpoints @PreAuthorize
    // hasAnyRole('BIBLIOTECARIO','GERENTE') -- ADMIN no tiene acceso y
    // recibiria 403. Se ocultan para ADMIN (solo GERENTE): no se disparan
    // los requests y no aparecen los widgets, evitando errores en consola.
    if (this.authService.hasRole('GERENTE')) {
      this.reporteService.librosMasPrestados().subscribe({
        next: (libros) => {
          this.librosMasPrestados = libros.slice(0, 5); // top 5, igual que el mockup
          this.cargando = false;
        },
        error: () => {
          this.error = 'No se pudo cargar el reporte de libros más prestados.';
          this.cargando = false;
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
    } else {
      // ADMIN: esos 2 endpoints no existen para su rol, no dispara nada.
      this.cargando = false;
      this.cargandoMorosidad = false;
    }

    this.multaService.resumenFinanciero().subscribe({
      next: (resumen) => {
        this.resumenFinanciero = resumen;
        this.cargandoFinanciero = false;
      },
      error: () => {
        this.errorFinanciero = 'No se pudo cargar el resumen financiero.';
        this.cargandoFinanciero = false;
      }
    });

    this.auditoriaService.listar({ page: 0, size: 5 }).subscribe({
      next: (pagina) => {
        this.eventosAuditoria = pagina.content;
        this.cargandoAuditoria = false;
      },
      error: () => {
        this.errorAuditoria = 'No se pudo cargar la actividad de auditoría.';
        this.cargandoAuditoria = false;
      }
    });
  }

  get montoTotalAdeudado(): number {
    return this.usuariosEnMora.reduce((suma, u) => suma + u.montoTotalAdeudado, 0);
  }

  // El titulo no asume rol: GERENTE ve "Bienvenida, Gerencia" y ADMIN
  // ve "Bienvenida, Administración".
  get tituloBienvenida(): string {
    if (this.authService.hasRole('ADMIN')) return 'Bienvenida, Administración';
    return 'Bienvenida, Gerencia';
  }

  // GERENTE: libros más prestados y morosidad (endpoints exclusivos del rol).
  // ADMIN: esos endpoints no existen -> la vista los oculta.
  get esGerente(): boolean {
    return this.authService.hasRole('GERENTE');
  }
}