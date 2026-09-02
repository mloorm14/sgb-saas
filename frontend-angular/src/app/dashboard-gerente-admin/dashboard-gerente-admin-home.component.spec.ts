import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { DashboardGerenteAdminHomeComponent } from './dashboard-gerente-admin-home.component';
import { ReporteService, LibroMasPrestado, ReporteMorosidad } from '../core/services/reporte-gerencial.service';
import { ReservacionService } from '../core/services/reservacion.service';
import { AuthService } from '../core/services/auth.service';
import { ReservacionHoy } from '../core/models/reservacion.model';

describe('DashboardGerenteAdminHomeComponent', () => {
  let component: DashboardGerenteAdminHomeComponent;
  let fixture: ComponentFixture<DashboardGerenteAdminHomeComponent>;
  let reporteServiceSpy: jasmine.SpyObj<ReporteService>;
  let reservacionServiceSpy: jasmine.SpyObj<ReservacionService>;

  const mockLibros: LibroMasPrestado[] = [
    { libroId: 1, titulo: 'Libro A', isbn: '978-1', totalPrestamos: 10 },
    { libroId: 2, titulo: 'Libro B', isbn: '978-2', totalPrestamos: 8 },
  ];

  const mockMorosidad: ReporteMorosidad[] = [
    { usuarioId: 1, nombre: 'Juan', apellido: 'Perez', correo: 'juan@test.com', montoTotalAdeudado: 50, cantidadMultasPendientes: 2, diasAtrasoPromedio: 10 },
  ];

  const mockReservaciones: ReservacionHoy[] = [
    { reservacionId: 1, usuarioNombre: 'Ana Garcia', usuarioCorreo: 'ana@test.com', libroTitulo: 'El Principito', estadoNombre: 'PENDIENTE', fechaLimiteRetiro: '2026-08-24T23:59:59Z' },
  ];

  beforeEach(async () => {
    reporteServiceSpy = jasmine.createSpyObj('ReporteService', ['librosMasPrestados', 'morosidad']);
    reservacionServiceSpy = jasmine.createSpyObj('ReservacionService', ['reservacionesDeHoy', 'cambiarEstado']);

    reporteServiceSpy.librosMasPrestados.and.returnValue(of(mockLibros));
    reporteServiceSpy.morosidad.and.returnValue(of(mockMorosidad));
    reservacionServiceSpy.reservacionesDeHoy.and.returnValue(of(mockReservaciones));
    reservacionServiceSpy.cambiarEstado.and.returnValue(of({} as any));

    await TestBed.configureTestingModule({
      imports: [DashboardGerenteAdminHomeComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ReporteService, useValue: reporteServiceSpy },
        { provide: ReservacionService, useValue: reservacionServiceSpy },
        { provide: AuthService, useValue: { hasRole: (...roles: string[]) => roles.includes('GERENTE') } },
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardGerenteAdminHomeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('carga libros con limite por defecto (5)', () => {
    expect(reporteServiceSpy.librosMasPrestados).toHaveBeenCalledWith(undefined, undefined, 5);
    expect(component.librosMasPrestados.length).toBe(2);
    expect(component.limiteLibros).toBe(5);
  });

  it('cambiarLimiteLibros pide al backend con el nuevo limite', () => {
    reporteServiceSpy.librosMasPrestados.and.returnValue(of(mockLibros));

    component.cambiarLimiteLibros(10);

    expect(reporteServiceSpy.librosMasPrestados).toHaveBeenCalledWith(undefined, undefined, 10);
    expect(component.limiteLibros).toBe(10);
  });

  it('carga reservaciones de hoy', () => {
    expect(reservacionServiceSpy.reservacionesDeHoy).toHaveBeenCalled();
    expect(component.reservacionesHoy.length).toBe(1);
  });

  it('muestra top10PorDeuda correctamente', () => {
    expect(component.top10PorDeuda.length).toBe(1);
    expect(component.top10PorDeuda[0].usuarioId).toBe(1);
  });

  it('marcarListaParaRetiro cambia estado y refresca', () => {
    reservacionServiceSpy.reservacionesDeHoy.and.returnValue(of([]));

    component.marcarListaParaRetiro(1);

    expect(reservacionServiceSpy.cambiarEstado).toHaveBeenCalledWith(1, { nuevoEstado: 'LISTA_PARA_RETIRO' });
    expect(reservacionServiceSpy.reservacionesDeHoy).toHaveBeenCalledTimes(2);
  });

  it('muestra titulo para GERENTE', () => {
    expect(component.tituloBienvenida).toBe('Bienvenido, Gerencia');
  });

  it('maneja error al cargar libros', () => {
    reporteServiceSpy.librosMasPrestados.and.returnValue(throwError(() => new Error('fail')));

    component.cambiarLimiteLibros(5);

    expect(component.errorLibros).toContain('No se pudo cargar');
    expect(component.cargandoLibros).toBeFalse();
  });
});
