import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AdminUsuariosComponent } from './admin-usuarios.component';
import { AuthService } from '../core/services/auth.service';

describe('AdminUsuariosComponent', () => {
  let component: AdminUsuariosComponent;
  let fixture: ComponentFixture<AdminUsuariosComponent>;
  let httpMock: HttpTestingController;

  async function configurar(rol: string): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [AdminUsuariosComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { hasRole: (...roles: string[]) => roles.includes(rol) } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AdminUsuariosComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('un ADMIN ve el listado y tiene habilitadas las acciones de gestión', async () => {
    await configurar('ADMIN');
    fixture.detectChanges(); // ngOnInit -> como puedeVer, carga el listado

    const req = httpMock.expectOne(r => r.url.startsWith('http://localhost:8080/api/v1/admin/usuarios'));
    req.flush({
      content: [{ id: 1, nombre: 'Ana', apellido: 'Ruiz', correo: 'ana@test.com', roles: ['LECTOR'], estado: 'ACTIVO', multasPendientes: false }],
      totalPages: 1
    });

    expect(component.usuarios.length).toBe(1);
    expect(component.puedeVer).toBeTrue();
    expect(component.puedeGestionar).toBeTrue();
    expect(component.errorMsg).toBe('');

    fixture.detectChanges();
    const botonesAccion = fixture.nativeElement.querySelectorAll('td button');
    expect(botonesAccion.length).toBe(2); // "Cambiar rol" + "Cambiar estado"
  });

  it('un GERENTE ve el listado en modo solo lectura (sin botones de gestión)', async () => {
    await configurar('GERENTE');
    fixture.detectChanges();

    const req = httpMock.expectOne(r => r.url.startsWith('http://localhost:8080/api/v1/admin/usuarios'));
    req.flush({
      content: [{ id: 1, nombre: 'Ana', apellido: 'Ruiz', correo: 'ana@test.com', roles: ['LECTOR'], estado: 'ACTIVO', multasPendientes: false }],
      totalPages: 1
    });

    expect(component.puedeVer).toBeTrue();
    expect(component.puedeGestionar).toBeFalse();

    fixture.detectChanges();
    const botonesAccion = fixture.nativeElement.querySelectorAll('td button');
    expect(botonesAccion.length).toBe(0);
  });

  it('un LECTOR no dispara la carga del listado (sin permiso para ver)', async () => {
    await configurar('LECTOR');
    fixture.detectChanges();

    httpMock.expectNone(r => r.url.startsWith('http://localhost:8080/api/v1/admin/usuarios'));
    expect(component.puedeVer).toBeFalse();
  });

  it('muestra errorMsg sin romper la UI si el backend falla al listar', async () => {
    await configurar('ADMIN');
    fixture.detectChanges();

    const req = httpMock.expectOne(r => r.url.startsWith('http://localhost:8080/api/v1/admin/usuarios'));
    req.flush('error', { status: 500, statusText: 'Server Error' });

    expect(component.errorMsg).toBe('Error al cargar el listado de usuarios');
    expect(component.cargando).toBeFalse();
  });

  it('reporta un mensaje específico si el backend rechaza el cambio de rol con 403', async () => {
    await configurar('ADMIN');
    fixture.detectChanges();

    httpMock.expectOne(r => r.url.startsWith('http://localhost:8080/api/v1/admin/usuarios'))
      .flush({
        content: [{ id: 1, nombre: 'Ana', apellido: 'Ruiz', correo: 'ana@test.com', roles: ['LECTOR'], estado: 'ACTIVO', multasPendientes: false }],
        totalPages: 1
      });

    component.abrirModalRol(component.usuarios[0]);
    component.nuevoRol = 'BIBLIOTECARIO';
    component.confirmarCambioRol();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/admin/usuarios/1/rol');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ nuevoRol: 'BIBLIOTECARIO' });
    req.flush('error', { status: 403, statusText: 'Forbidden' });

    expect(component.errorMsg).toBe('No tenés permisos para cambiar el rol de un usuario');
  });

  it('envía nuevoEstado y motivo al confirmar un cambio de estado', async () => {
    await configurar('ADMIN');
    fixture.detectChanges();

    httpMock.expectOne(r => r.url.startsWith('http://localhost:8080/api/v1/admin/usuarios'))
      .flush({
        content: [{ id: 1, nombre: 'Ana', apellido: 'Ruiz', correo: 'ana@test.com', roles: ['LECTOR'], estado: 'ACTIVO', multasPendientes: false }],
        totalPages: 1
      });

    component.abrirModalEstado(component.usuarios[0]);
    component.nuevoEstado = 'INACTIVO';
    component.motivoEstado = 'Solicitado por el propio usuario';
    component.confirmarCambioEstado();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/admin/usuarios/1/estado');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ nuevoEstado: 'INACTIVO', motivo: 'Solicitado por el propio usuario' });
    req.flush(null, { status: 204, statusText: 'No Content' });

    // Tras confirmar, recarga la página -- responde esa segunda llamada
    httpMock.expectOne(r => r.url.startsWith('http://localhost:8080/api/v1/admin/usuarios'))
      .flush({ content: [], totalPages: 0 });

    expect(component.mostrarModalEstado).toBeFalse();
  });
});
