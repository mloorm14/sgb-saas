import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { PrestamosGestionComponent } from './prestamos-gestion.component';
import { PrestamoService } from '../core/services/prestamo.service';

describe('PrestamosGestionComponent', () => {
  let component: PrestamosGestionComponent;
  let fixture: ComponentFixture<PrestamosGestionComponent>;
  let prestamoService: jasmine.SpyObj<PrestamoService>;

  beforeEach(async () => {
    prestamoService = jasmine.createSpyObj('PrestamoService', ['listarPorUsuario', 'crear', 'devolver', 'renovar', 'activosPorUsuario']);

    await TestBed.configureTestingModule({
      imports: [PrestamosGestionComponent],
      providers: [{ provide: PrestamoService, useValue: prestamoService }]
    }).compileComponents();

    fixture = TestBed.createComponent(PrestamosGestionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('carga el listado inicial de prestamos de un usuario buscado', () => {
    prestamoService.listarPorUsuario.and.returnValue(
      of({ content: [{ id: 1, usuarioId: 7, libroId: 3 }], totalPages: 1 } as any)
    );

    component.usuarioIdBusqueda = 7;
    component.buscarPrestamos();

    expect(prestamoService.listarPorUsuario).toHaveBeenCalledWith(7, jasmine.anything());
    expect(component.prestamos.length).toBe(1);
    expect(component.errorMsg).toBe('');
  });

  it('muestra errorMsg sin romper la UI si la busqueda falla', () => {
    prestamoService.listarPorUsuario.and.returnValue(throwError(() => ({ status: 500 })));

    component.usuarioIdBusqueda = 7;
    component.buscarPrestamos();

    expect(component.errorMsg).toBe('Error al buscar los préstamos de ese usuario');
    expect(component.cargando).toBeFalse();
  });
});
