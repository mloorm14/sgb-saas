import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { NotificacionesComponent } from './notificaciones.component';
import { NotificacionService } from '../core/services/notificacion.service';
import { ActivatedRoute } from '@angular/router';
import { Page } from '../core/models/pagina.model';
import { Notificacion } from '../core/models/notificacion.model';

describe('NotificacionesComponent', () => {
  let component: NotificacionesComponent;
  let fixture: ComponentFixture<NotificacionesComponent>;
  let notificacionService: jasmine.SpyObj<NotificacionService>;

  const mockNotificaciones: Notificacion[] = [
    {
      id: 1,
      prestamoId: 1,
      tipoNotificacionId: 1,
      mensaje: 'Su préstamo vence en 3 días',
      fechaEnvio: '2026-08-12T10:00:00',
      enviadoOk: true,
      creadoEn: '2026-08-12T10:00:00'
    },
    {
      id: 2,
      prestamoId: 8,
      tipoNotificacionId: 2,
      mensaje: 'Multa generada por devolución tardía',
      fechaEnvio: '2026-08-10T14:30:00',
      enviadoOk: false,
      creadoEn: '2026-08-10T14:30:00'
    }
  ];

  const mockPage: Page<Notificacion> = {
    content: mockNotificaciones,
    totalPages: 2,
    totalElements: 15,
    size: 10,
    number: 0,
    numberOfElements: 2,
    empty: false
  };

  beforeEach(async () => {
    const notificacionServiceSpy = jasmine.createSpyObj('NotificacionService', ['listar']);

    await TestBed.configureTestingModule({
      imports: [NotificacionesComponent],
      providers: [
        { provide: NotificacionService, useValue: notificacionServiceSpy },
        { provide: ActivatedRoute, useValue: { snapshot: {} } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(NotificacionesComponent);
    component = fixture.componentInstance;
    notificacionService = TestBed.inject(NotificacionService) as jasmine.SpyObj<NotificacionService>;
  });

  it('carga las notificaciones del usuario y muestra la fecha formateada', () => {
    notificacionService.listar.and.returnValue(of({
      content: mockNotificaciones,
      totalPages: 2,
      totalElements: 15,
      size: 10,
      number: 0,
      numberOfElements: 2,
      empty: false
    }));

    fixture.detectChanges();

    expect(notificacionService.listar).toHaveBeenCalledWith({ page: 0, size: 10 });
    expect(component.notificaciones.length).toBe(2);
    expect(component.formatearFecha('2026-08-12T10:00:00')).toBe('12 ago 2026 10:00');
  });

  it('pagina a la siguiente página y recarga', () => {
    const page1 = { content: mockNotificaciones, totalPages: 2, totalElements: 15, size: 10, number: 0, numberOfElements: 2, empty: false };
    const page2 = { content: [mockNotificaciones[1]], totalPages: 2, totalElements: 15, size: 10, number: 1, numberOfElements: 1, empty: false };

    notificacionService.listar.and.returnValues(of(page1), of(page2));

    fixture.detectChanges();
    expect(component.currentPage).toBe(0);

    component.paginaSiguiente();
    fixture.detectChanges();

    expect(notificacionService.listar).toHaveBeenCalledTimes(2);
    expect(notificacionService.listar).toHaveBeenCalledWith({ page: 1, size: 10 });
    expect(component.currentPage).toBe(1);
    expect(component.notificaciones.length).toBe(1);
  });

  it('pagina a la página anterior', () => {
    const page1 = { content: mockNotificaciones, totalPages: 2, totalElements: 15, size: 10, number: 1, numberOfElements: 2, empty: false };
    const page0 = { content: mockNotificaciones, totalPages: 2, totalElements: 15, size: 10, number: 0, numberOfElements: 2, empty: false };

    // Set up initial state with page 1
    notificacionService.listar.and.returnValues(of(page1), of(page0));

    fixture.detectChanges();
    // After initial load, manually set currentPage to 1 to simulate being on page 1
    component.currentPage = 1;

    component.paginaAnterior();
    fixture.detectChanges();

    expect(notificacionService.listar).toHaveBeenCalledWith({ page: 0, size: 10 });
    expect(component.currentPage).toBe(0);
  });

  it('irAPagina navega a una página específica', () => {
    const page2 = { content: mockNotificaciones, totalPages: 3, totalElements: 25, size: 10, number: 2, numberOfElements: 5, empty: false };

    notificacionService.listar.and.returnValue(of(page2));

    fixture.detectChanges();
    expect(component.currentPage).toBe(0);

    component.irAPagina(2);
    fixture.detectChanges();

    expect(notificacionService.listar).toHaveBeenCalledWith({ page: 2, size: 10 });
    expect(component.currentPage).toBe(2);
  });

  it('irAPagina no hace nada si la página es inválida o igual a la actual', () => {
    notificacionService.listar.and.returnValue(of({
      content: mockNotificaciones,
      totalPages: 2,
      totalElements: 15,
      size: 10,
      number: 0,
      numberOfElements: 2,
      empty: false
    }));

    fixture.detectChanges();

    component.irAPagina(-1);
    fixture.detectChanges();
    expect(component.currentPage).toBe(0);

    component.irAPagina(100);
    fixture.detectChanges();
    expect(component.currentPage).toBe(0);

    component.irAPagina(0);
    fixture.detectChanges();
    expect(component.currentPage).toBe(0);
  });

  it('muestra error sin romper la UI si el backend falla', () => {
    notificacionService.listar.and.returnValue(throwError(() => ({ status: 500, error: { detail: 'Error interno' } })));

    fixture.detectChanges();

    expect(component.errorMsg).toBe('Error al cargar las notificaciones');
    expect(component.cargando).toBeFalse();
    expect(component.notificaciones.length).toBe(0);
  });

  it('renderiza badge de "No enviada" cuando enviadoOk es false', () => {
    notificacionService.listar.and.returnValue(of({
      content: [mockNotificaciones[1]],
      totalPages: 1,
      totalElements: 1,
      size: 10,
      number: 0,
      numberOfElements: 1,
      empty: false
    }));

    fixture.detectChanges();

    const badge = fixture.nativeElement.querySelector('.bg-error-container');
    expect(badge).toBeTruthy();
    expect(badge.textContent).toContain('No enviada');
  });

  it('renderiza badge de "Enviada" cuando enviadoOk es true', () => {
    notificacionService.listar.and.returnValue(of({
      content: [mockNotificaciones[0]],
      totalPages: 1,
      totalElements: 1,
      size: 10,
      number: 0,
      numberOfElements: 1,
      empty: false
    }));

    fixture.detectChanges();

    const badge = fixture.nativeElement.querySelector('.bg-success\\/15');
    expect(badge).toBeTruthy();
    expect(badge.textContent).toContain('Enviada');
  });
});