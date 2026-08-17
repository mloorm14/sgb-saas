import { HttpHandlerFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { jwtInterceptor } from './jwt.interceptor';
import { AuthService } from '../services/auth.service';

// Hallazgo delta: el interceptor refresca con 401 o 403, reintenta UNA
// sola vez (guardia X-Retry) y hace logout si el refresh falla. El spec
// simula el servidor con un HttpHandlerFn que falla la primera llamada.
describe('jwtInterceptor', () => {
  let authService: jasmine.SpyObj<AuthService>;
  let navigateSpy: jasmine.Spy;

  const req = new HttpRequest('GET', '/api/v1/libros');
  const exito = new HttpResponse({ status: 200, body: { ok: true } });

  beforeEach(() => {
    authService = jasmine.createSpyObj('AuthService', ['getAccessToken', 'refresh', 'logout']);
    authService.getAccessToken.and.returnValue('token-viejo');
    navigateSpy = jasmine.createSpy('navigate');

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: { navigate: navigateSpy } }
      ]
    });
  });

  // Fuerza el fallo de la primer llamada del next y captura las requests.
  function nextQueFallaPrimero(status: number): { next: HttpHandlerFn; requests: HttpRequest<unknown>[] } {
    const requests: HttpRequest<unknown>[] = [];
    let primera = true;
    const next: HttpHandlerFn = (solicitud) => {
      requests.push(solicitud);
      if (primera) {
        primera = false;
        return throwError(() => ({ status, error: { detail: 'no autorizado' } }));
      }
      return of(exito);
    };
    return { next, requests };
  }

  it('refresca y reintenta una sola vez ante un 401 (con X-Retry y token nuevo)', () => {
    // Replica el tap de AuthService.refresh(): el accessToken nuevo queda
    // disponible para el reintento.
    authService.refresh.and.callFake(() => {
      authService.getAccessToken.and.returnValue('token-nuevo');
      return of({ accessToken: 'token-nuevo' });
    });
    const { next, requests } = nextQueFallaPrimero(401);

    TestBed.runInInjectionContext(() => jwtInterceptor(req, next))
      .subscribe((evento) => expect(evento).toBe(exito));

    expect(authService.refresh).toHaveBeenCalledTimes(1);
    expect(requests.length).toBe(2);
    expect(requests[1].headers.has('X-Retry')).toBeTrue();
    expect(requests[1].headers.get('Authorization')).toBe('Bearer token-nuevo');
  });

  it('refresca tambien ante un 403 (token vencido en el filtro de seguridad)', () => {
    authService.refresh.and.callFake(() => {
      authService.getAccessToken.and.returnValue('token-nuevo');
      return of({ accessToken: 'token-nuevo' });
    });
    const { next, requests } = nextQueFallaPrimero(403);

    TestBed.runInInjectionContext(() => jwtInterceptor(req, next))
      .subscribe((evento) => expect(evento).toBe(exito));

    expect(authService.refresh).toHaveBeenCalledTimes(1);
    expect(requests[1].headers.has('X-Retry')).toBeTrue();
  });

  it('no reintenta cuando la request ya trae X-Retry (evita el bucle con 403 por rol)', () => {
    const conRetry = req.clone({ setHeaders: { 'X-Retry': 'true' } });
    const requests: HttpRequest<unknown>[] = [];
    const next: HttpHandlerFn = (solicitud) => {
      requests.push(solicitud);
      return throwError(() => ({ status: 403, error: { detail: 'rol insuficiente' } }));
    };

    let errorPropagado: unknown;
    TestBed.runInInjectionContext(() => jwtInterceptor(conRetry, next))
      .subscribe({ error: (e) => { errorPropagado = e; } });

    expect(authService.refresh).not.toHaveBeenCalled();
    expect(requests.length).toBe(1);
    expect((errorPropagado as { status: number }).status).toBe(403);
  });

  it('hace logout y redirige a /login si el refresh mismo falla', () => {
    authService.refresh.and.returnValue(throwError(() => ({ status: 401 })));
    const { next } = nextQueFallaPrimero(401);

    let errorPropagado: unknown;
    TestBed.runInInjectionContext(() => jwtInterceptor(req, next))
      .subscribe({ error: (e) => { errorPropagado = e; } });

    expect(authService.logout).toHaveBeenCalled();
    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
    expect((errorPropagado as { status: number }).status).toBe(401);
  });
});