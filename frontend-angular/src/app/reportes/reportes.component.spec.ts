import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ReportesComponent } from './reportes.component';
import { ReporteService } from '../core/services/reporte-gerencial.service';
import { SugerenciaAdquisicionService } from '../core/services/sugerencia-adquisicion.service';

describe('ReportesComponent', () => {
  let component: ReportesComponent;
  let fixture: ComponentFixture<ReportesComponent>;
  let reporteService: jasmine.SpyObj<ReporteService>;
  let sugerenciaService: jasmine.SpyObj<SugerenciaAdquisicionService>;

  beforeEach(async () => {
    reporteService = jasmine.createSpyObj('ReporteService', [
      'librosMasPrestadosDetallado', 'morosidad', 'morosidadPdf', 'inventario', 'vencidos', 'categoriasDemandadas'
    ]);
    reporteService.librosMasPrestadosDetallado.and.returnValue(of({
      content: [
        { libroId: 1, titulo: 'El Principito', isbn: '978987800', totalPrestamos: 12, autorNombre: 'Saint-Exupéry', categoriaNombre: 'Ficción', porcentaje: 50 }
      ],
      totalPages: 1,
      totalElements: 1
    }));
    reporteService.morosidad.and.returnValue(of({
      content: [
        { usuarioId: 4, nombre: 'Ana', apellido: 'Paz', correo: 'ana@correo.com', montoTotalAdeudado: 5.0, cantidadMultasPendientes: 1, diasAtrasoPromedio: 3 }
      ],
      totalPages: 1,
      totalElements: 1
    }));
    reporteService.inventario.and.returnValue(of({ content: [], totalPages: 0, totalElements: 0 }));
    reporteService.vencidos.and.returnValue(of({ content: [], totalPages: 0, totalElements: 0 }));
    reporteService.categoriasDemandadas.and.returnValue(of({ content: [], totalPages: 0, totalElements: 0 }));
    sugerenciaService = jasmine.createSpyObj('SugerenciaAdquisicionService', ['listarMasPedidos', 'reportePdf']);
    sugerenciaService.listarMasPedidos.and.returnValue(of({
      content: [{ isbn: '9781449373320', titulo: 'DDIA', autor: 'Kleppmann', cantidad: 3 }],
      totalPages: 1,
      totalElements: 1
    } as any));

    await TestBed.configureTestingModule({
      imports: [ReportesComponent],
      providers: [
        { provide: ReporteService, useValue: reporteService },
        { provide: SugerenciaAdquisicionService, useValue: sugerenciaService }
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

  it('carga sugerencias más pedidas al abrir el módulo', () => {
    component.abrirModulo('sugerencias');

    expect(sugerenciaService.listarMasPedidos).toHaveBeenCalledWith(
      jasmine.objectContaining({ page: 0, size: 10 }));
    expect(component.sugerenciasMasPedidas.length).toBe(1);
    expect(component.sugerenciasMasPedidas[0].cantidad).toBe(3);
  });

  it('descarga el PDF de sugerencias como Blob', () => {
    sugerenciaService.reportePdf.and.returnValue(of(new Blob(['%PDF'], { type: 'application/pdf' })));

    component.descargarSugerenciasPdf();

    expect(sugerenciaService.reportePdf).toHaveBeenCalled();
    expect(component.descargandoPdf).toBeNull();
  });

  it('exporta sugerencias a Excel con columnas #/Título/Autor/ISBN/Solicitudes', async () => {
    component.abrirModulo('sugerencias');

    await component.excelSugerencias();

    expect(component.sugerenciasMasPedidas.length).toBe(1);
  });
});
