import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { UsuariosComponent } from './usuarios.component';
import { UsuarioAdminService } from '../../core/services/usuario-admin.service';
import { AuthService } from '../../core/services/auth.service';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';
import { ToastService } from '../../shared/toast/toast.service';

describe('UsuariosComponent', () => {
  let component: UsuariosComponent;
  let fixture: ComponentFixture<UsuariosComponent>;
  let usuarioAdminService: jasmine.SpyObj<UsuarioAdminService>;
  let authService: jasmine.SpyObj<AuthService>;

  const pagina = {
    content: [
      { id: 1, nombre: 'María', apellido: 'López', correo: 'm.lopez@correo.com', roles: ['LECTOR'], estado: 'ACTIVO', multasPendientes: false },
      { id: 2, nombre: 'Usuario', apellido: 'Demo', correo: 'u@correo.com', roles: ['LECTOR'], estado: 'BLOQUEADO_POR_MULTA', multasPendientes: true },
      { id: 3, nombre: 'Jorge', apellido: 'Cajas', correo: 'j.cajas@correo.com', roles: ['BIBLIOTECARIO'], estado: 'INACTIVO', multasPendientes: false }
    ],
    totalPages: 1,
    totalElements: 3
  };

  beforeEach(async () => {
    usuarioAdminService = jasmine.createSpyObj('UsuarioAdminService', ['listar', 'cambiarRol', 'cambiarEstado', 'crear', 'eliminar']);
    authService = jasmine.createSpyObj('AuthService', ['hasRole']);
    usuarioAdminService.listar.and.returnValue(of(pagina as any));

    await TestBed.configureTestingModule({
      imports: [UsuariosComponent],
      providers: [
        { provide: UsuarioAdminService, useValue: usuarioAdminService },
        { provide: AuthService, useValue: authService },
        { provide: ActivatedRoute, useValue: { snapshot: { data: {} } } },
        { provide: ConfirmDialogService, useValue: jasmine.createSpyObj('ConfirmDialogService', ['confirm']) },
        { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['success', 'error', 'warning']) }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(UsuariosComponent);
    component = fixture.componentInstance;
  });

  it('como ADMIN carga el listado y puede gestionar rol y estado', () => {
    authService.hasRole.and.callFake((...roles: string[]) => roles.includes('ADMIN'));

    fixture.detectChanges();

    expect(component.puedeVer).toBeTrue();
    expect(component.puedeGestionar).toBeTrue();
    expect(usuarioAdminService.listar).toHaveBeenCalledWith('', 0, 10, false);
    expect(component.usuarios.length).toBe(3);
  });

  it('como GERENTE en admin/usuarios queda en solo lectura (gestiona en mis-usuarios)', () => {
    authService.hasRole.and.callFake((...roles: string[]) => roles.includes('GERENTE'));

    fixture.detectChanges();

    expect(component.puedeVer).toBeTrue();
    expect(component.puedeGestionar).toBeFalse();
    expect(usuarioAdminService.listar).toHaveBeenCalledWith('', 0, 10, true);
    expect(component.usuarios.length).toBe(3);
  });

  it('no consulta el backend sin permisos de visualización', () => {
    authService.hasRole.and.returnValue(false);

    fixture.detectChanges();

    expect(component.puedeVer).toBeFalse();
    expect(usuarioAdminService.listar).not.toHaveBeenCalled();
  });

  describe('cambio de estado con motivo obligatorio', () => {
    beforeEach(() => {
      authService.hasRole.and.callFake((...roles: string[]) => roles.includes('ADMIN'));
      fixture.detectChanges();
    });

    it('confirma el bloqueo con motivo y recarga el listado', () => {
      usuarioAdminService.cambiarEstado.and.returnValue(of(undefined));
      const usuario = component.usuarios[0];

      component.abrirModalEstado(usuario, 'INACTIVO');
      component.motivoEstado = 'Incumplimiento de política';
      component.confirmarCambioEstado();

      expect(usuarioAdminService.cambiarEstado).toHaveBeenCalledWith(
        1, 'INACTIVO', 'Incumplimiento de política'
      );
      expect(component.mostrarModalEstado).toBeFalse();
      expect(usuarioAdminService.listar).toHaveBeenCalledTimes(2);
    });

    it('no envía la petición sin motivo', () => {
      const usuario = component.usuarios[0];

      component.abrirModalEstado(usuario, 'INACTIVO');
      component.motivoEstado = '   ';
      component.confirmarCambioEstado();

      expect(usuarioAdminService.cambiarEstado).not.toHaveBeenCalled();
    });

    it('muestra el detail del backend si el cambio de estado falla', () => {
      usuarioAdminService.cambiarEstado.and.returnValue(
        throwError(() => ({ error: { detail: 'El estado no existe en el catálogo' } }))
      );

      component.abrirModalEstado(component.usuarios[0], 'ACTIVO');
      component.motivoEstado = 'Reactivación solicitada';
      component.confirmarCambioEstado();

      expect(component.errorModal).toBe('El estado no existe en el catálogo');
      expect(component.mostrarModalEstado).toBeTrue();
    });
  });

  describe('cambio de rol', () => {
    beforeEach(() => {
      authService.hasRole.and.callFake((...roles: string[]) => roles.includes('ADMIN'));
      fixture.detectChanges();
    });

    it('envía el nuevo rol y recarga para reflejar el estado real del backend', () => {
      usuarioAdminService.cambiarRol.and.returnValue(of(undefined));

      component.cambiarRol(component.usuarios[0], 'BIBLIOTECARIO');

      expect(usuarioAdminService.cambiarRol).toHaveBeenCalledWith(1, 'BIBLIOTECARIO');
      expect(usuarioAdminService.listar).toHaveBeenCalledTimes(2);
    });

    it('muestra error y recarga igual si el PATCH falla (el select ya cambió visualmente)', () => {
      usuarioAdminService.cambiarRol.and.returnValue(
        throwError(() => ({ error: { detail: 'Rol no encontrado' } }))
      );

      component.cambiarRol(component.usuarios[0], 'INEXISTENTE');

      expect(component.errorMsg).toBe('Rol no encontrado');
      expect(usuarioAdminService.listar).toHaveBeenCalledTimes(2);
    });
  });

  describe('F8-gerente y crear usuario', () => {
    it('GERENTE solo ofrece LECTOR y BIBLIOTECARIO en el select', () => {
      authService.hasRole.and.callFake((...roles: string[]) => roles.includes('GERENTE'));
      fixture.detectChanges();
      expect(component.rolesParaSelect).toEqual(['LECTOR', 'BIBLIOTECARIO']);
      expect(component.rolesParaSelect).not.toContain('GERENTE');
    });

    it('crear con correo duplicado muestra 409 sin cerrar el modal', () => {
      authService.hasRole.and.callFake((...roles: string[]) => roles.includes('ADMIN'));
      fixture.detectChanges();
      usuarioAdminService.crear.and.returnValue(
        throwError(() => ({ status: 409, error: { detail: 'El correo ya está registrado' } }))
      );
      component.abrirModalCrear();
      component.nuevoNombre = 'Ana';
      component.nuevoApellido = 'Paz';
      component.nuevoCorreo = 'ana@correo.com';
      component.nuevoPassword = 'Secreta123';
      component.nuevoRol = 'LECTOR';
      component.confirmarCrear();
      expect(component.errorCrear).toContain('ya está registrado');
      expect(component.mostrarModalCrear).toBeTrue();
    });
  });

  describe('toast único sin cuadro inline duplicado', () => {
    let toastService: jasmine.SpyObj<ToastService>;

    beforeEach(() => {
      authService.hasRole.and.callFake((...roles: string[]) => roles.includes('ADMIN'));
      fixture.detectChanges();
      toastService = TestBed.inject(ToastService) as jasmine.SpyObj<ToastService>;
    });

    it('crear exitoso invoca toast y deja mensajeOk vacío', () => {
      usuarioAdminService.crear.and.returnValue(of({} as any));
      component.abrirModalCrear();
      component.nuevoNombre = 'Andres';
      component.nuevoApellido = 'Paz';
      component.nuevoCorreo = 'andres@correo.com';
      component.nuevoPassword = 'Secreta123';
      component.nuevoRol = 'LECTOR';
      component.confirmarCrear();

      expect(toastService.success).toHaveBeenCalledTimes(1);
      expect(toastService.success).toHaveBeenCalledWith('Usuario creado', jasmine.any(String));
      expect(component.mensajeOk).toBe('');
    });

    it('eliminar exitoso invoca toast y deja mensajeOk vacío', () => {
      const confirmService = TestBed.inject(ConfirmDialogService) as jasmine.SpyObj<ConfirmDialogService>;
      confirmService.confirm.and.returnValue(of(true));
      usuarioAdminService.eliminar.and.returnValue(of(undefined));

      component.eliminarDefinitivo(component.usuarios[0]);

      expect(toastService.success).toHaveBeenCalledTimes(1);
      expect(toastService.success).toHaveBeenCalledWith('Eliminado', jasmine.any(String));
      expect(component.mensajeOk).toBe('');
    });
  });
});