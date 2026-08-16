import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { MultasComponent } from './multas.component';
import { AuthService } from '../core/services/auth.service';
import { MultaService } from '../core/services/multa.service';

describe('MultasComponent', () => {
  let component: MultasComponent;
  let fixture: ComponentFixture<MultasComponent>;
  let multaService: jasmine.SpyObj<MultaService>;

  beforeEach(async () => {
    multaService = jasmine.createSpyObj('MultaService', ['listarPorUsuario', 'pagar', 'anular']);

    await TestBed.configureTestingModule({
      imports: [MultasComponent],
      providers: [
        { provide: MultaService, useValue: multaService },
        { provide: AuthService, useValue: { getUserId: () => 3, hasRole: (...roles: string[]) => false } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MultasComponent);
    component = fixture.componentInstance;
  });

  it('carga el listado inicial de multas propias correctamente (rol lector)', () => {
    multaService.listarPorUsuario.and.returnValue(
      of({ content: [{ id: 1, monto: 5, estadoMultaId: 1 }], totalPages: 1 } as any)
    );

    fixture.detectChanges(); // ngOnInit -> como es lector, busca automaticamente

    expect(multaService.listarPorUsuario).toHaveBeenCalledWith(3, jasmine.anything());
    expect(component.multas.length).toBe(1);
    expect(component.errorMsg).toBe('');
    expect(component.puedeGestionar).toBeFalse();
  });

  it('muestra errorMsg sin romper la UI si el backend falla', () => {
    multaService.listarPorUsuario.and.returnValue(throwError(() => ({ status: 500 })));

    fixture.detectChanges();

    expect(component.errorMsg).toBe('Error al buscar las multas');
    expect(component.cargando).toBeFalse();
  });
});
