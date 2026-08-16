import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { PortadaLibroComponent } from './portada-libro.component';
import { LibroService } from '../../core/services/libro.service';

describe('PortadaLibroComponent', () => {
  let component: PortadaLibroComponent;
  let fixture: ComponentFixture<PortadaLibroComponent>;
  let libroService: jasmine.SpyObj<LibroService>;

  beforeEach(async () => {
    libroService = jasmine.createSpyObj('LibroService', ['obtenerPortada']);

    await TestBed.configureTestingModule({
      imports: [PortadaLibroComponent],
      providers: [{ provide: LibroService, useValue: libroService }]
    }).compileComponents();

    fixture = TestBed.createComponent(PortadaLibroComponent);
    component = fixture.componentInstance;
    component.libroId = 10;
  });

  it('si el libro no tiene portada (flag false) muestra el placeholder sin llamar al service', () => {
    component.tienePortada = false;
    component.ngOnChanges();

    fixture.detectChanges();

    expect(libroService.obtenerPortada).not.toHaveBeenCalled();
    expect(component.imagenUrl).toBeNull();
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('menu_book');
  });

  it('si el flag viene undefined (favoritos) intenta cargar igual y resuelve el ObjectURL del Blob', () => {
    spyOn(URL, 'createObjectURL').and.returnValue('blob:portada-falsa');
    libroService.obtenerPortada.and.returnValue(of(new Blob(['x'], { type: 'image/png' })));

    component.tienePortada = undefined;
    component.ngOnChanges();
    fixture.detectChanges();

    expect(libroService.obtenerPortada).toHaveBeenCalledWith(10);
    expect(component.imagenUrl).toBe('blob:portada-falsa');
    const img = fixture.nativeElement.querySelector('img');
    expect(img).not.toBeNull();
    expect(img.getAttribute('src')).toBe('blob:portada-falsa');
  });

  it('si la carga de la portada falla (404) cae al placeholder sin romper la UI', () => {
    libroService.obtenerPortada.and.returnValue(throwError(() => ({ status: 404 })));

    component.tienePortada = true;
    component.ngOnChanges();
    fixture.detectChanges();

    expect(component.imagenUrl).toBeNull();
    expect(component.cargando).toBeFalse();
    expect(fixture.nativeElement.textContent).toContain('menu_book');
  });

  it('al destruirse revoca el ObjectURL para no filtrar memoria', () => {
    const revoke = spyOn(URL, 'revokeObjectURL');
    spyOn(URL, 'createObjectURL').and.returnValue('blob:portada-falsa');
    libroService.obtenerPortada.and.returnValue(of(new Blob(['x'], { type: 'image/png' })));

    component.tienePortada = true;
    component.ngOnChanges();
    fixture.detectChanges();

    expect(component.imagenUrl).toBe('blob:portada-falsa');
    fixture.destroy();

    expect(revoke).toHaveBeenCalledWith('blob:portada-falsa');
    expect(component.imagenUrl).toBeNull();
  });
});