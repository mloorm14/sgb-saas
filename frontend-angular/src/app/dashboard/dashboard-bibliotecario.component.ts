import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

// Dashboard del BIBLIOTECARIO (Rama E §8.1 / Fase 1 E2).
// Pantalla reducida con accesos directos: no hay métricas reales porque
// el backend no expone endpoints de agregados globales (verificado en
// el reporte §5.3). Se amplía cuando exista GET /api/v1/prestamos
// global o equivalentes. Misma decisión que dashboard-gerente cuando
// se omitieron los KPIs numéricos del mockup 24 por falta de contrato.
@Component({
  selector: 'app-dashboard-bibliotecario',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './dashboard-bibliotecario.component.html'
})
export class DashboardBibliotecarioComponent {}
