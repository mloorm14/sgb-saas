import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AuditoriaComponent } from './auditoria.component';
import { AuditoriaService } from '../../core/services/auditoria.service';

describe('AuditoriaComponent', () => {
  let component: AuditoriaComponent;
  let fixture: ComponentFixture<AuditoriaComponent>;
  let auditoriaService: jasmine.SpyObj<AuditoriaService>;

  const pagina = {
    content: [
      { id: 1, usuario: 'admin@sgb-saas.local', accion: 'UPDATE', fechaHora: '2026-08-16T15:02:00Z', modulo: 'usuarios', detalle: 'Cambio de rol' },
      { id: 2, usuario: null, accion: 'LOGIN_FAIL', fechaHora: '2026-08-16T14:58:00Z', modulo: 'usuarios', detalle: 'Intento con correo desconocido' },
      { id: 3, usuario: 'u@uteq.edu.ec', accion: 'LOGIN_OK', fechaHora: '2026-08-16T14:40:00Z', modulo: 'usuarios', detalle: null }
    ],
    totalPages: 1,
    totalElements: 3
  };

  beforeEach(async () => {
    auditoriaService = jasmine.createSpyObj('AuditoriaService', ['listar']);
    auditoriaService.listar.and.returnValue(of(pagina as any));

    await TestBed.configureTestingModule({
      imports: [AuditoriaComponent],
      providers: [
        { provide: AuditoriaService, useValue: auditoriaService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AuditoriaComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('carga la bitácora inicial sin filtros', () => {
    expect(auditoriaService.listar).toHaveBeenCalledWith(
      jasmine.objectContaining({ page: 0, size: 20 })
    );
    expect(component.eventos.length).toBe(3);
  });

  it('muestra "—" cuando el usuario del evento es null (Javadoc de EventoAuditoriaResponseDTO)', () => {
    expect(component.usuarioLabel(component.eventos[1])).toBe('—');
    expect(component.usuarioLabel(component.eventos[0])).toBe('admin@sgb-saas.local');
  });

  it('aplica filtros y vuelve a la primera página', () => {
    component.currentPage = 2;
    component.filtroUsuarioId = '14';
    component.filtroModulo = 'usuarios';
    component.filtroDesde = '2026-08-01';
    component.filtroHasta = '2026-08-16';

    component.filtrar();

    expect(component.currentPage).toBe(0);
    expect(auditoriaService.listar).toHaveBeenCalledWith({
      usuarioId: 14,
      modulo: 'usuarios',
      desde: '2026-08-01T00:00:00.000Z',
      hasta: '2026-08-16T23:59:59.999Z',
      page: 0,
      size: 20
    });
  });

  it('ignora un ID de usuario inválido en los filtros', () => {
    component.filtroUsuarioId = 'abc';

    component.filtrar();

    const llamada = auditoriaService.listar.calls.mostRecent().args[0] as any;
    expect(llamada.usuarioId).toBeNull();
  });

  it('muestra el detail del backend si la carga falla', () => {
    auditoriaService.listar.and.returnValue(
      throwError(() => ({ error: { detail: 'Error de base de datos' } }))
    );

    component.filtrar();

    expect(component.errorMsg).toBe('Error de base de datos');
  });
});