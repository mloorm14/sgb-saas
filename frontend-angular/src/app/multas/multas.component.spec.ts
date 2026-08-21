import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { MultasComponent } from './multas.component';
import { MultaService } from '../core/services/multa.service';
import { AuthService } from '../core/services/auth.service';
import { Multa } from '../core/models/multa.model';

// Query params simulados (mutable por prueba): llega "usuarioId" cuando se
// navega desde Préstamos -> Gestionar Multas.
let parametrosConsulta: Record<string, string> = {};
const activatedRouteMock = {
  snapshot: {
    queryParamMap: {
      get: (clave: string) => parametrosConsulta[clave] ?? null
    }
  }
};
import { Page } from '../core/models/pagina.model';

describe('MultasComponent', () => {
  let component: MultasComponent;
  let fixture: ComponentFixture<MultasComponent>;
  let multaService: jasmine.SpyObj<MultaService>;
  let authService: jasmine.SpyObj<AuthService>;

  const mockMultas: Multa[] = [
    { id: 1, prestamoId: 1, monto: 5, estadoMultaId: 1, fechaGenerada: '2026-08-12T10:00:00', fechaPagada: '', observaciones: '' },
    { id: 2, prestamoId: 8, monto: 10, estadoMultaId: 2, fechaGenerada: '2026-08-10T14:30:00', fechaPagada: '2026-08-11T09:00:00', observaciones: 'Pago en efectivo' }
  ];

  const mockPage = {
    content: mockMultas,
    totalPages: 2,
    totalElements: 15,
    size: 10,
    number: 0,
    numberOfElements: 2,
    empty: false
  };

  beforeEach(async () => {
    multaService = jasmine.createSpyObj('MultaService', ['listarPorUsuario', 'pagar', 'anular']);
    authService = jasmine.createSpyObj('AuthService', ['getUserId', 'hasRole']);

    authService.getUserId.and.returnValue(3);
    authService.hasRole.and.returnValue(false); // lector por defecto
    parametrosConsulta = {};

    await TestBed.configureTestingModule({
      imports: [MultasComponent],
      providers: [
        { provide: MultaService, useValue: multaService },
        { provide: AuthService, useValue: authService },
        { provide: ActivatedRoute, useValue: activatedRouteMock }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MultasComponent);
    component = fixture.componentInstance;
    multaService = TestBed.inject(MultaService) as jasmine.SpyObj<MultaService>;
    authService = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
  });

  it('carga el listado inicial de multas propias correctamente (rol lector)', () => {
    multaService.listarPorUsuario.and.returnValue(of({
      content: [{ id: 1, prestamoId: 1, monto: 5, estadoMultaId: 1, fechaGenerada: '2026-08-12T10:00:00', fechaPagada: '', observaciones: '' }],
      totalPages: 1,
      totalElements: 1,
      size: 10,
      number: 0,
      numberOfElements: 1,
      empty: false
    }));

    fixture.detectChanges();

    expect(multaService.listarPorUsuario).toHaveBeenCalledWith(3, jasmine.anything());
    expect(component.multas.length).toBe(1);
    expect(component.errorMsg).toBe('');
    expect(component.puedeGestionar).toBeFalse();
  });

  it('carga multas con estado Pagada y fechaPagada formateada', () => {
    multaService.listarPorUsuario.and.returnValue(of({
      content: [{ id: 2, prestamoId: 8, monto: 10, estadoMultaId: 2, fechaGenerada: '2026-08-10T14:30:00', fechaPagada: '2026-08-11T09:00:00', observaciones: 'Pago en efectivo' }],
      totalPages: 1,
      totalElements: 1,
      size: 10,
      number: 0,
      numberOfElements: 1,
      empty: false
    }));

    fixture.detectChanges();

    expect(component.multas.length).toBe(1);
    expect(component.formatearFecha('2026-08-10T14:30:00')).toBe('10 ago 2026');
  });

  it('claseEstadoMulta devuelve la clase Tailwind correcta para cada estado', () => {
    expect(component.claseEstadoMulta(1)).toBe('bg-tertiary-fixed text-on-tertiary-fixed');     // Pendiente
    expect(component.claseEstadoMulta(2)).toBe('bg-secondary-container text-on-secondary-container'); // Pagada
    expect(component.claseEstadoMulta(3)).toBe('bg-surface-container-low text-on-surface-variant'); // Anulada
    expect(component.claseEstadoMulta(99)).toBe('bg-surface-container-low text-on-surface-variant'); // Desconocido
  });

  it('muestra errorMsg sin romper la UI si el backend falla', () => {
    multaService.listarPorUsuario.and.returnValue(throwError(() => ({ status: 500 })));

    fixture.detectChanges();

    expect(component.errorMsg).toBe('Error al buscar las multas');
    expect(component.cargando).toBeFalse();
  });

  it('formatearFecha formatea correctamente una fecha ISO', () => {
    expect(component.formatearFecha('2026-08-12T10:00:00')).toBe('12 ago 2026');
    expect(component.formatearFecha('')).toBe('—');
    expect(component.formatearFecha(null as any)).toBe('—');
  });

  it('prefiltra por el usuarioId del query param cuando llega desde Préstamos', () => {
    authService.hasRole.and.returnValue(true); // BIBLIOTECARIO/GERENTE
    parametrosConsulta = { usuarioId: '7' };
    multaService.listarPorUsuario.and.returnValue(of(mockPage));

    fixture.detectChanges();

    expect(component.usuarioIdBusqueda).toBe(7);
    expect(multaService.listarPorUsuario).toHaveBeenCalledWith(7, jasmine.anything());
  });

  it('calcula totalPendiente sumando solo multas con estado PENDIENTE', () => {
    multaService.listarPorUsuario.and.returnValue(of({
      content: [
        { id: 1, prestamoId: 1, monto: 5, estadoMultaId: 1, fechaGenerada: '2026-08-12T10:00:00', fechaPagada: '', observaciones: '' },
        { id: 2, prestamoId: 2, monto: 10, estadoMultaId: 2, fechaGenerada: '2026-08-10T14:30:00', fechaPagada: '2026-08-11T09:00:00', observaciones: '' },
        { id: 3, prestamoId: 3, monto: 3, estadoMultaId: 1, fechaGenerada: '2026-08-09T10:00:00', fechaPagada: '', observaciones: '' }
      ],
      totalPages: 1, totalElements: 3, size: 10, number: 0, numberOfElements: 3, empty: false
    }));

    fixture.detectChanges();

    expect(component.totalPendiente).toBe(8);
    expect(component.totalHistoricas).toBe(3);
  });

  it('motivoMulta devuelve observaciones si existe, fallback genérico si no', () => {
    const multaConObs: Multa = { id: 1, prestamoId: 1, monto: 5, estadoMultaId: 1, fechaGenerada: '', fechaPagada: '', observaciones: 'Pago en efectivo' };
    const multaSinObs: Multa = { id: 2, prestamoId: 2, monto: 5, estadoMultaId: 1, fechaGenerada: '', fechaPagada: '', observaciones: '' };

    expect(component.motivoMulta(multaConObs)).toBe('Pago en efectivo');
    expect(component.motivoMulta(multaSinObs)).toBe('Multa por préstamo atrasado');
  });

  it('diasAtraso calcula días transcurridos desde fechaGenerada', () => {
    const hoy = new Date();
    const hace3 = new Date(hoy);
    hace3.setDate(hace3.getDate() - 3);
    const iso = hace3.toISOString();

    const multa: Multa = { id: 1, prestamoId: 1, monto: 5, estadoMultaId: 1, fechaGenerada: iso, fechaPagada: '', observaciones: '' };

    expect(component.diasAtraso(multa)).toBe(3);
  });

  it('iconoEstadoMulta devuelve el ícono correcto por estado', () => {
    expect(component.iconoEstadoMulta(1)).toBe('payments');
    expect(component.iconoEstadoMulta(2)).toBe('check_circle');
    expect(component.iconoEstadoMulta(3)).toBe('cancel');
    expect(component.iconoEstadoMulta(99)).toBe('help');
  });
});