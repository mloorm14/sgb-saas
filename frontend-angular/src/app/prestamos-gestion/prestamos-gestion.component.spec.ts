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
    correo: 'ana.perez@uteq.edu.ec',
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
      'buscarUsuarioPorCorreo', 'historial', 'crear', 'sugerenciasUsuarios'
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

  it('busca al usuario por correo y carga historial', () => {
    prestamoService.buscarUsuarioPorCorreo.and.returnValue(of(usuarioActivo()));
    prestamoService.historial.and.returnValue(of([]));

    component.correoBusqueda = 'ana.perez@uteq.edu.ec';
    component.buscarUsuario();

    expect(prestamoService.buscarUsuarioPorCorreo).toHaveBeenCalledWith('ana.perez@uteq.edu.ec');
    expect(component.usuario?.id).toBe(7);
    expect(component.diasPrestamo).toBe(15);
    expect(component.errorBusqueda).toBe('');
    expect(prestamoService.historial).toHaveBeenCalledWith(7);
  });

  it('muestra el mensaje de error cuando el correo no corresponde a ningún usuario', () => {
    prestamoService.buscarUsuarioPorCorreo.and.returnValue(
      throwError(() => ({ status: 404 }))
    );

    component.correoBusqueda = 'nadie@uteq.edu.ec';
    component.buscarUsuario();

    expect(component.errorBusqueda).toBe('No se encontró ningún usuario con este correo');
    expect(component.usuario).toBeNull();
  });

  it('rechaza un correo con formato inválido sin llamar al backend', () => {
    component.correoBusqueda = 'correo-sin-arroba';
    component.buscarUsuario();

    expect(component.errorBusqueda).toBe('Ingresa un correo electrónico válido');
    expect(prestamoService.buscarUsuarioPorCorreo).not.toHaveBeenCalled();
  });

  it('detecta el Caso C (bloqueado por multas pendientes)', () => {
    const bloqueado: UsuarioPrestamos = {
      ...usuarioActivo(),
      estadoCuenta: 'BLOQUEADO_POR_MULTA',
      montoMultasPendientes: 3.5,
      cantidadMultasPendientes: 1
    };
    prestamoService.buscarUsuarioPorCorreo.and.returnValue(of(bloqueado));
    prestamoService.historial.and.returnValue(of([]));

    component.correoBusqueda = 'ana.perez@uteq.edu.ec';
    component.buscarUsuario();

    expect(component.estaBloqueado).toBeTrue();
    expect(component.motivoBloqueo).toContain('$3.50');
  });

  it('bloquea el registro cuando el libro no tiene ejemplares disponibles', () => {
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

    expect(component.puedeRegistrarPrestamo).toBeFalse();
  });
});
