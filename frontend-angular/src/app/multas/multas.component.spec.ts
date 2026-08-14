import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MultasComponent } from './multas.component';
import { AuthService } from '../core/services/auth.service';

describe('MultasComponent', () => {
  let component: MultasComponent;
  let fixture: ComponentFixture<MultasComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MultasComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { getUserId: () => 3, hasRole: (...roles: string[]) => false } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MultasComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('carga el listado inicial de multas propias correctamente (rol lector)', () => {
    fixture.detectChanges(); // ngOnInit -> como es lector, busca automaticamente

    const req = httpMock.expectOne(
      r => r.url.startsWith('https://sgb-backend-b058.onrender.com/api/v1/multas/usuario/3')
    );
    req.flush({ content: [{ id: 1, monto: 5, estadoMultaId: 1 }], totalPages: 1 });

    expect(component.multas.length).toBe(1);
    expect(component.errorMsg).toBe('');
    expect(component.puedeGestionar).toBeFalse();
  });

  it('muestra errorMsg sin romper la UI si el backend falla', () => {
    fixture.detectChanges();

    const req = httpMock.expectOne(
      r => r.url.startsWith('https://sgb-backend-b058.onrender.com/api/v1/multas/usuario/3')
    );
    req.flush('error', { status: 500, statusText: 'Server Error' });

    expect(component.errorMsg).toBe('Error al buscar las multas');
    expect(component.cargando).toBeFalse();
  });
});