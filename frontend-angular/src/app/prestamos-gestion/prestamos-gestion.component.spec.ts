import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { PrestamosGestionComponent } from './prestamos-gestion.component';
import { PrestamoService } from '../core/services/prestamo.service';
import { LibroService } from '../core/services/libro.service';
import { UsuarioPrestamos } from '../core/models/prestamos-gestion.model';

// Usuario activo sin multas (Casos A/B)
function usuarioActivo(): UsuarioPrestamos {
  return {
    id: 7,
    nombreCompleto: 'Ana Pérez',
    cedula: '1712345678',
    correo: 'ana@uteq.edu.ec',
    tiposUsuario: ['LECTOR'],
    estadoCuenta: 'ACTIVO',
    montoMultasPendientes: 0,
    cantidadMultasPendientes: 0,
    diasPrestamoSugerido: 15
  };
}

describe('PrestamosGestionComponent', () => {
  let component: PrestamosGestionComponent;
  let fixture: ComponentFixture<PrestamosGestionComponent>;
  let prestamoService: jasmine.SpyObj<PrestamoService>;
  let libroService: jasmine.SpyObj<LibroService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    prestamoService = jasmine.createSpyObj('PrestamoService', [
      'buscarUsuarioPorCedula', 'reservaActiva', 'historial', 'crear', 'devolver'
    ]);
    libroService = jasmine.createSpyObj('LibroService', ['sugerencias', 'obtener']);
    router = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [PrestamosGestionComponent],
      providers: [
        { provide: PrestamoService, useValue: prestamoService },
        { provide: LibroService, useValue: libroService },
        { provide: Router, useValue: router }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PrestamosGestionComponent);
    component = fixture.componentInstance;
  });

  it('busca al usuario por cédula y carga reserva e historial', () => {
    prestamoService.buscarUsuarioPorCedula.and.returnValue(of(usuarioActivo()));
    prestamoService.reservaActiva.and.returnValue(throwError(() => ({ status: 404 })));
    prestamoService.historial.and.returnValue(of([]));

    component.cedulaBusqueda = '1712345678';
    component.buscarUsuario();

    expect(prestamoService.buscarUsuarioPorCedula).toHaveBeenCalledWith('1712345678');
    expect(component.usuario?.id).toBe(7);
    // Días de préstamo prellenados con el valor de configuración del sistema
    expect(component.diasReserva).toBe(15);
    expect(component.diasDirecto).toBe(15);
    expect(component.errorCedula).toBe('');
    expect(prestamoService.historial).toHaveBeenCalledWith(7);
  });

  it('muestra el mensaje de error cuando la cédula no corresponde a ningún usuario', () => {
    prestamoService.buscarUsuarioPorCedula.and.returnValue(
      throwError(() => ({ status: 404 }))
    );

    component.cedulaBusqueda = '9999999999';
    component.buscarUsuario();

    expect(component.errorCedula).toBe('No se encontró ningún usuario con esta cédula');
    expect(component.usuario).toBeNull();
  });

  it('rechaza una cédula con formato inválido sin llamar al backend', () => {
    component.cedulaBusqueda = '123';
    component.buscarUsuario();

    expect(component.errorCedula).toBe('Ingresa una cédula válida de 10 dígitos');
    expect(prestamoService.buscarUsuarioPorCedula).not.toHaveBeenCalled();
  });

  it('detecta el Caso C (bloqueado por multas pendientes) y no consulta reservas', () => {
    const bloqueado: UsuarioPrestamos = {
      ...usuarioActivo(),
      estadoCuenta: 'BLOQUEADO_POR_MULTA',
      montoMultasPendientes: 3.5,
      cantidadMultasPendientes: 1
    };
    prestamoService.buscarUsuarioPorCedula.and.returnValue(of(bloqueado));
    prestamoService.historial.and.returnValue(of([]));

    component.cedulaBusqueda = '1712345678';
    component.buscarUsuario();

    expect(component.estaBloqueado).toBeTrue();
    expect(component.motivoBloqueo).toContain('$3.50');
    expect(prestamoService.reservaActiva).not.toHaveBeenCalled();
  });

  it('confirma la entrega de una reserva creando el préstamo con reservacionId', () => {
    prestamoService.crear.and.returnValue(of({ id: 99 } as any));
    // Tras crear, la pantalla refresca reserva (ya retirada -> 404) e historial
    prestamoService.reservaActiva.and.returnValue(throwError(() => ({ status: 404 })));
    prestamoService.historial.and.returnValue(of([]));

    component.usuario = usuarioActivo();
    component.reserva = {
      reservacionId: 77,
      libroId: 3,
      titulo: 'Clean Code',
      autores: ['Robert C. Martin'],
      isbn: '9780132350884',
      fechaReserva: '2026-08-20T10:00:00Z',
      fechaLimiteRetiro: '2026-08-21T10:00:00Z',
      diasPrestamoSugerido: 15
    };
    component.diasReserva = 15;

    component.confirmarEntrega();

    expect(prestamoService.crear).toHaveBeenCalledWith({
      usuarioId: 7,
      libroId: 3,
      diasPrestamo: 15,
      reservacionId: 77
    });
  });

  it('bloquea el registro directo cuando el libro no tiene ejemplares disponibles', () => {
    component.libroSeleccionado = {
      id: 3,
      titulo: 'Clean Code',
      isbn: '9780132350884',
      resumen: '',
      portadaUrl: '',
      tienePortada: false,
      portadaNombre: '',
      portadaTipo: '',
      anioPublicacion: 2008,
      editorialId: 1,
      editorial: '',
      idiomaId: 1,
      idioma: '',
      estadoId: 1,
      estado: 'ACTIVO',
      stockTotal: 2,
      stockDisponible: 0,
      ubicacionFisica: '',
      fechaRegistro: '',
      categorias: [],
      autores: []
    };

    expect(component.puedeRegistrarDirecto).toBeFalse();
  });

  it('registra una devolución exitosa sin multa y refresca el historial', () => {
    window.confirm = jasmine.createSpy('confirm').and.returnValue(true);
    prestamoService.devolver.and.returnValue(of({ prestamoId: 1, huboMulta: false, montoMulta: 0 }));
    prestamoService.reservaActiva.and.returnValue(throwError(() => ({ status: 404 })));
    prestamoService.historial.and.returnValue(of([]));

    component.usuario = usuarioActivo();
    component.registrarDevolucion(1);

    expect(prestamoService.devolver).toHaveBeenCalledWith(1);
    expect(component.exitoMsg).toContain('devuelto correctamente');
    expect(component.avisoDevolucion).toBe('');
    expect(component.devolviendoPrestamoId).toBeNull();
  });

  it('muestra aviso de multa cuando la devolución genera multa por atraso', () => {
    window.confirm = jasmine.createSpy('confirm').and.returnValue(true);
    prestamoService.devolver.and.returnValue(of({ prestamoId: 2, huboMulta: true, montoMulta: 5.50 }));
    prestamoService.reservaActiva.and.returnValue(throwError(() => ({ status: 404 })));
    prestamoService.historial.and.returnValue(of([]));

    component.usuario = usuarioActivo();
    component.registrarDevolucion(2);

    expect(component.avisoDevolucion).toContain('Devuelto tarde');
    expect(component.avisoDevolucion).toContain('$5.50');
    expect(component.exitoMsg).toBe('');
  });

  it('muestra error cuando la devolución falla', () => {
    window.confirm = jasmine.createSpy('confirm').and.returnValue(true);
    prestamoService.devolver.and.returnValue(throwError(() => ({ status: 500, error: { detail: 'Préstamo no encontrado' } })));

    component.usuario = usuarioActivo();
    component.registrarDevolucion(999);

    expect(component.errorMsgAccion).toBe('Préstamo no encontrado');
    expect(component.devolviendoPrestamoId).toBeNull();
  });
});
