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
      'librosMasPrestados', 'morosidad', 'uso', 'morosidadPdf'
    ]);
    reporteService.librosMasPrestados.and.returnValue(of([
      { libroId: 1, titulo: 'El Principito', isbn: '978987800', totalPrestamos: 12 }
    ]));
    reporteService.morosidad.and.returnValue(of([
      { usuarioId: 4, nombre: 'Ana', apellido: 'Paz', correo: 'ana@uteq.edu.ec', montoTotalAdeudado: 5.0, cantidadMultasPendientes: 1, diasAtrasoPromedio: 3 }
    ]));
    reporteService.uso.and.returnValue(of([
      { periodo: '2026-08-15T00:00:00Z', totalPrestamos: 8, totalDevoluciones: 5 }
    ]));

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

  it('carga los tres reportes al iniciar (libros, morosidad y uso por día)', () => {
    expect(reporteService.librosMasPrestados).toHaveBeenCalled();
    expect(reporteService.morosidad).toHaveBeenCalled();
    expect(reporteService.uso).toHaveBeenCalledWith('dia');
    expect(component.libros.length).toBe(1);
    expect(component.morosos.length).toBe(1);
    expect(component.uso.length).toBe(1);
  });

  it('recarga solo el reporte de uso al cambiar la granularidad', () => {
    component.granularidad = 'mes';
    component.cambiarGranularidad();

    expect(reporteService.uso).toHaveBeenCalledWith('mes');
    expect(reporteService.librosMasPrestados).toHaveBeenCalledTimes(1);
  });

  it('descarga el PDF de morosidad como Blob (los otros reportes no tienen PDF)', () => {
    reporteService.morosidadPdf.and.returnValue(of(new Blob(['%PDF'], { type: 'application/pdf' })));

    component.descargarMorosidadPdf();

    expect(reporteService.morosidadPdf).toHaveBeenCalled();
    expect(component.descargandoPdf).toBeFalse();
  });

  it('muestra el detail del backend si falla la carga', () => {
    reporteService.librosMasPrestados.and.returnValue(
      throwError(() => ({ error: { detail: 'Sin permisos para el reporte' } }))
    );

    component.ngOnInit();

    expect(component.errorMsg).toBe('Sin permisos para el reporte');
  });

  it('muestra el detail del backend si falla la descarga del PDF', () => {
    reporteService.morosidadPdf.and.returnValue(
      throwError(() => ({ error: { detail: 'No se pudo generar el PDF' } }))
    );

    component.descargarMorosidadPdf();

    expect(component.errorMsg).toBe('No se pudo generar el PDF');
    expect(component.descargandoPdf).toBeFalse();
  });
});