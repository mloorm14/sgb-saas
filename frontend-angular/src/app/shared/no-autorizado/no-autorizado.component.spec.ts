import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { NoAutorizadoComponent } from './no-autorizado.component';
import { AuthService } from '../../core/services/auth.service';

describe('NoAutorizadoComponent', () => {
  let component: NoAutorizadoComponent;
  let fixture: ComponentFixture<NoAutorizadoComponent>;
  let authService: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authService = jasmine.createSpyObj('AuthService', ['isLoggedIn', 'hasRole']);

    await TestBed.configureTestingModule({
      imports: [NoAutorizadoComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(NoAutorizadoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('redirige a / si no hay sesion activa', () => {
    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigate');
    authService.isLoggedIn.and.returnValue(false);

    component.volverAlHome();

    expect(navigateSpy).toHaveBeenCalledWith(['/']);
  });

  it('redirige al home de LECTOR (/prestamos)', () => {
    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigate');
    authService.isLoggedIn.and.returnValue(true);
    authService.hasRole.and.callFake((...roles: string[]) => roles.includes('LECTOR'));

    component.volverAlHome();

    expect(navigateSpy).toHaveBeenCalledWith(['/prestamos']);
  });

  it('redirige al home de BIBLIOTECARIO/GERENTE (/prestamos/gestion)', () => {
    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigate');
    authService.isLoggedIn.and.returnValue(true);
    authService.hasRole.and.callFake((...roles: string[]) => roles.includes('BIBLIOTECARIO'));

    component.volverAlHome();

    expect(navigateSpy).toHaveBeenCalledWith(['/prestamos/gestion']);
  });
});
