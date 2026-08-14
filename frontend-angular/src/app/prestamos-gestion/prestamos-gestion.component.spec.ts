import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PrestamosGestionComponent } from './prestamos-gestion.component';

describe('PrestamosGestionComponent', () => {
  let component: PrestamosGestionComponent;
  let fixture: ComponentFixture<PrestamosGestionComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PrestamosGestionComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    fixture = TestBed.createComponent(PrestamosGestionComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('carga el listado inicial de prestamos de un usuario buscado', () => {
    component.usuarioIdBusqueda = 7;
    component.buscarPrestamos();

    const req = httpMock.expectOne(
      r => r.url.startsWith('https://sgb-backend-b058.onrender.com/api/v1/prestamos/usuario/7')
    );
    req.flush({ content: [{ id: 1, usuarioId: 7, libroId: 3 }], totalPages: 1 });

    expect(component.prestamos.length).toBe(1);
    expect(component.errorMsg).toBe('');
  });

  it('muestra errorMsg sin romper la UI si la busqueda falla', () => {
    component.usuarioIdBusqueda = 7;
    component.buscarPrestamos();

    const req = httpMock.expectOne(
      r => r.url.startsWith('https://sgb-backend-b058.onrender.com/api/v1/prestamos/usuario/7')
    );
    req.flush('error', { status: 500, statusText: 'Server Error' });

    expect(component.errorMsg).toBe('Error al buscar los préstamos de ese usuario');
    expect(component.cargando).toBeFalse();
  });
});