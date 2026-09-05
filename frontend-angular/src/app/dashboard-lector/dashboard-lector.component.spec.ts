import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { DashboardLectorComponent } from './dashboard-lector.component';
import { AuthService } from '../core/services/auth.service';

describe('DashboardLectorComponent', () => {
  let fixture: ComponentFixture<DashboardLectorComponent>;
  let authService: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    // El shell persiste colapso en localStorage: aislar cada test del orden de ejecución.
    localStorage.clear();
    authService = jasmine.createSpyObj('AuthService', ['isLoggedIn', 'hasRole', 'logout', 'getCorreo']);
    authService.isLoggedIn.and.returnValue(true);
    authService.hasRole.and.callFake((...roles: string[]) => roles.includes('LECTOR'));
    authService.getCorreo.and.returnValue('lector@correo.com');

    await TestBed.configureTestingModule({
      imports: [DashboardLectorComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardLectorComponent);
    fixture.detectChanges();
  });

  it('debería crear el componente', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('debería mostrar las secciones BIBLIOTECA y MI CUENTA', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('BIBLIOTECA');
    expect(compiled.textContent).toContain('MI CUENTA');
    expect(compiled.textContent).toContain('Catálogo');
  });

  it('debería mostrar el widget del chatbot (exclusivo del lector)', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-chatbot-widget')).not.toBeNull();
  });
});
