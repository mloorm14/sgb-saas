import { Component, EventEmitter, OnDestroy, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { LibroService } from '../../core/services/libro.service';
import { LibroSugerencia } from '../../core/models/libro.model';

// Buscador predictivo de libros compartido (Rama B): mismo patrón del
// buscador del catálogo — debounceTime(300) + distinctUntilChanged +
// LibroService.sugerencias(texto). Emite el libro elegido; si el texto
// cambia después de elegir, emite null para que el padre invalide su
// selección (el libroId ya no corresponde). Rama E lo reutilizará en
// prestamos-gestion para elegir el libro al crear un préstamo.
@Component({
  standalone: true,
  selector: 'app-buscador-libro',
  imports: [CommonModule, FormsModule],
  templateUrl: './buscador-libro.component.html'
})
export class BuscadorLibroComponent implements OnInit, OnDestroy {
  texto: string = '';
  sugerencias: LibroSugerencia[] = [];
  buscando: boolean = false;
  seleccionado: LibroSugerencia | null = null;
  indiceActivo: number = -1;

  @Output() libroSeleccionado = new EventEmitter<LibroSugerencia | null>();

  private busqueda$ = new Subject<string>();

  constructor(private libroService: LibroService) {}

  ngOnInit(): void {
    this.busqueda$.pipe(
      debounceTime(300),
      distinctUntilChanged()
    ).subscribe(texto => this.buscarSugerencias(texto));
  }

  ngOnDestroy(): void {
    this.busqueda$.complete();
  }

  onBusquedaChange(): void {
    this.indiceActivo = -1;
    const texto = this.texto.trim();
    // Si ya había un libro elegido y el texto cambió, la selección queda
    // inválida: se avisa al padre para que descarte el libroId.
    if (this.seleccionado && texto !== this.seleccionado.titulo) {
      this.seleccionado = null;
      this.libroSeleccionado.emit(null);
    }
    this.busqueda$.next(texto);
  }

  private buscarSugerencias(texto: string): void {
    if (texto.length < 2) {
      this.sugerencias = [];
      this.buscando = false;
      return;
    }
    this.buscando = true;
    this.libroService.sugerencias(texto).subscribe({
      next: (s) => {
        this.sugerencias = s;
        this.buscando = false;
      },
      error: () => {
        this.sugerencias = [];
        this.buscando = false;
      }
    });
  }

  seleccionar(libro: LibroSugerencia): void {
    this.indiceActivo = -1;
    this.seleccionado = libro;
    this.texto = libro.titulo;
    this.sugerencias = [];
    this.libroSeleccionado.emit(libro);
  }

  onKeydown(event: KeyboardEvent): void {
    if (this.sugerencias.length === 0) {
      if (event.key === 'Enter') {
        event.preventDefault();
      }
      return;
    }
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.indiceActivo = Math.min(this.indiceActivo + 1, this.sugerencias.length - 1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.indiceActivo = Math.max(this.indiceActivo - 1, -1);
        break;
      case 'Enter':
        event.preventDefault();
        if (this.indiceActivo >= 0 && this.indiceActivo < this.sugerencias.length) {
          this.seleccionar(this.sugerencias[this.indiceActivo]);
        }
        break;
      case 'Escape':
        this.sugerencias = [];
        this.indiceActivo = -1;
        break;
      case 'Tab':
        if (this.indiceActivo >= 0 && this.indiceActivo < this.sugerencias.length) {
          this.seleccionar(this.sugerencias[this.indiceActivo]);
        }
        break;
    }
  }

  // Lo llama el padre tras guardar, para dejar el buscador listo.
  limpiar(): void {
    this.texto = '';
    this.sugerencias = [];
    this.seleccionado = null;
  }
}