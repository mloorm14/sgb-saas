import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { FavoritosComponent } from './favoritos.component';
import { FavoritoService } from '../core/services/favorito.service';
import { ActivatedRoute } from '@angular/router';

describe('FavoritosComponent', () => {
  let component: FavoritosComponent;
  let fixture: ComponentFixture<FavoritosComponent>;
  let favoritoService: jasmine.SpyObj<FavoritoService>;

  beforeEach(async () => {
    favoritoService = jasmine.createSpyObj('FavoritoService', ['listar', 'quitar']);

    await TestBed.configureTestingModule({
      imports: [FavoritosComponent],
      providers: [
        { provide: FavoritoService, useValue: favoritoService },
        { provide: ActivatedRoute, useValue: { snapshot: {} } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(FavoritosComponent);
    component = fixture.componentInstance;
  });

  it('carga los favoritos del usuario y muestra la fecha formateada', () => {
    favoritoService.listar.and.returnValue(of([
      { usuarioId: 1, libroId: 5, tituloLibro: 'Clean Code', agregadoEn: '2026-08-12T10:00:00' }
    ]));

    fixture.detectChanges();

    expect(favoritoService.listar).toHaveBeenCalled();
    expect(component.favoritos.length).toBe(1);
    expect(component.formatearFecha('2026-08-12T10:00:00')).toBe('12 ago 2026');
  });

  it('quita un favorito y lo remueve de la grilla sin recargar', () => {
    favoritoService.listar.and.returnValue(of([
      { usuarioId: 1, libroId: 5, tituloLibro: 'Clean Code', agregadoEn: '' },
      { usuarioId: 1, libroId: 9, tituloLibro: 'Otro', agregadoEn: '' }
    ]));
    favoritoService.quitar.and.returnValue(of(undefined));
    fixture.detectChanges();

    component.quitar(component.favoritos[0]);

    expect(favoritoService.quitar).toHaveBeenCalledWith(5);
    expect(component.favoritos.length).toBe(1);
    expect(component.favoritos[0].libroId).toBe(9);
  });

  it('muestra error sin romper la UI si el backend falla', () => {
    favoritoService.listar.and.returnValue(throwError(() => ({ status: 500 })));

    fixture.detectChanges();

    expect(component.errorMsg).toBe('Error al cargar los favoritos');
    expect(component.cargando).toBeFalse();
  });
});