import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { PrestamosGestionComponent } from './prestamos-gestion.component';
import { PrestamoService } from '../core/services/prestamo.service';
import { LibroService } from '../core/services/libro.service';
import { UsuarioPrestamos } from '../core/models/prestamos-gestion.model';

function usuarioActivo(): UsuarioPrestamos {
  return {
    id: 7,
    nombreCompleto: 'Ana Pérez',
    cedula: '1712345678',
    correo: 'ana.perez@correo.com',
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
      'buscarUsuarioPorCorreo', 'reservaActiva', 'historial', 'crear', 'sugerenciasUsuarios'
    ]);
    libroService = jasmine.createSpyObj('LibroService', ['sugerencias', 'obtener', 'obtenerPortada']);
    router = jasmine.createSpyObj('Router', ['navigate']);

    prestamoService.buscarUsuarioPorCorreo.and.returnValue(of(usuarioActivo()));
    prestamoService.reservaActiva.and.returnValue(throwError(() => ({ status: 404 })));
    prestamoService.historial.and.returnValue(of([]));
    prestamoService.sugerenciasUsuarios.and.returnValue(of([]));

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

  it('busca al usuario por correo y carga reserva e historial', () => {
    component.correoBusqueda = 'ana.perez@correo.com';
    component.buscarUsuario();

    expect(prestamoService.buscarUsuarioPorCorreo).toHaveBeenCalledWith('ana.perez@correo.com');
    expect(component.usuario?.id).toBe(7);
    expect(component.diasReserva).toBe(15);
    expect(component.errorBusqueda).toBe('');
    expect(prestamoService.historial).toHaveBeenCalledWith(7);
  });

  it('muestra el mensaje de error cuando el correo no corresponde a ningún usuario', () => {
    prestamoService.buscarUsuarioPorCorreo.and.returnValue(
      throwError(() => ({ status: 404 }))
    );

    component.correoBusqueda = 'nadie@correo.com';
    component.buscarUsuario();

    expect(component.errorBusqueda).toBe('No se encontró ningún usuario con este correo');
    expect(component.usuario).toBeNull();
  });

  it('rechaza un correo con menos de 2 caracteres sin llamar al backend', () => {
    component.correoBusqueda = 'a';
    component.buscarUsuario();

    expect(component.errorBusqueda).toBe('Ingresa un correo electrónico válido');
    expect(prestamoService.buscarUsuarioPorCorreo).not.toHaveBeenCalled();
  });

  it('detecta el Caso C (bloqueado por multas pendientes) y no consulta reservas', () => {
    const bloqueado: UsuarioPrestamos = {
      ...usuarioActivo(),
      estadoCuenta: 'BLOQUEADO_POR_MULTA',
      montoMultasPendientes: 3.5,
      cantidadMultasPendientes: 1
    };
    prestamoService.buscarUsuarioPorCorreo.and.returnValue(of(bloqueado));

    component.correoBusqueda = 'ana.perez@correo.com';
    component.buscarUsuario();

    expect(component.estaBloqueado).toBeTrue();
    expect(component.motivoBloqueo).toContain('$3.50');
    expect(prestamoService.reservaActiva).not.toHaveBeenCalled();
  });

  it('confirma la entrega de una reserva creando el préstamo con reservacionId', () => {
    prestamoService.crear.and.returnValue(of({ id: 99 } as any));

    component.usuario = usuarioActivo();
    component.reserva = {
      reservacionId: 77,
      libroId: 3,
      titulo: 'Clean Code',
      autores: ['Robert C. Martin'],
      isbn: '9780132350884',
      fechaReserva: '2026-08-20T10:00:00Z',
      fechaLimiteRetiro: '2026-08-21T10:00:00Z',
      diasPrestamoSugerido: 15,
      anioPublicacion: 2026,
      stockDisponible: 3,
      stockTotal: 5,
      ubicacionFisica: 'Estante A1',
      categorias: ['Ficción'],
      tienePortada: false
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
});
