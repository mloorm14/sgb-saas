import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { PrestamosLectorComponent } from './prestamos-lector.component';
import { AuthService } from '../core/services/auth.service';
import { PrestamoService } from '../core/services/prestamo.service';
import { LibroService } from '../core/services/libro.service';

describe('PrestamosLectorComponent', () => {
  let component: PrestamosLectorComponent;
  let fixture: ComponentFixture<PrestamosLectorComponent>;
  let prestamoService: jasmine.SpyObj<PrestamoService>;
  let libroService: jasmine.SpyObj<LibroService>;

  beforeEach(async () => {
    prestamoService = jasmine.createSpyObj('PrestamoService', ['listarPorUsuario', 'activosPorUsuario', 'renovar']);
    libroService = jasmine.createSpyObj('LibroService', ['obtener']);
    prestamoService.listarPorUsuario.and.returnValue(of({ content: [], totalPages: 1 } as any));
    prestamoService.activosPorUsuario.and.returnValue(of([]));
    libroService.obtener.and.returnValue(of({} as any));

    await TestBed.configureTestingModule({
      imports: [PrestamosLectorComponent],
      providers: [
        { provide: PrestamoService, useValue: prestamoService },
        { provide: LibroService, useValue: libroService },
        { provide: AuthService, useValue: { getUserId: () => 1 } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PrestamosLectorComponent);
    component = fixture.componentInstance;
  });

  it('carga los préstamos activos y el historial propio al iniciar', () => {
    prestamoService.activosPorUsuario.and.returnValue(of([{ prestamoId: 1, libroTitulo: 'Clean Code', diasRestantes: 5 } as any]));
    prestamoService.listarPorUsuario.and.returnValue(of({ content: [{ id: 1, libroId: 5, estadoPrestamoId: 1 }], totalPages: 1 } as any));

    fixture.detectChanges();

    expect(prestamoService.activosPorUsuario).toHaveBeenCalledWith(1);
    expect(prestamoService.listarPorUsuario).toHaveBeenCalledWith(1, jasmine.anything());
    expect(component.prestamosActivos.length).toBe(1);
    expect(component.prestamos.length).toBe(1);
  });

  it('resuelve el título del historial con LibroService.obtener y lo cachea por id', () => {
    libroService.obtener.and.returnValue(of({ id: 5, titulo: 'Clean Code' } as any));

    expect(component.tituloLibro(5)).toBe('Libro #5'); // placeholder mientras carga
    expect(component.tituloLibro(5)).toBe('Clean Code'); // cacheado

    expect(libroService.obtener).toHaveBeenCalledTimes(1);
  });

  it('renueva un préstamo activo y recarga la lista de activos', () => {
    prestamoService.activosPorUsuario.and.returnValue(of([{ prestamoId: 1, diasRestantes: 5 } as any]));
    prestamoService.renovar.and.returnValue(of({ prestamoId: 1 } as any));

    fixture.detectChanges();
    component.renovar(1);

    expect(prestamoService.renovar).toHaveBeenCalledWith(1);
    expect(prestamoService.activosPorUsuario).toHaveBeenCalledTimes(2);
    expect(component.renovandoId).toBeNull();
  });

  it('muestra errorMsg sin romper la UI si el historial falla', () => {
    prestamoService.listarPorUsuario.and.returnValue(throwError(() => ({ status: 500 })));

    fixture.detectChanges();

    expect(component.errorMsg).toBe('Error al cargar tus préstamos');
    expect(component.cargando).toBeFalse();
  });
});