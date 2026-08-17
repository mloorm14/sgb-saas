import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { BuscadorLibroComponent } from './buscador-libro.component';
import { LibroService } from '../../core/services/libro.service';

describe('BuscadorLibroComponent', () => {
  let component: BuscadorLibroComponent;
  let fixture: ComponentFixture<BuscadorLibroComponent>;
  let libroService: jasmine.SpyObj<LibroService>;

  beforeEach(async () => {
    libroService = jasmine.createSpyObj('LibroService', ['sugerencias']);
    libroService.sugerencias.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [BuscadorLibroComponent],
      providers: [{ provide: LibroService, useValue: libroService }]
    }).compileComponents();

    fixture = TestBed.createComponent(BuscadorLibroComponent);
    component = fixture.componentInstance;
  });

  it('emite el libro seleccionado y muestra su título en el input', () => {
    const emitido = jasmine.createSpy('emitido');
    component.libroSeleccionado.subscribe(emitido);

    component.seleccionar({ id: 9, titulo: 'Refactoring', disponible: true });

    expect(emitido).toHaveBeenCalledWith(jasmine.objectContaining({ id: 9 }));
    expect(component.texto).toBe('Refactoring');
    expect(component.sugerencias.length).toBe(0);
  });

  it('emite null si el texto cambia después de elegir un libro (selección inválida)', () => {
    const emitido = jasmine.createSpy('emitido');
    component.libroSeleccionado.subscribe(emitido);
    component.seleccionar({ id: 9, titulo: 'Refactoring', disponible: true });

    component.texto = 'otro texto';
    component.onBusquedaChange();

    expect(emitido).toHaveBeenCalledWith(null);
  });

  it('no consulta sugerencias con menos de 2 caracteres', () => {
    component.texto = 'a';
    component.onBusquedaChange();

    expect(libroService.sugerencias).not.toHaveBeenCalled();
  });
});