import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { GestionSugerenciasComponent } from './gestion-sugerencias.component';
import { SugerenciaAdquisicionService } from '../../core/services/sugerencia-adquisicion.service';

describe('GestionSugerenciasComponent', () => {
  let component: GestionSugerenciasComponent;
  let fixture: ComponentFixture<GestionSugerenciasComponent>;
  let sugerenciaService: jasmine.SpyObj<SugerenciaAdquisicionService>;

  const pagina = {
    content: [
      { id: 1, usuarioId: 9, titulo: 'Designing Data-Intensive Applications', autor: 'Martin Kleppmann', isbn: '', justificacion: 'Lo piden varios estudiantes', estado: 'PENDIENTE', revisadoPor: null, creadoEn: '2026-08-01T10:00:00Z' },
      { id: 2, usuarioId: 3, titulo: 'Refactoring', autor: 'Martin Fowler', isbn: '9780134757599', justificacion: '', estado: 'APROBADA', revisadoPor: 1, creadoEn: '2026-07-28T09:00:00Z' }
    ],
    totalPages: 1,
    totalElements: 2
  };

  beforeEach(async () => {
    sugerenciaService = jasmine.createSpyObj('SugerenciaAdquisicionService', [
      'listarTodas', 'cambiarEstado'
    ]);
    sugerenciaService.listarTodas.and.returnValue(of(pagina as any));

    await TestBed.configureTestingModule({
      imports: [GestionSugerenciasComponent],
      providers: [
        { provide: SugerenciaAdquisicionService, useValue: sugerenciaService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(GestionSugerenciasComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('carga el listado inicial con el filtro PENDIENTE por defecto', () => {
    expect(sugerenciaService.listarTodas).toHaveBeenCalledWith('PENDIENTE', jasmine.any(Object));
    expect(component.sugerencias.length).toBe(2);
  });

  it('cambia el filtro, vuelve a la primera página y recarga sin enviar estado para "Todas"', () => {
    component.currentPage = 3;
    component.cambiarFiltro('APROBADA');
    expect(component.currentPage).toBe(0);
    expect(sugerenciaService.listarTodas).toHaveBeenCalledWith('APROBADA', jasmine.any(Object));

    component.cambiarFiltro('');
    expect(sugerenciaService.listarTodas).toHaveBeenCalledWith('', jasmine.any(Object));
  });

  it('aprueba una sugerencia pendiente (solo APROBADA/RECHAZADA, nunca Pendiente de vuelta)', () => {
    sugerenciaService.cambiarEstado.and.returnValue(of(pagina.content[0] as any));

    component.aprobar(component.sugerencias[0]);

    expect(sugerenciaService.cambiarEstado).toHaveBeenCalledWith(1, 'APROBADA');
    expect(sugerenciaService.listarTodas).toHaveBeenCalledTimes(2);
  });

  it('rechaza una sugerencia pendiente', () => {
    sugerenciaService.cambiarEstado.and.returnValue(of(pagina.content[0] as any));

    component.rechazar(component.sugerencias[0]);

    expect(sugerenciaService.cambiarEstado).toHaveBeenCalledWith(1, 'RECHAZADA');
  });

  it('muestra el detail del backend si el cambio de estado falla', () => {
    sugerenciaService.cambiarEstado.and.returnValue(
      throwError(() => ({ error: { detail: 'El estado debe ser APROBADA o RECHAZADA' } }))
    );

    component.aprobar(component.sugerencias[0]);

    expect(component.errorMsg).toBe('El estado debe ser APROBADA o RECHAZADA');
    expect(component.cambiandoId).toBeNull();
  });

  it('muestra el id del solicitante (el DTO no trae el correo)', () => {
    expect(component.solicitanteLabel(component.sugerencias[0])).toBe('Usuario #9');
  });
});