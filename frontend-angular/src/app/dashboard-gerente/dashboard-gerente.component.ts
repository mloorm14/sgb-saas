import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ReporteService, LibroMasPrestado } from '../core/services/reporte-gerencial.service';
import { AuthService } from '../core/services/auth.service';

// Dashboard del GERENTE (mockup docs/mockups/rama-b/24-dashboard-gerente.html).
// Solo secciones con datos reales detrás: "Bienvenida, Gerencia" +
// "Libros más prestados" (GET /api/v1/prestamos/reportes/libros-mas-prestados,
// reutiliza ReporteService) + "Accesos rápidos" a rutas que ya existen.
// Los KPIs numéricos del mockup ($142.50, 7, 4, 312) NO se reproducen:
// no tienen endpoint real que los respalde (reportes/uso y morosidad
// existen, pero el KPI mezclaría datos sin contrato verificado).
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

  constructor(
    private reporteService: ReporteService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
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
  }
}