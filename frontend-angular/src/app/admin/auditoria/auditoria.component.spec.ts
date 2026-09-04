import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AuditoriaComponent } from './auditoria.component';
import { AuditoriaService } from '../../core/services/auditoria.service';
import { UsuarioAdminService } from '../../core/services/usuario-admin.service';

describe('AuditoriaComponent', () => {
  let component: AuditoriaComponent;
  let fixture: ComponentFixture<AuditoriaComponent>;
  let auditoriaService: jasmine.SpyObj<AuditoriaService>;

  const pagina = {
    content: [
      { id: 1, usuario: 'admin@sgb-saas.local', accion: 'UPDATE', fechaHora: '2026-08-16T15:02:00Z', modulo: 'usuarios', detalle: '{"nombre":"Ana","rol":"ADMIN"}' },
      { id: 2, usuario: null, accion: 'LOGIN_FAIL', fechaHora: '2026-08-16T14:58:00Z', modulo: 'usuarios', detalle: 'Intento con correo desconocido' },
      { id: 3, usuario: 'u@correo.com', accion: 'LOGIN_OK', fechaHora: '2026-08-16T14:40:00Z', modulo: 'usuarios', detalle: null }
    ],
    totalPages: 1,
    totalElements: 3
  };

  beforeEach(async () => {
    auditoriaService = jasmine.createSpyObj('AuditoriaService', ['listar', 'resumen', 'exportar']);
    auditoriaService.listar.and.returnValue(of(pagina as any));
    auditoriaService.resumen.and.returnValue(of([]));
    const usuarioAdminServiceSpy = jasmine.createSpyObj('UsuarioAdminService', ['listar']);

    await TestBed.configureTestingModule({
      imports: [AuditoriaComponent],
      providers: [
        { provide: AuditoriaService, useValue: auditoriaService },
        { provide: UsuarioAdminService, useValue: usuarioAdminServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AuditoriaComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('carga la bitácora inicial sin filtros', () => {
    component.abrirHistorial('usuarios');
    expect(auditoriaService.listar).toHaveBeenCalledWith(
      jasmine.objectContaining({ page: 0, size: 20 })
    );
    expect(component.eventos.length).toBe(3);
  });

  it('identifica eventos del sistema cuando usuario es null', () => {
    component.abrirHistorial('usuarios');
    expect(component.esSistema(component.eventos[1])).toBeTrue();
    expect(component.esSistema(component.eventos[0])).toBeFalse();
  });

  it('aplica filtros y vuelve a la primera página', () => {
    component.currentPage = 2;
    component.seleccionarUsuario({ id: 14, nombre: 'Ana', apellido: 'Paz', correo: 'ana@correo.com', roles: ['LECTOR'], estado: 'ACTIVO', multasPendientes: false });
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

  it('aplica filtro de día con rango completo', () => {
    component.filtroDia = '2026-08-16';

    component.filtrar();

    expect(auditoriaService.listar).toHaveBeenCalledWith(
      jasmine.objectContaining({
        desde: '2026-08-16T00:00:00.000Z',
        hasta: '2026-08-16T23:59:59.999Z'
      })
    );
  });

  it('aplica filtro de día + hora con ventana de 1 minuto', () => {
    component.filtroDia = '2026-08-16';
    component.filtroHora = '14:58';

    component.filtrar();

    expect(auditoriaService.listar).toHaveBeenCalledWith(
      jasmine.objectContaining({
        desde: '2026-08-16T14:58:00.000Z',
        hasta: '2026-08-16T14:58:59.999Z'
      })
    );
  });

  it('limpia todos los filtros con limpiarFiltros', () => {
    component.filtroDia = '2026-08-16';
    component.filtroHora = '10:00';
    component.filtroModulo = 'usuarios';

    component.limpiarFiltros();

    expect(component.filtroDia).toBe('');
    expect(component.filtroHora).toBe('');
    expect(component.filtroModulo).toBe('');
    expect(component.currentPage).toBe(0);
  });

  it('limpia el chip de usuario y vuelve a buscar sin filtro de usuario', () => {
    component.seleccionarUsuario({ id: 14, nombre: 'Ana', apellido: 'Paz', correo: 'ana@correo.com', roles: ['LECTOR'], estado: 'ACTIVO', multasPendientes: false });
    expect(component.usuarioSeleccionado).toBeTruthy();
    expect(component.filtroUsuarioId).toBe('14');

    component.limpiarSeleccion();

    expect(component.usuarioSeleccionado).toBeNull();
    expect(component.filtroUsuarioId).toBe('');
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

  it('formatea las etiquetas de módulo legiblemente con moduloLabel', () => {
    expect(component.moduloLabel('sugerencias_adquisicion')).toBe('Sugerencias de adquisición');
    expect(component.moduloLabel('usuarios')).toBe('Usuarios');
    expect(component.moduloLabel('otro_modulo')).toBe('otro modulo');
  });

  it('abre y cierra el modal de detalle', () => {
    const evento = component.eventos[0];
    component.abrirDetalle(evento);
    expect(component.modalVisible).toBeTrue();
    expect(component.eventoSeleccionado).toBe(evento);

    component.cerrarDetalle();
    expect(component.modalVisible).toBeFalse();
    expect(component.eventoSeleccionado).toBeNull();
  });

  it('formatea JSON válido para el modal', () => {
    component.eventoSeleccionado = { id: 1, usuario: 'admin', accion: 'UPDATE', fechaHora: '2026-08-16T15:02:00Z', modulo: 'usuarios', detalle: '{"nombre":"Ana","rol":"ADMIN"}' };
    const json = component.detalleFormateado();
    expect(json).toContain('"nombre"');
    expect(json).toContain('"Ana"');
  });

  it('maneja JSON inválido en detalleFormateado', () => {
    component.eventoSeleccionado = { id: 2, usuario: null, accion: 'LOGIN_FAIL', fechaHora: '2026-08-16T14:58:00Z', modulo: 'usuarios', detalle: 'Intento con correo desconocido' };
    const json = component.detalleFormateado();
    expect(json).toBe('Intento con correo desconocido');
  });

  it('separa antes/despues en UPDATE Java->Python y detecta diff en nombre', () => {
    const detalle = JSON.stringify({ antes: { nombre: 'Java' }, despues: { nombre: 'Python' } });
    const split = component.getAntesDespues(detalle, 'UPDATE');
    expect(split.esUpdate).toBeTrue();
    expect(split.antes.nombre).toBe('Java');
    expect(split.despues.nombre).toBe('Python');
    expect(component.diffKeys(split.antes, split.despues)).toContain('nombre');
    const html = component.resaltarConDiff(split.despues, ['nombre']);
    expect(html).toContain('bg-yellow-200');
  });

  it('no parte en dos ventanas cuando es INSERT', () => {
    const split = component.getAntesDespues('{"nombre":"Ana"}', 'INSERT');
    expect(split.esUpdate).toBeFalse();
  });

  it('retorna texto plano cuando el detalle no es JSON', () => {
    const split = component.getAntesDespues('Intento con correo desconocido', 'LOGIN_FAIL');
    expect(split.esUpdate).toBeFalse();
    expect(split.textoPlano).toBe('Intento con correo desconocido');
  });

  it('exporta CSV con los filtros vigentes', () => {
    auditoriaService.exportar.and.returnValue(of(new Blob(['a,b'], { type: 'text/csv' })));
    component.filtroModulo = 'usuarios';
    component.filtroDesde = '2026-08-01';
    component.filtroHasta = '2026-08-16';

    component.exportarCsv();

    expect(auditoriaService.exportar).toHaveBeenCalledWith({
      usuarioId: null,
      modulo: 'usuarios',
      desde: '2026-08-01T00:00:00.000Z',
      hasta: '2026-08-16T23:59:59.999Z'
    });
    expect(component.exportando).toBeFalse();
  });

  it('muestra el detail si la exportación falla', () => {
    auditoriaService.exportar.and.returnValue(
      throwError(() => ({ error: { detail: 'Sin permiso de exportación' } }))
    );

    component.exportarCsv();

    expect(component.errorMsg).toBe('Sin permiso de exportación');
    expect(component.exportando).toBeFalse();
  });

  it('trunca detalle largo a 2000 chars salvo ver completo', () => {
    const largo = JSON.stringify({ texto: 'x'.repeat(5000) });
    component.eventoSeleccionado = { id: 9, usuario: 'a', accion: 'UPDATE', fechaHora: '2026-08-16T15:02:00Z', modulo: 'libros', detalle: largo };
    component.verCompleto = false;
    expect(component.detalleFormateado().length).toBeLessThanOrEqual(2100);
    component.verCompleto = true;
    expect(component.detalleFormateado()).toContain('xxxx');
  });
});