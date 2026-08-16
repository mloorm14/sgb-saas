import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { PrestamosLectorComponent } from './prestamos-lector.component';
import { AuthService } from '../core/services/auth.service';
import { PrestamoService } from '../core/services/prestamo.service';

describe('PrestamosLectorComponent', () => {
  let component: PrestamosLectorComponent;
  let fixture: ComponentFixture<PrestamosLectorComponent>;
  let prestamoService: jasmine.SpyObj<PrestamoService>;

  beforeEach(async () => {
    prestamoService = jasmine.createSpyObj('PrestamoService', ['listarPorUsuario', 'crear', 'devolver', 'renovar', 'activosPorUsuario']);

    await TestBed.configureTestingModule({
      imports: [PrestamosLectorComponent],
      providers: [
        { provide: PrestamoService, useValue: prestamoService },
        { provide: AuthService, useValue: { getUserId: () => 1 } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PrestamosLectorComponent);
    component = fixture.componentInstance;
  });

  it('carga el listado inicial de prestamos propios correctamente', () => {
    prestamoService.listarPorUsuario.and.returnValue(
      of({ content: [{ id: 1, libroId: 5, estadoPrestamoId: 1 }], totalPages: 1 } as any)
    );

    fixture.detectChanges(); // dispara ngOnInit -> cargarPrestamos()

    expect(prestamoService.listarPorUsuario).toHaveBeenCalledWith(1, jasmine.anything());
    expect(component.prestamos.length).toBe(1);
    expect(component.errorMsg).toBe('');
  });

  it('muestra errorMsg sin romper la UI si el backend falla', () => {
    prestamoService.listarPorUsuario.and.returnValue(throwError(() => ({ status: 500 })));

    fixture.detectChanges();

    expect(component.errorMsg).toBe('Error al cargar tus préstamos');
    expect(component.cargando).toBeFalse();
  });
});
