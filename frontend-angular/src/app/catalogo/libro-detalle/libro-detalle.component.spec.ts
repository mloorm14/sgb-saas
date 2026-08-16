import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { LibroDetalleComponent } from './libro-detalle.component';
import { ActivatedRoute } from '@angular/router';
import { LibroService } from '../../core/services/libro.service';
import { FavoritoService } from '../../core/services/favorito.service';
import { Libro } from '../../core/models/libro.model';

describe('LibroDetalleComponent', () => {
  let component: LibroDetalleComponent;
  let fixture: ComponentFixture<LibroDetalleComponent>;
  let libroService: jasmine.SpyObj<LibroService>;
  let favoritoService: jasmine.SpyObj<FavoritoService>;

  beforeEach(async () => {
    libroService = jasmine.createSpyObj('LibroService', ['obtener', 'obtenerPortada']);
    favoritoService = jasmine.createSpyObj('FavoritoService', ['listar', 'agregar', 'quitar']);

    libroService.obtenerPortada.and.returnValue(throwError(() => ({ status: 404 })));

    libroService.obtener.and.returnValue(of({
      id: 5,
      titulo: 'Clean Code',
      isbn: '978-0132350884',
      resumen: 'Prácticas de código limpio.',
      portadaUrl: '',
      tienePortada: true,
      portadaNombre: '',
      portadaTipo: '',
      anioPublicacion: 2008,
      editorialId: 1,
      editorial: 'Prentice Hall',
      idiomaId: 1,
      idioma: 'Español',
      estadoId: 1,
      estado: 'Disponible',
      stockTotal: 6,
      stockDisponible: 4,
      ubicacionFisica: '',
      fechaRegistro: '',
      categorias: ['Ingeniería de Software'],
      autores: ['Robert C. Martin']
    } as Libro));
    favoritoService.listar.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [LibroDetalleComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: (clave: string) => (clave === 'id' ? '5' : null) } } }
        },
        { provide: LibroService, useValue: libroService },
        { provide: FavoritoService, useValue: favoritoService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(LibroDetalleComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('carga el libro por el id de la ruta', () => {
    expect(libroService.obtener).toHaveBeenCalledWith(5);
    expect(component.libro?.titulo).toBe('Clean Code');
    expect(component.categoriasTexto()).toBe('Ingeniería de Software');
    expect(component.autoresTexto()).toBe('Robert C. Martin');
  });

  it('agrega el libro a favoritos desde el detalle', () => {
    favoritoService.agregar.and.returnValue(of({ usuarioId: 1, libroId: 5, tituloLibro: 'Clean Code', agregadoEn: '' }));

    component.alternarFavorito();

    expect(favoritoService.agregar).toHaveBeenCalledWith(5);
    expect(component.esFavorito()).toBeTrue();
  });

  it('quita el libro de favoritos si ya estaba marcado', () => {
    favoritoService.listar.and.returnValue(of([{ usuarioId: 1, libroId: 5, tituloLibro: 'Clean Code', agregadoEn: '' }]));
    component.ngOnInit();
    fixture.detectChanges();
    favoritoService.quitar.and.returnValue(of(undefined));

    expect(component.esFavorito()).toBeTrue();
    component.alternarFavorito();

    expect(favoritoService.quitar).toHaveBeenCalledWith(5);
    expect(component.esFavorito()).toBeFalse();
  });
});