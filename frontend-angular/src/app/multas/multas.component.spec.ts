import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { MultasComponent } from './multas.component';
import { MultaService } from '../core/services/multa.service';
import { PrestamoService } from '../core/services/prestamo.service';
import { AuthService } from '../core/services/auth.service';
import { MultaDetalle } from '../core/models/multa.model';
import { UsuarioPrestamos } from '../core/models/prestamos-gestion.model';

describe('MultasComponent', () => {
  let component: MultasComponent;
  let fixture: ComponentFixture<MultasComponent>;
  let multaService: jasmine.SpyObj<MultaService>;
  let prestamoService: jasmine.SpyObj<PrestamoService>;
  let authService: jasmine.SpyObj<AuthService>;

  const mockMulta1: MultaDetalle = {
    id: 1,
    prestamoId: 1,
    libroTitulo: 'Clean Code',
    libroIsbn: '9780132350884',
    observaciones: '',
    monto: 5,
    montoPagado: 0,
    saldo: 5,
    estadoMultaId: 1,
    estadoNombre: 'PENDIENTE',
    fechaGenerada: '2026-08-12T10:00:00',
    fechaPagada: '',
    fechaPrestamoInicio: '2026-08-01T10:00:00',
    fechaPrestamoFin: '2026-08-08T10:00:00',
    diasAtraso: 4
  };

  const mockMulta2: MultaDetalle = {
    id: 2,
    prestamoId: 8,
    libroTitulo: 'The Pragmatic Programmer',
    libroIsbn: '9780201616224',
    observaciones: 'Pago en efectivo',
    monto: 10,
    montoPagado: 10,
    saldo: 0,
    estadoMultaId: 2,
    estadoNombre: 'PAGADA',
    fechaGenerada: '2026-08-10T14:30:00',
    fechaPagada: '2026-08-11T09:00:00',
    fechaPrestamoInicio: '2026-08-01T10:00:00',
    fechaPrestamoFin: '2026-08-08T10:00:00',
    diasAtraso: 2
  };

  const mockPage = {
    content: [mockMulta1, mockMulta2],
    totalPages: 1,
    totalElements: 2,
    size: 5,
    number: 0,
    numberOfElements: 2,
    empty: false
  };

  const mockUsuario: UsuarioPrestamos = {
    id: 3,
    nombreCompleto: 'Juan Perez',
    correo: 'juan.perez@uteq.edu.ec',
    cedula: '1234567890',
    tiposUsuario: ['LECTOR'],
    estadoCuenta: 'ACTIVO',
    montoMultasPendientes: 5,
    cantidadMultasPendientes: 1,
    diasPrestamoSugerido: 7
  };

  beforeEach(async () => {
    multaService = jasmine.createSpyObj('MultaService', ['listarDetallePorUsuario', 'pagoParcial', 'anular']);
    prestamoService = jasmine.createSpyObj('PrestamoService', ['buscarUsuarioPorCorreo']);
    authService = jasmine.createSpyObj('AuthService', ['getUserId', 'getCorreo', 'hasRole']);

    authService.getUserId.and.returnValue(3);
    authService.getCorreo.and.returnValue('juan.perez@uteq.edu.ec');
    authService.hasRole.and.callFake((...roles: string[]) => roles.includes('LECTOR'));
    multaService.listarDetallePorUsuario.and.returnValue(of(mockPage));
    prestamoService.buscarUsuarioPorCorreo.and.returnValue(of(mockUsuario));

    await TestBed.configureTestingModule({
      imports: [MultasComponent],
      providers: [
        { provide: MultaService, useValue: multaService },
        { provide: PrestamoService, useValue: prestamoService },
        { provide: AuthService, useValue: authService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MultasComponent);
    component = fixture.componentInstance;
  });

  it('carga el listado inicial de multas propias correctamente (rol lector)', () => {
    fixture.detectChanges();

    expect(authService.hasRole).toHaveBeenCalledWith('LECTOR');
    expect(authService.getUserId).toHaveBeenCalled();
    expect(authService.getCorreo).toHaveBeenCalled();
    expect(multaService.listarDetallePorUsuario).toHaveBeenCalledWith(3, jasmine.objectContaining({
      page: 0,
      size: 5,
      sort: 'estadoMultaId,asc'
    }));
    expect(component.multas.length).toBe(2);
    expect(component.esLector).toBeTrue();
    expect(component.errorMsg).toBe('');
  });

  it('claseBadge devuelve la clase Tailwind correcta para cada estado', () => {
    const multaPendiente = { ...mockMulta1, estadoMultaId: 1, montoPagado: 0 };
    const multaPagada = { ...mockMulta2, estadoMultaId: 2, montoPagado: 10 };
    const multaParcial = { ...mockMulta1, estadoMultaId: 1, montoPagado: 2 };

    expect(component.claseBadge(multaPendiente)).toBe('bg-red-100 text-red-700');
    expect(component.claseBadge(multaPagada)).toBe('bg-green-100 text-green-700');
    expect(component.claseBadge(multaParcial)).toBe('bg-amber-100 text-amber-700');
  });

  it('etiquetaEstado devuelve el texto correcto para cada estado', () => {
    const multaPendiente = { ...mockMulta1, estadoMultaId: 1, montoPagado: 0 };
    const multaPagada = { ...mockMulta2, estadoMultaId: 2, montoPagado: 10 };
    const multaParcial = { ...mockMulta1, estadoMultaId: 1, montoPagado: 2 };

    expect(component.etiquetaEstado(multaPendiente)).toBe('Pendiente');
    expect(component.etiquetaEstado(multaPagada)).toBe('Pagada');
    expect(component.etiquetaEstado(multaParcial)).toBe('Parcial');
  });

  it('muestra errorMsg sin romper la UI si el backend falla al cargar multas', () => {
    multaService.listarDetallePorUsuario.and.returnValue(throwError(() => ({ status: 500 })));

    fixture.detectChanges();

    expect(component.errorMsg).toBe('Error al cargar las multas.');
    expect(component.cargando).toBeFalse();
  });

  it('calcula totalPendiente sumando solo el saldo de multas con estado PENDIENTE', () => {
    multaService.listarDetallePorUsuario.and.returnValue(of({
      content: [
        { ...mockMulta1, id: 1, estadoMultaId: 1, saldo: 5 },
        { ...mockMulta2, id: 2, estadoMultaId: 2, saldo: 0 },
        { ...mockMulta1, id: 3, estadoMultaId: 1, saldo: 3 }
      ],
      totalPages: 1,
      totalElements: 3,
      size: 5,
      number: 0,
      numberOfElements: 3,
      empty: false
    }));

    fixture.detectChanges();

    expect(component.totalPendiente).toBe(8);
  });

  it('permite buscar un usuario por correo para bibliotecario/gerente', () => {
    authService.hasRole.and.returnValue(false);
    fixture.detectChanges();

    component.onBuscarAhora('juan.perez@uteq.edu.ec');

    expect(prestamoService.buscarUsuarioPorCorreo).toHaveBeenCalledWith('juan.perez@uteq.edu.ec');
    expect(component.usuarioSeleccionado?.id).toBe(3);
    expect(multaService.listarDetallePorUsuario).toHaveBeenCalledWith(3, jasmine.anything());
  });

  it('registra un pago parcial correctamente', () => {
    fixture.detectChanges();
    multaService.pagoParcial.and.returnValue(of({
      o_multa_id: 1,
      o_estado: 'PARCIAL',
      o_saldo_restante: 2,
      o_usuario_desbloqueado: false
    }));

    component.abrirModalPago(mockMulta1);
    component.montoRecibido = 3;
    component.confirmarPago();

    expect(multaService.pagoParcial).toHaveBeenCalledWith(1, 3);
    expect(component.exitoMsg).toContain('Pago registrado correctamente');
  });

  it('permite anular una multa a roles autorizados (GERENTE/ADMIN)', () => {
    authService.hasRole.and.callFake((...roles: string[]) => roles.includes('GERENTE'));
    fixture.detectChanges();

    expect(component.puedeAnular()).toBeTrue();

    multaService.anular.and.returnValue(of({
      multaId: 1,
      usuarioDesbloqueado: false
    }));

    component.abrirModalAnulacion(mockMulta1);
    component.motivoAnulacion = 'Error administrativo comprobado';
    component.confirmarAnulacion();

    expect(multaService.anular).toHaveBeenCalledWith(1, 'Error administrativo comprobado');
    expect(component.exitoMsg).toContain('Multa anulada correctamente');
  });
});