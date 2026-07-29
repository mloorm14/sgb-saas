import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PrestamosLectorComponent } from './prestamos-lector.component';
import { AuthService } from '../core/services/auth.service';

describe('PrestamosLectorComponent', () => {
  let component: PrestamosLectorComponent;
  let fixture: ComponentFixture<PrestamosLectorComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PrestamosLectorComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { getUserId: () => 1 } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PrestamosLectorComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('carga el listado inicial de prestamos propios correctamente', () => {
    fixture.detectChanges(); // dispara ngOnInit -> cargarPrestamos()

    const req = httpMock.expectOne(
      r => r.url === 'http://localhost:8080/api/v1/prestamos/usuario/1'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [{ id: 1, libroId: 5, estadoPrestamoId: 1 }], totalPages: 1 });

    expect(component.prestamos.length).toBe(1);
    expect(component.errorMsg).toBe('');
  });

  it('muestra errorMsg sin romper la UI si el backend falla', () => {
    fixture.detectChanges();

    const req = httpMock.expectOne(
      r => r.url.startsWith('http://localhost:8080/api/v1/prestamos/usuario/1')
    );
    req.flush('error', { status: 500, statusText: 'Server Error' });

    expect(component.errorMsg).toBe('Error al cargar tus préstamos');
    expect(component.cargando).toBeFalse();
  });
});