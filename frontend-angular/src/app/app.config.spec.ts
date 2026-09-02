import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { inicializarSesion } from './app.config';
import { AuthService } from './core/services/auth.service';

describe('inicializarSesion', () => {
  let authService: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    authService = jasmine.createSpyObj('AuthService', ['isLoggedIn', 'refresh']);
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: authService }]
    });
  });

  it('pide un accessToken nuevo cuando no hay sesion en memoria (F5)', async () => {
    authService.isLoggedIn.and.returnValue(false);
    authService.refresh.and.returnValue(of({ accessToken: 'nuevo' }));

    await inicializarSesion(authService)();

    expect(authService.refresh).toHaveBeenCalledTimes(1);
  });

  it('no llama refresh si ya hay accessToken en memoria', async () => {
    authService.isLoggedIn.and.returnValue(true);

    await inicializarSesion(authService)();

    expect(authService.refresh).not.toHaveBeenCalled();
  });

  it('arranca igual si no hay cookie de refresh', async () => {
    authService.isLoggedIn.and.returnValue(false);
    authService.refresh.and.returnValue(throwError(() => ({ status: 400 })));

    await inicializarSesion(authService)();

    expect(authService.refresh).toHaveBeenCalledTimes(1);
  });
});
