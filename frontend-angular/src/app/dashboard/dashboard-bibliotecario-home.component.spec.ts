import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { DashboardBibliotecarioHomeComponent } from './dashboard-bibliotecario-home.component';
import { ReservacionService } from '../core/services/reservacion.service';
import { ReservacionHoy } from '../core/models/reservacion.model';

describe('DashboardBibliotecarioHomeComponent', () => {
  let component: DashboardBibliotecarioHomeComponent;
  let fixture: ComponentFixture<DashboardBibliotecarioHomeComponent>;
  let reservacionServiceSpy: jasmine.SpyObj<ReservacionService>;

  const mockReservaciones: ReservacionHoy[] = [
    { reservacionId: 1, usuarioNombre: 'Ana Garcia', usuarioCorreo: 'ana@test.com', libroTitulo: 'El Principito', estadoNombre: 'PENDIENTE', fechaLimiteRetiro: '2026-08-24T23:59:59Z' },
    { reservacionId: 2, usuarioNombre: 'Luis Lopez', usuarioCorreo: 'luis@test.com', libroTitulo: 'Cien Anos', estadoNombre: 'LISTA_PARA_RETIRO', fechaLimiteRetiro: '2026-08-24T23:59:59Z' },
  ];

  beforeEach(async () => {
    reservacionServiceSpy = jasmine.createSpyObj('ReservacionService', ['reservacionesDeHoy', 'cambiarEstado']);
    reservacionServiceSpy.reservacionesDeHoy.and.returnValue(of(mockReservaciones));
    reservacionServiceSpy.cambiarEstado.and.returnValue(of({} as any));

    await TestBed.configureTestingModule({
      imports: [DashboardBibliotecarioHomeComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
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
    expect(compiled.textContent).toContain('No hay reservaciones para hoy');
  });

  it('marcarListaParaRetiro cambia estado y refresca la lista', () => {
    reservacionServiceSpy.reservacionesDeHoy.and.returnValue(of([mockReservaciones[1]]));

    component.marcarListaParaRetiro(1);

    expect(reservacionServiceSpy.cambiarEstado).toHaveBeenCalledWith(1, { nuevoEstado: 'LISTA_PARA_RETIRO' });
    expect(reservacionServiceSpy.reservacionesDeHoy).toHaveBeenCalledTimes(2);
  });

  it('muestra skeleton de carga', () => {
    component.cargandoReservacionesHoy = true;
    component.reservacionesHoy = [];
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const skeletons = compiled.querySelectorAll('.animate-pulse');
    expect(skeletons.length).toBeGreaterThan(0);
  });

  it('maneja error al cargar reservaciones de hoy', () => {
    reservacionServiceSpy.reservacionesDeHoy.and.returnValue(throwError(() => new Error('fail')));

    component.ngOnInit();

    expect(component.errorReservacionesHoy).toContain('reservaciones');
    expect(component.cargandoReservacionesHoy).toBeFalse();
  });
});
