import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { SugerenciasFormComponent } from './sugerencias-form.component';
import { ActivatedRoute, Router } from '@angular/router';
import { SugerenciaAdquisicionService } from '../../core/services/sugerencia-adquisicion.service';

describe('SugerenciasFormComponent', () => {
  let component: SugerenciasFormComponent;
  let fixture: ComponentFixture<SugerenciasFormComponent>;
  let sugerenciaService: jasmine.SpyObj<SugerenciaAdquisicionService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    sugerenciaService = jasmine.createSpyObj('SugerenciaAdquisicionService', ['crear']);
    router = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [SugerenciasFormComponent],
      providers: [
        { provide: SugerenciaAdquisicionService, useValue: sugerenciaService },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => null } } } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SugerenciasFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('no envía si el formulario está inválido (título obligatorio)', () => {
    component.form.patchValue({ titulo: '', autor: '', isbn: '', justificacion: '' });
    component.enviar();

    expect(sugerenciaService.crear).not.toHaveBeenCalled();
  });

  it('envía la sugerencia con el DTO correcto y navega a mis solicitudes', () => {
    sugerenciaService.crear.and.returnValue(of({
      id: 1, usuarioId: 1, titulo: 'DDIA', autor: 'Kleppmann', isbn: '978-144937332',
      justificacion: 'Muy usado', estado: 'PENDIENTE', revisadoPor: 0, creadoEn: ''
    }));

    component.form.patchValue({
      titulo: 'Designing Data-Intensive Applications',
      autor: 'Martin Kleppmann',
      isbn: '978-144937332',
      justificacion: 'Muy usado en la carrera'
    });
    component.enviar();

    expect(sugerenciaService.crear).toHaveBeenCalledWith({
      titulo: 'Designing Data-Intensive Applications',
      autor: 'Martin Kleppmann',
      isbn: '978-144937332',
      justificacion: 'Muy usado en la carrera'
    });
    expect(router.navigate).toHaveBeenCalledWith(['/sugerencias']);
  });

  it('omite los opcionales vacíos (isbn "") del DTO para no romper el @Pattern del backend', () => {
    sugerenciaService.crear.and.returnValue(of({
      id: 1, usuarioId: 1, titulo: 'Solo título', autor: '', isbn: '', justificacion: '',
      estado: 'PENDIENTE', revisadoPor: 0, creadoEn: ''
    }));

    component.form.patchValue({ titulo: 'Solo título', autor: '', isbn: '', justificacion: '' });
    component.enviar();

    expect(sugerenciaService.crear).toHaveBeenCalledWith({ titulo: 'Solo título' });
    expect(router.navigate).toHaveBeenCalledWith(['/sugerencias']);
  });

  it('rechaza un ISBN con formato inválido', () => {
    component.form.patchValue({ titulo: 'T', autor: '', isbn: 'abc', justificacion: '' });
    expect(component.form.invalid).toBeTrue();
    expect(component.form.get('isbn')?.hasError('pattern')).toBeTrue();
  });

  it('muestra el contador de caracteres de la justificación', () => {
    component.form.patchValue({ justificacion: 'abc' });
    expect(component.form.get('justificacion')?.value.length).toBe(3);
  });

  it('muestra el error del backend sin romper la UI', () => {
    sugerenciaService.crear.and.returnValue(throwError(() => ({ status: 500 })));

    component.form.patchValue({ titulo: 'Título', autor: '', isbn: '', justificacion: '' });
    component.enviar();

    expect(component.errorMsg).toBe('Error al enviar la sugerencia');
    expect(component.cargando).toBeFalse();
  });

  it('prellena el título desde el query param del detalle de libro', () => {
    TestBed.resetTestingModule();
    const sugerenciaService2 = jasmine.createSpyObj('SugerenciaAdquisicionService', ['crear']);
    const router2 = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      imports: [SugerenciasFormComponent],
      providers: [
        { provide: SugerenciaAdquisicionService, useValue: sugerenciaService2 },
        { provide: Router, useValue: router2 },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: { get: (clave: string) => (clave === 'titulo' ? 'Clean Code' : null) } } }
        }
      ]
    });

    const fixture2 = TestBed.createComponent(SugerenciasFormComponent);
    const component2 = fixture2.componentInstance;
    fixture2.detectChanges();

    expect(component2.form.get('titulo')?.value).toBe('Clean Code');
  });
});