import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ReportesComponent } from './reportes.component';
import { ReporteService } from '../core/services/reporte-gerencial.service';

describe('ReportesComponent', () => {
  let component: ReportesComponent;
  let fixture: ComponentFixture<ReportesComponent>;
  let reporteService: jasmine.SpyObj<ReporteService>;

  beforeEach(async () => {
    reporteService = jasmine.createSpyObj('ReporteService', [
      'librosMasPrestadosDetallado', 'morosidad', 'morosidadPdf', 'inventario', 'vencidos', 'categoriasDemandadas'
    ]);
    reporteService.librosMasPrestadosDetallado.and.returnValue(of([
      { libroId: 1, titulo: 'El Principito', isbn: '978987800', totalPrestamos: 12, autorNombre: 'Saint-Exupéry', categoriaNombre: 'Ficción', porcentaje: 50 }
    ]));
    reporteService.morosidad.and.returnValue(of([
      { usuarioId: 4, nombre: 'Ana', apellido: 'Paz', correo: 'ana@uteq.edu.ec', montoTotalAdeudado: 5.0, cantidadMultasPendientes: 1, diasAtrasoPromedio: 3 }
    ]));
    reporteService.inventario.and.returnValue(of([]));
    reporteService.vencidos.and.returnValue(of([]));
    reporteService.categoriasDemandadas.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [ReportesComponent],
      providers: [
        { provide: ReporteService, useValue: reporteService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ReportesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('carga libros al abrir el módulo de libros', () => {
    component.abrirModulo('libros');

    expect(reporteService.librosMasPrestadosDetallado).toHaveBeenCalled();
    expect(component.libros.length).toBe(1);
  });

  it('carga morosidad al abrir el módulo de morosidad', () => {
    component.abrirModulo('morosidad');

    expect(reporteService.morosidad).toHaveBeenCalled();
    expect(component.morosos.length).toBe(1);
  });

  it('recarga el reporte al cambiar el limiteTop y aplicar filtros', () => {
    component.abrirModulo('libros');
    component.limiteTop = 5;
    component.aplicarFiltros();

    expect(reporteService.librosMasPrestadosDetallado).toHaveBeenCalledTimes(2);
  });

  it('descarga el PDF de morosidad como Blob', () => {
    reporteService.morosidadPdf.and.returnValue(of(new Blob(['%PDF'], { type: 'application/pdf' })));

    component.descargarMorosidadPdf();

    expect(reporteService.morosidadPdf).toHaveBeenCalled();
    expect(component.descargandoPdf).toBeNull();
  });

  it('muestra el detail del backend si falla la carga', () => {
    reporteService.librosMasPrestadosDetallado.and.returnValue(
      throwError(() => ({ error: { detail: 'Sin permisos para el reporte' } }))
    );

    component.abrirModulo('libros');

    expect(component.errorMsg).toBe('Sin permisos para el reporte');
  });

  it('muestra el detail del backend si falla la descarga del PDF', () => {
    reporteService.morosidadPdf.and.returnValue(
      throwError(() => ({ error: { detail: 'No se pudo generar el PDF' } }))
    );

    component.descargarMorosidadPdf();

    expect(component.errorMsg).toBe('No se pudo generar el PDF');
    expect(component.descargandoPdf).toBeNull();
  });
});
