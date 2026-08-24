import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ConfiguracionSistemaComponent } from './configuracion-sistema.component';
import { AuthService } from '../core/services/auth.service';

describe('ConfiguracionSistemaComponent', () => {
  let component: ConfiguracionSistemaComponent;
  let fixture: ComponentFixture<ConfiguracionSistemaComponent>;
  let httpMock: HttpTestingController;

  async function configurar(rol: string): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [ConfiguracionSistemaComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { hasRole: (...roles: string[]) => roles.includes(rol) } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ConfiguracionSistemaComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('un ADMIN carga el listado de configuración y tipos de daño', async () => {
    await configurar('ADMIN');
    fixture.detectChanges();

    const reqConfig = httpMock.expectOne('http://localhost:8080/api/v1/configuracion');
    reqConfig.flush([
      { clave: 'monto_multa_diaria', valor: '0.50' },
      { clave: 'dias_prestamo_default', valor: '15' }
    ]);

    const reqDanos = httpMock.expectOne('http://localhost:8080/api/v1/tipos-dano');
    reqDanos.flush([
      { id: 1, nombre: 'Páginas rotas', precio: 5.0 }
    ]);

    expect(component.esAdmin).toBeTrue();
    expect(component.configuraciones.length).toBe(2);
    expect(component.tiposDano.length).toBe(1);
    expect(component.errorMsg).toBe('');
  });

  it('un GERENTE no dispara la carga (sin permiso, defensa en profundidad)', async () => {
    await configurar('GERENTE');
    fixture.detectChanges();

    httpMock.expectNone('http://localhost:8080/api/v1/configuracion');
    httpMock.expectNone('http://localhost:8080/api/v1/tipos-dano');
    expect(component.esAdmin).toBeFalse();

    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No tenés permisos');
  });

  it('muestra errorMsg sin romper la UI si el backend falla al listar', async () => {
    await configurar('ADMIN');
    fixture.detectChanges();

    const reqConfig = httpMock.expectOne('http://localhost:8080/api/v1/configuracion');
    reqConfig.flush('error', { status: 500, statusText: 'Server Error' });

    const reqDanos = httpMock.expectOne('http://localhost:8080/api/v1/tipos-dano');
    reqDanos.flush([]);

    expect(component.errorMsg).toBe('Error al cargar la configuración del sistema');
    expect(component.cargando).toBeFalse();
  });

  it('actualiza una clave existente con PUT al confirmar la edición', async () => {
    await configurar('ADMIN');
    fixture.detectChanges();

    httpMock.expectOne('http://localhost:8080/api/v1/configuracion')
      .flush([{ clave: 'monto_multa_diaria', valor: '0.50' }]);
    httpMock.expectOne('http://localhost:8080/api/v1/tipos-dano').flush([]);

    component.iniciarEdicion(component.configuraciones[0]);
    component.valorEditando = '0.75';
    component.guardarValor();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/configuracion/monto_multa_diaria');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ valor: '0.75' });
    req.flush({ clave: 'monto_multa_diaria', valor: '0.75' });

    // Tras guardar, recarga el listado -- responde esa segunda llamada
    httpMock.expectOne('http://localhost:8080/api/v1/configuracion')
      .flush([{ clave: 'monto_multa_diaria', valor: '0.75' }]);

    expect(component.claveEditando).toBeNull();
  });

  it('no envía la petición si el valor excede los 200 caracteres (validación cliente)', async () => {
    await configurar('ADMIN');
    fixture.detectChanges();

    httpMock.expectOne('http://localhost:8080/api/v1/configuracion')
      .flush([{ clave: 'monto_multa_diaria', valor: '0.50' }]);
    httpMock.expectOne('http://localhost:8080/api/v1/tipos-dano').flush([]);

    component.iniciarEdicion(component.configuraciones[0]);
    component.valorEditando = 'x'.repeat(201);
    component.guardarValor();

    httpMock.expectNone('http://localhost:8080/api/v1/configuracion/monto_multa_diaria');
    expect(component.claveEditando).toBe('monto_multa_diaria'); // sigue en modo edición, no se cerró
  });

  it('reporta un mensaje específico si el backend rechaza la actualización con 403', async () => {
    await configurar('ADMIN');
    fixture.detectChanges();

    httpMock.expectOne('http://localhost:8080/api/v1/configuracion')
      .flush([{ clave: 'monto_multa_diaria', valor: '0.50' }]);
    httpMock.expectOne('http://localhost:8080/api/v1/tipos-dano').flush([]);

    component.iniciarEdicion(component.configuraciones[0]);
    component.valorEditando = '1.00';
    component.guardarValor();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/configuracion/monto_multa_diaria');
    req.flush('error', { status: 403, statusText: 'Forbidden' });

    expect(component.errorMsg).toBe('No tenés permisos para modificar la configuración del sistema');
  });
});
