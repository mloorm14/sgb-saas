import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AppComponent } from './app.component';
import { AuthService } from './core/services/auth.service';

describe('AppComponent', () => {
  let authService: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authService = jasmine.createSpyObj('AuthService', ['isLoggedIn', 'hasRole', 'logout', 'getCorreo']);
    authService.isLoggedIn.and.returnValue(false);
    authService.hasRole.and.returnValue(false);
    authService.getCorreo.and.returnValue(null);

    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authService }
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it(`should have the 'SGB-SaaS' title`, () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app.title).toEqual('SGB-SaaS');
  });

  it('should render the router outlet', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('router-outlet')).toBeTruthy();
  });

  it('no muestra la barra de navegacion sin sesion activa', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('header')).toBeNull();
  });

  it('muestra los enlaces del LECTOR y cierra sesion via dropdown', () => {
    authService.isLoggedIn.and.returnValue(true);
    authService.hasRole.and.callFake((...roles: string[]) => roles.includes('LECTOR'));
    authService.getCorreo.and.returnValue('lector@uteq.edu.ec');

    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('header')).toBeTruthy();
    expect(compiled.textContent).toContain('Mis Préstamos');
    expect(compiled.textContent).toContain('Reservaciones');
    expect(compiled.textContent).not.toContain('Libros');
    expect(compiled.textContent).not.toContain('Mi Credencial');
    expect(compiled.textContent).toContain('Notificaciones');

    const menuBtn = compiled.querySelector('[data-menu-usuario] button') as HTMLButtonElement;
    menuBtn?.click();
    fixture.detectChanges();

    const botonCerrar = Array.from(compiled.querySelectorAll('button'))
      .find(b => b.textContent?.includes('Cerrar sesión'));
    botonCerrar?.click();
    fixture.detectChanges();

    // El modal de confirmación accesible reemplazó a window.confirm
    const botonConfirmar = Array.from(compiled.querySelectorAll('button'))
      .find(b => b.textContent?.includes('Confirmar'));
    botonConfirmar?.click();
    fixture.detectChanges();

    expect(authService.logout).toHaveBeenCalled();
  });

  it('muestra los enlaces de BIBLIOTECARIO/GERENTE (operacion + inventario)', () => {
    authService.isLoggedIn.and.returnValue(true);
    authService.hasRole.and.callFake((...roles: string[]) => roles.includes('BIBLIOTECARIO'));

    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Libros');
    expect(compiled.textContent).toContain('Reportes');
  });

  it('GERENTE ve reportes, sugerencias y auditoría pero NO usuarios', () => {
    authService.isLoggedIn.and.returnValue(true);
    authService.hasRole.and.callFake((...roles: string[]) => roles.includes('GERENTE'));

    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Reportes');
    expect(compiled.textContent).toContain('Sugerencias');
    expect(compiled.textContent).not.toContain('Usuarios');
    expect(compiled.textContent).toContain('Auditoría');
    expect(compiled.textContent).not.toContain('Mis Préstamos');
  });

  it('ADMIN ve inventario, sugerencias, usuarios y auditoria pero NO reportes ni prestamos (backend)', () => {
    authService.isLoggedIn.and.returnValue(true);
    authService.hasRole.and.callFake((...roles: string[]) => roles.includes('ADMIN'));

    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Libros');
    expect(compiled.textContent).toContain('Sugerencias');
    expect(compiled.textContent).toContain('Usuarios');
    expect(compiled.textContent).toContain('Auditoría');
    expect(compiled.textContent).not.toContain('Reportes');
    expect(compiled.textContent).not.toContain('Préstamos');
  });
});
