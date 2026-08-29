import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { DashboardGerenteAdminComponent } from './dashboard-gerente-admin.component';
import { AuthService } from '../core/services/auth.service';

describe('DashboardGerenteAdminComponent', () => {
  let component: DashboardGerenteAdminComponent;
  let fixture: ComponentFixture<DashboardGerenteAdminComponent>;
  let authService: jasmine.SpyObj<AuthService>;

  describe('con rol GERENTE', () => {
    beforeEach(async () => {
      authService = jasmine.createSpyObj('AuthService', ['isLoggedIn', 'hasRole', 'logout', 'getCorreo']);
      authService.isLoggedIn.and.returnValue(true);
      authService.hasRole.and.callFake((...roles: string[]) => roles.includes('GERENTE'));
      authService.getCorreo.and.returnValue('gerente@uteq.edu.ec');

      await TestBed.configureTestingModule({
        imports: [DashboardGerenteAdminComponent],
        providers: [
          provideRouter([]),
          { provide: AuthService, useValue: authService }
        ]
      }).compileComponents();

      fixture = TestBed.createComponent(DashboardGerenteAdminComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('debería crear el componente', () => {
      expect(component).toBeTruthy();
    });

    it('debería mostrar Reportes en el sidebar para GERENTE', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.textContent).toContain('Reportes');
    });

    it('NO debería mostrar Auditoría en el sidebar para GERENTE (solo ADMIN)', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.textContent).not.toContain('Auditoría');
    });

    it('debería mostrar nombre del rol como Gerente', () => {
      expect(component.nombreRol).toBe('Gerente');
    });
  });

  describe('con rol ADMIN', () => {
    beforeEach(async () => {
      authService = jasmine.createSpyObj('AuthService', ['isLoggedIn', 'hasRole', 'logout', 'getCorreo']);
      authService.isLoggedIn.and.returnValue(true);
      authService.hasRole.and.callFake((...roles: string[]) => roles.includes('ADMIN'));
      authService.getCorreo.and.returnValue('admin@uteq.edu.ec');

      await TestBed.configureTestingModule({
        imports: [DashboardGerenteAdminComponent],
        providers: [
          provideRouter([]),
          { provide: AuthService, useValue: authService }
        ]
      }).compileComponents();

      fixture = TestBed.createComponent(DashboardGerenteAdminComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('debería mostrar Reportes en el sidebar para ADMIN', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.textContent).toContain('Reportes');
    });

    it('debería mostrar Configuración en el sidebar para ADMIN', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.textContent).toContain('Configuración');
    });

    it('debería mostrar Usuarios en el sidebar para ADMIN', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.textContent).toContain('Usuarios');
    });

    it('debería mostrar nombre del rol como Administrador', () => {
      expect(component.nombreRol).toBe('Administrador');
    });
  });
});
