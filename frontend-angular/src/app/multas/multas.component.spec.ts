import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { MultasComponent } from './multas.component';
import { MultaService } from '../core/services/multa.service';
import { AuthService } from '../core/services/auth.service';
import { Multa } from '../core/models/multa.model';
import { Page } from '../core/models/pagina.model';

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

describe('MultasComponent', () => {
  let component: MultasComponent;
  let fixture: ComponentFixture<MultasComponent>;
  let multaService: jasmine.SpyObj<MultaService>;
  let authService: jasmine.SpyObj<AuthService>;

  const mockMultas = [
    { id: 1, prestamoId: 1, monto: 5, estadoMultaId: 1, fechaGenerada: '2026-08-12T10:00:00', fechaPagada: '', observaciones: '', saldo: 5 },
    { id: 2, prestamoId: 8, monto: 10, estadoMultaId: 2, fechaGenerada: '2026-08-10T14:30:00', fechaPagada: '2026-08-11T09:00:00', observaciones: 'Pago en efectivo', saldo: 0 }
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

  describe('MultasComponent', () => {
    let component: MultasComponent;
    let fixture: ComponentFixture<MultasComponent>;
    let multaService: jasmine.SpyObj<MultaService>;
    let authService: jasmine.SpyObj<AuthService>;

    const mockMultas = [
      { id: 1, prestamoId: 1, monto: 5, estadoMultaId: 1, fechaGenerada: '2026-08-12T10:00:00', fechaPagada: '', observaciones: '', saldo: 5 },
      { id: 2, prestamoId: 8, monto: 10, estadoMultaId: 2, fechaGenerada: '2026-08-10T14:30:00', fechaPagada: '2026-08-11T09:00:00', observaciones: 'Pago en efectivo', saldo: 0 }
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

  describe('MultasComponent', () => {
    let component: MultasComponent;
    let fixture: ComponentFixture<MultasComponent>;
    let multaService: jasmine.SpyObj<MultaService>;
    let authService: jasmine.SpyObj<AuthService>;

    beforeEach(async () => {
      multaService = jasmine.createSpyObj('MultaService', ['listarPorUsuario', 'pagar', 'anular', 'pagoParcial', 'anular']);
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
        content: [{ id: 1, prestamoId: 1, monto: 5, estadoMultaId: 1, fechaGenerada: '2026-08-12T10:00:00', fechaPagada: '', observaciones: '', saldo: 5 }],
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
    });

    it('carga multas con estado Pagada y fechaPagada formateada', () => {
      multaService.listarPorUsuario.and.returnValue(of({
        content: [{ id: 2, prestamoId: 8, monto: 10, estadoMultaId: 2, fechaGenerada: '2026-08-10T14:30:00', fechaPagada: '2026-08-11T09:00:00', observaciones: 'Pago en efectivo', saldo: 0 }],
        totalPages: 1,
        totalElements: 1,
        size: 10,
        number: 0,
        numberOfElements: 1,
        empty: false
      }));

      fixture.detectChanges();

      expect(component.multas.length).toBe(1);
    });

    it('claseBadge devuelve la clase Tailwind correcta para cada estado', () => {
      const multaPendiente = { id: 1, prestamoId: 1, monto: 5, estadoMultaId: 1, fechaGenerada: '2026-08-12T10:00:00', fechaPagada: '', observaciones: '', saldo: 5, montoPagado: 0 } as any;
      const multaPagada = { id: 2, prestamoId: 2, monto: 10, estadoMultaId: 2, fechaGenerada: '', fechaPagada: '2026-08-11T09:00:00', observaciones: '', saldo: 0 } as any;
      const multaParcial = { id: 3, prestamoId: 3, monto: 10, estadoMultaId: 1, fechaGenerada: '', fechaPagada: '', observaciones: '', saldo: 5, montoPagado: 3 } as any;

      expect(component.claseBadge({ estadoMultaId: 1, montoPagado: 0 } as any)).toBe('bg-red-100 text-red-700');     // Pendiente
      expect(component.claseBadge({ estadoMultaId: 2, montoPagado: 0 } as any)).toBe('bg-green-100 text-green-700'); // Pagada
      expect(component.claseBadge({ estadoMultaId: 1, montoPagado: 5 } as any)).toBe('bg-amber-100 text-amber-700'); // Parcial
    });

    it('etiquetaEstado devuelve el texto correcto para cada estado', () => {
      expect(component.etiquetaEstado({ estadoMultaId: 1, montoPagado: 0 } as any)).toBe('Pendiente');
      expect(component.etiquetaEstado({ estadoMultaId: 2, montoPagado: 0 } as any)).toBe('Pagada');
      expect(component.etiquetaEstado({ estadoMultaId: 1, montoPagado: 5 } as any)).toBe('Parcial');
    });

    it('muestra errorMsg sin romper la UI si el backend falla', () => {
      multaService.listarPorUsuario.and.returnValue(throwError(() => ({ status: 500 })));

      fixture.detectChanges();

      expect(component.errorMsg).toBe('Error al buscar las multas');
      expect(component.cargando).toBeFalse();
    });

    it('prefiltra por el usuarioId del query param cuando llega desde Préstamos', () => {
      authService.hasRole.and.returnValue(true); // BIBLIOTECARIO/GERENTE
      parametrosConsulta = { usuarioId: '7' };
      multaService.listarPorUsuario.and.returnValue(of(mockPage));

      fixture.detectChanges();

      expect(component.usuarioSeleccionado?.id).toBe(7);
      expect(multaService.listarPorUsuario).toHaveBeenCalledWith(7, jasmine.anything());
    });

    it('calcula totalPendiente sumando solo multas con estado PENDIENTE', () => {
      multaService.listarPorUsuario.and.returnValue(of({
        content: [
          { id: 1, prestamoId: 1, monto: 5, estadoMultaId: 1, fechaGenerada: '2026-08-12T10:00:00', fechaPagada: '', observaciones: '', saldo: 5 },
          { id: 2, prestamoId: 2, monto: 10, estadoMultaId: 2, fechaGenerada: '2026-08-10T14:30:00', fechaPagada: '2026-08-11T09:00:00', observaciones: '', saldo: 0 },
          { id: 3, prestamoId: 3, monto: 3, estadoMultaId: 1, fechaGenerada: '2026-08-09T10:00:00', fechaPagada: '', observaciones: '', saldo: 3 }
        ],
        totalPages: 1, totalElements: 3, size: 10, number: 0, numberOfElements: 3, empty: false
      }));

      fixture.detectChanges();

      expect(component.totalPendiente).toBe(8);
    });

    it('muestra errorMsg sin romper la UI si el backend falla', () => {
      multaService.listarPorUsuario.and.returnValue(throwError(() => ({ status: 500 })));

      fixture.detectChanges();

      expect(component.errorMsg).toBe('Error al buscar las multas');
      expect(component.cargando).toBeFalse();
    });

    it('prefiltra por el usuarioId del query param cuando llega desde Préstamos', () => {
      authService.hasRole.and.returnValue(true); // BIBLIOTECARIO/GERENTE
      parametrosConsulta = { usuarioId: '7' };
      multaService.listarPorUsuario.and.returnValue(of(mockPage));

      fixture.detectChanges();

      expect(component.usuarioSeleccionado?.id).toBe(7);
      expect(multaService.listarPorUsuario).toHaveBeenCalledWith(7, jasmine.anything());
    });

    it('calcula totalPendiente sumando solo multas con estado PENDIENTE', () => {
      multaService.listarPorUsuario.and.returnValue(of({
        content: [
          { id: 1, prestamoId: 1, monto: 5, estadoMultaId: 1, fechaGenerada: '2026-08-12T10:00:00', fechaPagada: '', observaciones: '', saldo: 5 },
          { id: 2, prestamoId: 2, monto: 10, estadoMultaId: 2, fechaGenerada: '2026-08-10T14:30:00', fechaPagada: '2026-08-11T09:00:00', observaciones: '', saldo: 0 },
          { id: 3, prestamoId: 3, monto: 3, estadoMultaId: 1, fechaGenerada: '2026-08-09T10:00:00', fechaPagada: '', observaciones: '', saldo: 3 }
        ],
        totalPages: 1, totalElements: 3, size: 10, number: 0, numberOfElements: 3, empty: false
      }));

      fixture.detectChanges();

      expect(component.totalPendiente).toBe(8);
    });

    it('muestra errorMsg sin romper la UI si el backend falla', () => {
      multaService.listarPorUsuario.and.returnValue(throwError(() => ({ status: 500 })));

      fixture.detectChanges();

      expect(component.errorMsg).toBe('Error al buscar las multas');
      expect(component.cargando).toBeFalse();
    });

    it('prefiltra por el usuarioId del query param cuando llega desde Préstamos', () => {
      authService.hasRole.and.returnValue(true); // BIBLIOTECARIO/GERENTE
      parametrosConsulta = { usuarioId: '7' };
      multaService.listarPorUsuario.and.returnValue(of(mockPage));

      fixture.detectChanges();

      expect(component.usuarioSeleccionado?.id).toBe(7);
      expect(multaService.listarPorUsuario).toHaveBeenCalledWith(7, jasmine.anything());
    });

    it('calcula totalPendiente sumando solo multas con estado PENDIENTE', () => {
      multaService.listarPorUsuario.and.returnValue(of({
        content: [
          { id: 1, prestamoId: 1, monto: 5, estadoMultaId: 1, fechaGenerada: '2026-08-12T10:00:00', fechaPagada: '', observaciones: '', saldo: 5 },
          { id: 2, prestamoId: 2, monto: 10, estadoMultaId: 2, fechaGenerada: '2026-08-10T14:30:00', fechaPagada: '2026-08-11T09:00:00', observaciones: '', saldo: 0 },
          { id: 3, prestamoId: 3, monto: 3, estadoMultaId: 1, fechaGenerada: '2026-08-09T10:00:00', fechaPagada: '', observaciones: '', saldo: 3 }
        ],
        totalPages: 1, totalElements: 3, size: 10, number: 0, numberOfElements: 3, empty: false
      }));

      fixture.detectChanges();

      expect(component.totalPendiente).toBe(8);
    });

    it('muestra errorMsg sin romper la UI si el backend falla', () => {
      multaService.listarPorUsuario.and.returnValue(throwError(() => ({ status: 500 })));

      fixture.detectChanges();

      expect(component.errorMsg).toBe('Error al buscar las multas');
      expect(component.cargando).toBeFalse();
    });
  });
});
});