import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ReservacionesComponent } from './reservaciones.component';
import { AuthService } from '../core/services/auth.service';

describe('ReservacionesComponent', () => {
  let component: ReservacionesComponent;
  let fixture: ComponentFixture<ReservacionesComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReservacionesComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { getUserId: () => 2, hasRole: (...roles: string[]) => roles.includes('LECTOR') } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ReservacionesComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('carga el listado inicial de reservaciones propias correctamente (rol lector)', () => {
    fixture.detectChanges(); // ngOnInit -> como es lector, busca automaticamente

    const req = httpMock.expectOne(
      r => r.url.startsWith('http://localhost:8080/api/v1/reservaciones/usuario/2')
    );
    req.flush({ content: [{ id: 1, libroId: 9, estadoReservacionId: 1 }], totalPages: 1 });

    expect(component.reservaciones.length).toBe(1);
    expect(component.errorMsg).toBe('');
  });

  it('muestra errorMsg sin romper la UI si el backend falla', () => {
    fixture.detectChanges();

    const req = httpMock.expectOne(
      r => r.url.startsWith('http://localhost:8080/api/v1/reservaciones/usuario/2')
    );
    req.flush('error', { status: 500, statusText: 'Server Error' });

    expect(component.errorMsg).toBe('Error al buscar las reservaciones');
    expect(component.cargando).toBeFalse();
  });
});