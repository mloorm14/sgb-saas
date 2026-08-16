import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ReservacionesComponent } from './reservaciones.component';
import { AuthService } from '../core/services/auth.service';
import { ReservacionService } from '../core/services/reservacion.service';

describe('ReservacionesComponent', () => {
  let component: ReservacionesComponent;
  let fixture: ComponentFixture<ReservacionesComponent>;
  let reservacionService: jasmine.SpyObj<ReservacionService>;

  beforeEach(async () => {
    reservacionService = jasmine.createSpyObj('ReservacionService', ['listarPorUsuario', 'crear']);

    await TestBed.configureTestingModule({
      imports: [ReservacionesComponent],
      providers: [
        { provide: ReservacionService, useValue: reservacionService },
        { provide: AuthService, useValue: { getUserId: () => 2, hasRole: (...roles: string[]) => roles.includes('LECTOR') } }
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

  it('muestra errorMsg sin romper la UI si el backend falla', () => {
    reservacionService.listarPorUsuario.and.returnValue(throwError(() => ({ status: 500 })));

    fixture.detectChanges();

    expect(component.errorMsg).toBe('Error al buscar las reservaciones');
    expect(component.cargando).toBeFalse();
  });
});
