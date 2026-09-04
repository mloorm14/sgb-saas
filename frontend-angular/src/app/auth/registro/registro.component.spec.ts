import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { RouterTestingModule } from '@angular/router/testing';
import { RegistroComponent } from './registro.component';
import { AuthService } from '../../core/services/auth.service';

describe('RegistroComponent reenvío F6', () => {
  let component: RegistroComponent;
  let fixture: ComponentFixture<RegistroComponent>;
  let authService: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authService = jasmine.createSpyObj('AuthService', ['registro', 'verificarCorreo', 'reenviarCodigo']);
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [RegistroComponent, RouterTestingModule],
      providers: [{ provide: AuthService, useValue: authService }]
    }).compileComponents();
    fixture = TestBed.createComponent(RegistroComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => localStorage.clear());

  it('crea el componente en paso registro', () => {
    expect(component).toBeTruthy();
    expect(component.paso).toBe('registro');
  });

  it('permite reenviar cuando no hay intentos ni cooldown', () => {
    component.correoRegistrado = 'a@correo.com';
    component.iniciarEstadoReenvio();
    expect(component.puedeReenviar).toBeTrue();
  });

  it('bloquea tras 3 reenvíos (contador por correo)', () => {
    component.correoRegistrado = 'a@correo.com';
    authService.reenviarCodigo.and.returnValue(of({}));
    component.iniciarEstadoReenvio();
    for (let i = 0; i < 3; i++) {
      component.segundosRestantes = 0;
      component.reenviarCodigo();
    }
    expect(component.reenviosRealizados).toBe(3);
    expect(component.puedeReenviar).toBeFalse();
  });

  it('el contador es independiente por correo', () => {
    component.correoRegistrado = 'a@correo.com';
    authService.reenviarCodigo.and.returnValue(of({}));
    component.iniciarEstadoReenvio();
    component.segundosRestantes = 0;
    component.reenviarCodigo();
    expect(component.reenviosRealizados).toBe(1);

    component.correoRegistrado = 'b@correo.com';
    component.iniciarEstadoReenvio();
    expect(component.reenviosRealizados).toBe(0);
    expect(component.puedeReenviar).toBeTrue();
  });

  it('inicia cooldown de 3 minutos tras reenviar', () => {
    component.correoRegistrado = 'a@correo.com';
    authService.reenviarCodigo.and.returnValue(of({}));
    component.iniciarEstadoReenvio();
    component.reenviarCodigo();
    expect(component.segundosRestantes).toBe(180);
    expect(component.textoCooldown).toBe('03:00');
    expect(component.puedeReenviar).toBeFalse();
  });
});
