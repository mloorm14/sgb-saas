import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { MisSugerenciasComponent } from './mis-sugerencias.component';
import { SugerenciaAdquisicionService } from '../../core/services/sugerencia-adquisicion.service';
import { ActivatedRoute } from '@angular/router';

describe('MisSugerenciasComponent', () => {
  let component: MisSugerenciasComponent;
  let fixture: ComponentFixture<MisSugerenciasComponent>;
  let sugerenciaService: jasmine.SpyObj<SugerenciaAdquisicionService>;

  const sugerencia = (id: number, estado: string) => ({
    id, usuarioId: 1, titulo: `Sugerencia ${id}`, autor: 'Autor', isbn: '', justificacion: '',
    estado, revisadoPor: 0, creadoEn: '2026-08-10T10:00:00'
  });

  beforeEach(async () => {
    sugerenciaService = jasmine.createSpyObj('SugerenciaAdquisicionService', ['listarMias']);

    await TestBed.configureTestingModule({
      imports: [MisSugerenciasComponent],
      providers: [
        { provide: SugerenciaAdquisicionService, useValue: sugerenciaService },
        { provide: ActivatedRoute, useValue: { snapshot: {} } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MisSugerenciasComponent);
    component = fixture.componentInstance;
  });

  it('carga las solicitudes paginadas ordenadas por creadoEn descendente', () => {
    sugerenciaService.listarMias.and.returnValue(of({ content: [sugerencia(1, 'PENDIENTE')], totalPages: 1 } as any));

    fixture.detectChanges();

    expect(sugerenciaService.listarMias).toHaveBeenCalledWith({
      page: 0,
      size: 10,
      sort: 'creadoEn,desc'
    });
    expect(component.sugerencias.length).toBe(1);
  });

  it('formatea la fecha en español', () => {
    expect(component.formatearFecha('2026-08-10T10:00:00')).toBe('10 ago 2026');
  });

  it('pagina y vuelve a consultar la página solicitada', () => {
    sugerenciaService.listarMias.and.returnValue(of({ content: [], totalPages: 4 } as any));
    fixture.detectChanges();
    expect(component.paginasVisibles).toEqual([0, 1, 2, 3]);

    component.irAPagina(3);
    expect(component.currentPage).toBe(3);
    expect(sugerenciaService.listarMias).toHaveBeenCalledWith(jasmine.objectContaining({ page: 3 }));

    component.irAPagina(3); // misma página: no recarga
    expect(sugerenciaService.listarMias).toHaveBeenCalledTimes(2);
  });

  it('muestra el error del backend sin romper la UI', () => {
    sugerenciaService.listarMias.and.returnValue(throwError(() => ({ status: 500 })));

    fixture.detectChanges();

    expect(component.errorMsg).toBe('Error al cargar las solicitudes');
    expect(component.cargando).toBeFalse();
  });

  it('avanza y retrocede con Anterior/Siguiente recargando la página', () => {
    sugerenciaService.listarMias.and.returnValue(of({ content: [], totalPages: 4 } as any));
    fixture.detectChanges();

    component.paginaSiguiente();
    expect(component.currentPage).toBe(1);
    expect(sugerenciaService.listarMias).toHaveBeenCalledWith(jasmine.objectContaining({ page: 1 }));

    component.paginaAnterior();
    expect(component.currentPage).toBe(0);

    component.paginaAnterior(); // ya en la primera: no recarga
    expect(component.currentPage).toBe(0);
  });

  it('cambiar el tamaño vuelve a la primera página con el nuevo size', () => {
    sugerenciaService.listarMias.and.returnValue(of({ content: [], totalPages: 4 } as any));
    fixture.detectChanges();

    component.currentPage = 2;
    component.cambiarTamano(20);

    expect(component.pageSize).toBe(20);
    expect(component.currentPage).toBe(0);
    expect(sugerenciaService.listarMias).toHaveBeenCalledWith(jasmine.objectContaining({ size: 20, page: 0 }));
  });
});