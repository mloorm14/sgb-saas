import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReporteService, GranularidadUso, LibroMasPrestado, ReporteMorosidad, ReporteUsoPorPeriodo } from '../core/services/reporte-gerencial.service';

@Component({
  selector: 'app-reportes',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './reportes.component.html'
})
export class ReportesComponent implements OnInit {
  libros: LibroMasPrestado[] = [];
  morosos: ReporteMorosidad[] = [];
  uso: ReporteUsoPorPeriodo[] = [];
  granularidad: GranularidadUso = 'dia';

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
    this.reporteService.librosMasPrestados().subscribe({
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
        this.cargarUso();
      },
      error: (err) => this.fallar(err)
    });
  }

  private cargarUso(): void {
    this.reporteService.uso(this.granularidad).subscribe({
      next: (uso) => {
        this.uso = uso;
        this.cargando = false;
      },
      error: (err) => this.fallar(err)
    });
  }

  cambiarGranularidad(): void {
    this.cargando = true;
    this.errorMsg = '';
    this.reporteService.uso(this.granularidad).subscribe({
      next: (uso) => {
        this.uso = uso;
        this.cargando = false;
      },
      error: (err) => this.fallar(err)
    });
  }

  private fallar(err: unknown): void {
    this.cargando = false;
    this.errorMsg = (err as { error?: { detail?: string } })?.error?.detail
      || 'Error al cargar los reportes';
  }

  // Solo morosidad expone PDF (PrestamoController). Blob -> <a download>.
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

  formatoPeriodo(iso: string): string {
    const fecha = new Date(iso);
    return fecha.toLocaleDateString('es-ES', { day: '2-digit', month: 'short', year: 'numeric' });
  }
}