import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { DashboardBibliotecarioHomeComponent } from './dashboard-bibliotecario-home.component';
import { ReporteService, LibroMasPrestado, ReporteMorosidad } from '../core/services/reporte-gerencial.service';
import { ReservacionService } from '../core/services/reservacion.service';
import { ReservacionHoy } from '../core/models/reservacion.model';

describe('DashboardBibliotecarioHomeComponent', () => {
  let component: DashboardBibliotecarioHomeComponent;
  let fixture: ComponentFixture<DashboardBibliotecarioHomeComponent>;
  let reporteServiceSpy: jasmine.SpyObj<ReporteService>;
  let reservacionServiceSpy: jasmine.SpyObj<ReservacionService>;

  const mockLibros: LibroMasPrestado[] = [
    { libroId: 1, titulo: 'Libro A', isbn: '978-1', totalPrestamos: 10 },
    { libroId: 2, titulo: 'Libro B', isbn: '978-2', totalPrestamos: 8 },
  ];

  const mockMorosidad: ReporteMorosidad[] = [
    { usuarioId: 1, nombre: 'Juan', apellido: 'Perez', correo: 'juan@test.com', montoTotalAdeudado: 50, cantidadMultasPendientes: 2, diasAtrasoPromedio: 10 },
  ];

  const mockReservacionesHoy: ReservacionHoy[] = [
    { reservacionId: 1, usuarioNombre: 'Ana Garcia', usuarioCorreo: 'ana@test.com', libroTitulo: 'El Principito', estadoNombre: 'PENDIENTE', fechaLimiteRetiro: '2026-08-23T23:59:59Z' },
    { reservacionId: 2, usuarioNombre: 'Luis Lopez', usuarioCorreo: 'luis@test.com', libroTitulo: 'Cien Años', estadoNombre: 'LISTA_PARA_RETIRO', fechaLimiteRetiro: '2026-08-23T23:59:59Z' },
  ];

  beforeEach(async () => {
    reporteServiceSpy = jasmine.createSpyObj('ReporteService', ['librosMasPrestados', 'morosidad']);
    reservacionServiceSpy = jasmine.createSpyObj('ReservacionService', ['reservacionesDeHoy', 'cambiarEstado']);

    reporteServiceSpy.librosMasPrestados.and.returnValue(of(mockLibros));
    reporteServiceSpy.morosidad.and.returnValue(of(mockMorosidad));
    reservacionServiceSpy.reservacionesDeHoy.and.returnValue(of(mockReservacionesHoy));
    reservacionServiceSpy.cambiarEstado.and.returnValue(of({} as any));

    await TestBed.configureTestingModule({
      imports: [DashboardBibliotecarioHomeComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ReporteService, useValue: reporteServiceSpy },
        { provide: ReservacionService, useValue: reservacionServiceSpy },
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardBibliotecarioHomeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('carga reservaciones de hoy al iniciar', () => {
    expect(reservacionServiceSpy.reservacionesDeHoy).toHaveBeenCalled();
    expect(component.reservacionesHoy.length).toBe(2);
    expect(component.cargandoReservacionesHoy).toBeFalse();
  });

  it('muestra mensaje cuando no hay reservaciones de hoy', () => {
    reservacionServiceSpy.reservacionesDeHoy.and.returnValue(of([]));
    component.reservacionesHoy = [];
    component.cargandoReservacionesHoy = false;
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('No hay reservaciones que venzan hoy.');
  });

  it('marcarListaParaRetiro cambia estado y refresca la lista', () => {
    reservacionServiceSpy.cambiarEstado.and.returnValue(of({} as any));
    reservacionServiceSpy.reservacionesDeHoy.and.returnValue(of([mockReservacionesHoy[1]]));

    component.marcarListaParaRetiro(1);

    expect(reservacionServiceSpy.cambiarEstado).toHaveBeenCalledWith(1, { nuevoEstado: 'LISTA_PARA_RETIRO' });
    expect(reservacionServiceSpy.reservacionesDeHoy).toHaveBeenCalledTimes(2);
  });

  it('muestra skeleton de carga para reservaciones', () => {
    component.cargandoReservacionesHoy = true;
    component.reservacionesHoy = [];
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const skeletons = compiled.querySelectorAll('.animate-pulse');
    expect(skeletons.length).toBeGreaterThan(0);
  });

  it('calcula top5PorDeuda correctamente', () => {
    expect(component.top5PorDeuda.length).toBe(1);
    expect(component.top5PorDeuda[0].usuarioId).toBe(1);
  });

  it('calcula maxDeudaUsuario correctamente', () => {
    expect(component.maxDeudaUsuario).toBe(50);
  });

  it('maneja error al cargar reservaciones de hoy', () => {
    reservacionServiceSpy.reservacionesDeHoy.and.returnValue(throwError(() => new Error('fail')));

    component.ngOnInit();

    expect(component.errorReservacionesHoy).toContain('reservaciones');
    expect(component.cargandoReservacionesHoy).toBeFalse();
  });
});
