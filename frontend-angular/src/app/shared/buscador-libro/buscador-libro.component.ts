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
  selector: 'app-buscador-libro',
  imports: [CommonModule, FormsModule],
  templateUrl: './buscador-libro.component.html'
})
export class BuscadorLibroComponent implements OnInit, OnDestroy {
  texto: string = '';
  sugerencias: LibroSugerencia[] = [];
  buscando: boolean = false;
  seleccionado: LibroSugerencia | null = null;

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
    this.seleccionado = libro;
    this.texto = libro.titulo;
    this.sugerencias = [];
    this.libroSeleccionado.emit(libro);
  }

  // Lo llama el padre tras guardar, para dejar el buscador listo.
  limpiar(): void {
    this.texto = '';
    this.sugerencias = [];
    this.seleccionado = null;
  }
}