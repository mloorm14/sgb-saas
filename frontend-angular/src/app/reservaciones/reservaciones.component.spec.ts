import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ReservacionesComponent } from './reservaciones.component';
import { AuthService } from '../core/services/auth.service';
import { ReservacionService } from '../core/services/reservacion.service';
import { LibroService } from '../core/services/libro.service';

describe('ReservacionesComponent', () => {
  let component: ReservacionesComponent;
  let fixture: ComponentFixture<ReservacionesComponent>;
  let reservacionService: jasmine.SpyObj<ReservacionService>;
  let libroService: jasmine.SpyObj<LibroService>;

  beforeEach(async () => {
    reservacionService = jasmine.createSpyObj('ReservacionService', ['listarPorUsuario', 'crear']);
    libroService = jasmine.createSpyObj('LibroService', ['obtener', 'sugerencias']);
    libroService.sugerencias.and.returnValue(of([]));
    libroService.obtener.and.returnValue(of({} as any));

    await TestBed.configureTestingModule({
      imports: [ReservacionesComponent],
      providers: [
        { provide: ReservacionService, useValue: reservacionService },
        { provide: LibroService, useValue: libroService },
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

  it('guarda el libro elegido en el buscador predictivo en el formulario', () => {
    component.onLibroSeleccionado({ id: 9, titulo: 'Refactoring', disponible: true });

    expect(component.formCrear.get('libroId')!.value).toBe(9);
  });

  it('invalida el libroId cuando el buscador descarta la selección', () => {
    component.formCrear.patchValue({ libroId: 9 });
    component.onLibroSeleccionado(null);

    expect(component.formCrear.get('libroId')!.value).toBe('');
    expect(component.formCrear.invalid).toBeTrue();
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
});