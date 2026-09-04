import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { RouterTestingModule } from '@angular/router/testing';
import { Router } from '@angular/router';
import { RecuperarPasswordComponent } from './recuperar-password.component';
import { AuthService } from '../../core/services/auth.service';

describe('RecuperarPasswordComponent', () => {
  let component: RecuperarPasswordComponent;
  let fixture: ComponentFixture<RecuperarPasswordComponent>;
  let authService: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(async () => {
    authService = jasmine.createSpyObj('AuthService', ['solicitarReset', 'resetearPassword']);
    await TestBed.configureTestingModule({
      imports: [RecuperarPasswordComponent, RouterTestingModule],
      providers: [{ provide: AuthService, useValue: authService }]
    }).compileComponents();
    fixture = TestBed.createComponent(RecuperarPasswordComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('crea el componente en paso solicitar', () => {
    expect(component).toBeTruthy();
    expect(component.paso).toBe('solicitar');
  });

  it('solicitar con correo válido avanza al paso restablecer', () => {
    authService.solicitarReset.and.returnValue(of({}));
    component.formSolicitar.setValue({ correo: 'a@correo.com' });
    component.solicitar();
    expect(authService.solicitarReset).toHaveBeenCalledWith('a@correo.com');
    expect(component.paso).toBe('restablecer');
    expect(component.correoFijo).toBe('a@correo.com');
  });

  it('solicitar muestra el detail si el correo no existe', () => {
    authService.solicitarReset.and.returnValue(
      throwError(() => ({ error: { detail: 'Usuario no encontrado' } }))
    );
    component.formSolicitar.setValue({ correo: 'nadie@correo.com' });
    component.solicitar();
    expect(component.errorMsg).toBe('Usuario no encontrado');
    expect(component.paso).toBe('solicitar');
  });

  it('restablecer con código válido navega al login con ?reset=1', () => {
    authService.resetearPassword.and.returnValue(of({}));
    spyOn(router, 'navigate');
    component.correoFijo = 'a@correo.com';
    component.paso = 'restablecer';
    component.formReset.setValue({ codigo: '123456', nuevaPassword: 'Secreta123', confirmarPassword: 'Secreta123' });
    component.restablecer();
    expect(authService.resetearPassword).toHaveBeenCalledWith('a@correo.com', '123456', 'Secreta123');
    expect(router.navigate).toHaveBeenCalledWith(['/login'], { queryParams: { reset: '1' } });
  });

  it('no envía si las contraseñas no coinciden', () => {
    component.correoFijo = 'a@correo.com';
    component.paso = 'restablecer';
    component.formReset.setValue({ codigo: '123456', nuevaPassword: 'Secreta123', confirmarPassword: 'Otra12345' });
    component.restablecer();
    expect(authService.resetearPassword).not.toHaveBeenCalled();
  });

  it('restablecer muestra el detail con código inválido', () => {
    authService.resetearPassword.and.returnValue(
      throwError(() => ({ error: { detail: 'Código inválido o expirado' } }))
    );
    component.correoFijo = 'a@correo.com';
    component.paso = 'restablecer';
    component.formReset.setValue({ codigo: '000000', nuevaPassword: 'Secreta123', confirmarPassword: 'Secreta123' });
    component.restablecer();
    expect(component.errorMsg).toBe('Código inválido o expirado');
  });
});
