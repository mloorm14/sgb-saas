import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { DashboardBibliotecarioComponent } from './dashboard-bibliotecario.component';
import { AuthService } from '../core/services/auth.service';

describe('DashboardBibliotecarioComponent', () => {
  let component: DashboardBibliotecarioComponent;
  let fixture: ComponentFixture<DashboardBibliotecarioComponent>;
  let authService: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    // El shell persiste colapso en localStorage: aislar cada test del orden de ejecución.
    localStorage.clear();
    authService = jasmine.createSpyObj('AuthService', ['isLoggedIn', 'hasRole', 'logout', 'getCorreo']);
    authService.isLoggedIn.and.returnValue(true);
    authService.hasRole.and.callFake((...roles: string[]) => roles.includes('BIBLIOTECARIO'));
    authService.getCorreo.and.returnValue('bibliotecario@correo.com');

    await TestBed.configureTestingModule({
      imports: [DashboardBibliotecarioComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardBibliotecarioComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debería crear el componente', () => {
    expect(component).toBeTruthy();
  });

  it('debería mostrar la sección GESTIÓN y ocultar SISTEMA para el rol bibliotecario', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('GESTIÓN');
    expect(compiled.textContent).not.toContain('SISTEMA');
  });

  it('debería mostrar los enlaces de navegación del sidebar para bibliotecario', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Libros');
    expect(compiled.textContent).toContain('Préstamos');
    expect(compiled.textContent).toContain('Reservaciones');
    expect(compiled.textContent).toContain('Devoluciones');
    expect(compiled.textContent).toContain('Multas');
    expect(compiled.textContent).not.toContain('Reportes');
    expect(compiled.textContent).not.toContain('SISTEMA');
  });

  it('debería mostrar el correo del usuario', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('bibliotecario@correo.com');
  });

  it('debería mostrar iniciales y rol a través del shell compartido', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('BI');
    expect(compiled.textContent).toContain('Bibliotecario');
  });
});
