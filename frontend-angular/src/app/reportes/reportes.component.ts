import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReporteService, LibroMasPrestadoDetallado, ReporteMorosidad } from '../core/services/reporte-gerencial.service';

@Component({
  selector: 'app-reportes',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './reportes.component.html'
})
export class ReportesComponent implements OnInit {
  libros: LibroMasPrestadoDetallado[] = [];
  morosos: ReporteMorosidad[] = [];

  fechaDesde = '';
  fechaHasta = '';
  limiteTop = 10;

  cargando = false;
  errorMsg = '';
  descargandoPdf = false;

  constructor(private reporteService: ReporteService) {}

  ngOnInit(): void {
    this.cargarTodos();
  }

  private cargarTodos(): void {
    this.cargando = true;
    this.errorMsg = '';
    const desde = this.fechaDesde ? this.fechaDesde + 'T00:00:00Z' : undefined;
    const hasta = this.fechaHasta ? this.fechaHasta + 'T23:59:59Z' : undefined;
    this.reporteService.librosMasPrestadosDetallado(desde, hasta, this.limiteTop).subscribe({
      next: (libros) => {
        this.libros = libros;
        this.cargarMorosidad();
      },
      error: (err) => this.fallar(err)
    });
  }

  private cargarMorosidad(): void {
    this.reporteService.morosidad().subscribe({
      next: (morosos) => {
        this.morosos = morosos;
        this.cargando = false;
      },
      error: (err) => this.fallar(err)
    });
  }

  aplicarFiltros(): void {
    this.cargarTodos();
  }

  limpiarFiltros(): void {
    this.fechaDesde = '';
    this.fechaHasta = '';
    this.limiteTop = 10;
    this.cargarTodos();
  }

  cambiarLimite(): void {
    this.cargarTodos();
  }

  private fallar(err: unknown): void {
    this.cargando = false;
    this.errorMsg = (err as { error?: { detail?: string } })?.error?.detail
      || 'Error al cargar los reportes';
  }

  descargarMorosidadPdf(): void {
    this.descargandoPdf = true;
    this.errorMsg = '';
    this.reporteService.morosidadPdf().subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const enlace = document.createElement('a');
        enlace.href = url;
        enlace.download = 'reporte-morosidad.pdf';
        enlace.click();
        URL.revokeObjectURL(url);
        this.descargandoPdf = false;
      },
      error: (err) => {
        this.descargandoPdf = false;
        this.errorMsg = (err as { error?: { detail?: string } })?.error?.detail
          || 'Error al generar el PDF de morosidad';
      }
    });
  }
}
