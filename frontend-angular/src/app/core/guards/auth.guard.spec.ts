import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('authGuard', () => {
  let authService: jasmine.SpyObj<AuthService>;
  let navigateSpy: jasmine.Spy;

  beforeEach(() => {
    authService = jasmine.createSpyObj('AuthService', ['isLoggedIn', 'tokenExpirado', 'refresh']);
    navigateSpy = jasmine.createSpy('navigate');

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: { navigate: navigateSpy } }
      ]
    });
  });

  it('deja pasar si hay accessToken vigente', () => {
    authService.isLoggedIn.and.returnValue(true);
    authService.tokenExpirado.and.returnValue(false);

    const resultado = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));

    expect(resultado).toBeTrue();
    expect(authService.refresh).not.toHaveBeenCalled();
  });

  it('restaura la sesion con refresh tras F5 (sin token en memoria)', (done) => {
    authService.isLoggedIn.and.returnValue(false);
    authService.refresh.and.returnValue(of({ accessToken: 'nuevo' }));

    const resultado = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    (resultado as ReturnType<typeof of>).subscribe((ok) => {
      expect(ok).toBeTrue();
      expect(navigateSpy).not.toHaveBeenCalled();
      done();
    });
  });

  it('intenta refresh si el accessToken ya expiro, sin logout', (done) => {
    authService.isLoggedIn.and.returnValue(true);
    authService.tokenExpirado.and.returnValue(true);
    authService.refresh.and.returnValue(of({ accessToken: 'nuevo' }));

    const resultado = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    (resultado as ReturnType<typeof of>).subscribe((ok) => {
      expect(ok).toBeTrue();
      expect(authService.refresh).toHaveBeenCalledTimes(1);
      done();
    });
  });

  it('redirige a /login si el refresh falla', (done) => {
    authService.isLoggedIn.and.returnValue(false);
    authService.refresh.and.returnValue(throwError(() => ({ status: 401 })));

    const resultado = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    (resultado as ReturnType<typeof of>).subscribe((ok) => {
      expect(ok).toBeFalse();
      expect(navigateSpy).toHaveBeenCalledWith(['/login']);
      done();
    });
  });
});
