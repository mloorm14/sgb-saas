import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { DashboardShellComponent } from './dashboard-shell.component';
import { AuthService } from '../../core/services/auth.service';
import { SeccionSidebar } from './seccion-sidebar.model';

describe('DashboardShellComponent', () => {
  let component: DashboardShellComponent;
  let fixture: ComponentFixture<DashboardShellComponent>;
  let authService: jasmine.SpyObj<AuthService>;

  const secciones: SeccionSidebar[] = [
    {
      titulo: 'GESTIÓN',
      enlaces: [
        { ruta: '/a/libros', etiqueta: 'Libros', icono: 'inventory_2' },
        { ruta: '/a/usuarios', etiqueta: 'Usuarios', icono: 'manage_accounts', roles: ['ADMIN'] }
      ]
    },
    { titulo: 'VACÍA', enlaces: [] }
  ];

  async function crear(roles: string[], mostrarChatbot = false) {
    authService = jasmine.createSpyObj('AuthService', ['isLoggedIn', 'hasRole', 'logout', 'getCorreo']);
    authService.isLoggedIn.and.returnValue(true);
    authService.hasRole.and.callFake((...rs: string[]) => rs.some(r => roles.includes(r)));
    authService.getCorreo.and.returnValue('test@correo.com');

    await TestBed.configureTestingModule({
      imports: [DashboardShellComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: authService }]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardShellComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('secciones', secciones);
    fixture.componentRef.setInput('mostrarChatbot', mostrarChatbot);
    fixture.detectChanges();
  }

  beforeEach(() => localStorage.clear());

  it('debería crear el componente y renderizar las secciones', async () => {
    await crear(['BIBLIOTECARIO']);
    expect(component).toBeTruthy();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('GESTIÓN');
    expect(compiled.textContent).toContain('Libros');
  });

  it('debería filtrar enlaces por rol y ocultar secciones vacías', async () => {
    await crear(['BIBLIOTECARIO']);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).not.toContain('Usuarios');
    expect(compiled.textContent).not.toContain('VACÍA');
  });

  it('debería mostrar enlaces con rol cuando corresponde', async () => {
    await crear(['ADMIN']);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Usuarios');
  });

  it('debería ocultar el chatbot por defecto y mostrarlo con mostrarChatbot', async () => {
    await crear(['LECTOR'], false);
    let compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-chatbot-widget')).toBeNull();

    fixture.componentRef.setInput('mostrarChatbot', true);
    fixture.detectChanges();
    compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-chatbot-widget')).not.toBeNull();
  });

  it('debería persistir el colapso del sidebar en localStorage', async () => {
    await crear(['LECTOR']);
    expect(component.isCollapsed()).toBeFalse();
    component.toggleSidebar();
    expect(component.isCollapsed()).toBeTrue();
    expect(localStorage.getItem('sidebar:collapsed')).toBe('true');
  });

  it('debería cerrar sesión al llamar cerrarSesion()', async () => {
    await crear(['LECTOR']);
    component.cerrarSesion();
    expect(authService.logout).toHaveBeenCalled();
    expect(component.mostrarMenuUsuario).toBeFalse();
  });
});
