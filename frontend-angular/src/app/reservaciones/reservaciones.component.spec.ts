import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { ReservacionesComponent } from './reservaciones.component';
import { AuthService } from '../core/services/auth.service';
import { ReservacionService } from '../core/services/reservacion.service';
import { PrestamoService } from '../core/services/prestamo.service';
import { LibroService } from '../core/services/libro.service';

describe('ReservacionesComponent', () => {
  let component: ReservacionesComponent;
  let fixture: ComponentFixture<ReservacionesComponent>;
  let reservacionService: jasmine.SpyObj<ReservacionService>;
  let prestamoService: jasmine.SpyObj<PrestamoService>;
  let libroService: jasmine.SpyObj<LibroService>;
  let roles: string[];

  beforeEach(async () => {
    roles = ['LECTOR'];
    reservacionService = jasmine.createSpyObj('ReservacionService', [
      'listarPorUsuario', 'crear', 'cambiarEstado', 'buscarUsuarioPorCorreo', 'historialReservaciones'
    ]);
    prestamoService = jasmine.createSpyObj('PrestamoService', ['sugerenciasUsuarios']);
    libroService = jasmine.createSpyObj('LibroService', ['obtener', 'sugerencias', 'obtenerPortada']);

    reservacionService.buscarUsuarioPorCorreo.and.returnValue(of({} as any));
    reservacionService.historialReservaciones.and.returnValue(of([]));
    prestamoService.sugerenciasUsuarios.and.returnValue(of([]));
    libroService.sugerencias.and.returnValue(of([]));
    libroService.obtener.and.returnValue(of({} as any));

    await TestBed.configureTestingModule({
      imports: [ReservacionesComponent],
      providers: [
        { provide: ReservacionService, useValue: reservacionService },
        { provide: PrestamoService, useValue: prestamoService },
        { provide: LibroService, useValue: libroService },
        { provide: ActivatedRoute, useValue: { snapshot: {} } },
        {
          provide: AuthService,
          useValue: {
            getUserId: () => 2,
            hasRole: (...r: string[]) => r.some(rol => roles.includes(rol))
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ReservacionesComponent);
    component = fixture.componentInstance;
  });

  it('carga el listado inicial de reservaciones propias correctamente (rol lector)', () => {
    reservacionService.listarPorUsuario.and.returnValue(
      of({ content: [{ id: 1, libroId: 9, estadoReservacionId: 1 }], totalPages: 1 } as any)
    );

    fixture.detectChanges(); // ngOnInit -> como es lector, busca automaticamente

    expect(reservacionService.listarPorUsuario).toHaveBeenCalledWith(2, jasmine.anything());
    expect(component.reservaciones.length).toBe(1);
    expect(component.errorMsg).toBe('');
  });

  it('separa "Pendientes de retiro" del "Historial" en el modo lector', () => {
    reservacionService.listarPorUsuario.and.returnValue(
      of({
        content: [
          { id: 1, libroId: 9, estadoReservacionId: 1 },
          { id: 2, libroId: 8, estadoReservacionId: 3 }
        ],
        totalPages: 1
      } as any)
    );

    fixture.detectChanges();

    expect(component.pendientesDeRetiro.length).toBe(1);
    expect(component.historialLector.length).toBe(1);
    expect(component.pendientesDeRetiro[0].estadoReservacionId).toBe(1);
    expect(component.historialLector[0].estadoReservacionId).toBe(3);
  });

  it('el modo gestión crea la reservación con usuario y libro seleccionado', () => {
    roles = ['BIBLIOTECARIO'];

    fixture.detectChanges();

    expect(component.esLector).toBeFalse();
    expect(reservacionService.listarPorUsuario).not.toHaveBeenCalled();

    component.usuario = {
      id: 1,
      nombreCompleto: 'Juan Perez',
      correo: 'juan@uteq.edu.ec',
      estadoCuenta: 'ACTIVO',
      cantidadReservasActivas: 0,
      limiteReservas: 3
    };
    component.libroSeleccionado = {
      id: 9,
      titulo: 'Clean Code',
      disponible: true
    };
    component.fechaRetiro = '2026-08-25';

    reservacionService.crear.and.returnValue(of({
      id: 9, usuarioId: 1, libroId: 9, estadoReservacionId: 1,
      fechaReserva: '', fechaLimiteRetiro: ''
    } as any));

    component.crearReservacion();

    expect(reservacionService.crear).toHaveBeenCalledWith(jasmine.objectContaining({
      usuarioId: 1,
      libroId: 9
    }));
  });

  it('resuelve el título del libro con LibroService.obtener y lo cachea por id', () => {
    libroService.obtener.and.returnValue(of({ id: 9, titulo: 'Refactoring' } as any));

    expect(component.tituloLibro(9)).toBe('Libro #9'); // placeholder mientras carga
    expect(component.tituloLibro(9)).toBe('Refactoring'); // cacheado

    expect(libroService.obtener).toHaveBeenCalledTimes(1);
  });

  it('muestra errorMsg sin romper la UI si el backend falla', () => {
    reservacionService.listarPorUsuario.and.returnValue(throwError(() => ({ status: 500 })));

    fixture.detectChanges();

    expect(component.errorMsg).toBe('Error al buscar las reservaciones');
    expect(component.cargando).toBeFalse();
  });

  it('el staff acepta una reservación pendiente y recarga la página', () => {
    roles = ['BIBLIOTECARIO'];
    fixture.detectChanges();
    reservacionService.cambiarEstado.and.returnValue(of({ id: 7 } as any));
    reservacionService.listarPorUsuario.and.returnValue(of({ content: [], totalPages: 1 } as any));

    component.usuarioIdBusqueda = 2;
    component.cambiarEstadoReservacion(
      { id: 7, libroId: 9, estadoReservacionId: 1 } as any, 'LISTA_PARA_RETIRO');

    expect(reservacionService.cambiarEstado).toHaveBeenCalledWith(7, { nuevoEstado: 'LISTA_PARA_RETIRO' });
    expect(reservacionService.listarPorUsuario).toHaveBeenCalled();
  });

  it('muestra el detail del backend si el PATCH de estado falla', () => {
    roles = ['GERENTE'];
    fixture.detectChanges();
    reservacionService.cambiarEstado.and.returnValue(
      throwError(() => ({ error: { detail: 'Solo se puede aceptar o rechazar una reservación pendiente.' } }))
    );

    component.cambiarEstadoReservacion(
      { id: 7, libroId: 9, estadoReservacionId: 1 } as any, 'CANCELADA');

    expect(component.errorMsg).toContain('pendiente');
    expect(component.accionandoId).toBeNull();
  });
});