import { Component, Input, OnChanges, OnDestroy } from '@angular/core';
import { LibroService } from '../../core/services/libro.service';

// Portada binaria compartida (Ramas B y E). El backend sirve la portada en
// /v1/libros/{id}/portada con Content-Type dinámico, pero NO puede usarse
// directo en <img src> porque requiere el header Authorization (jwt.interceptor):
// se resuelve el Blob vía LibroService.obtenerPortada() y se expone un
// ObjectURL, que se revoca en ngOnDestroy para no filtrar memoria.
// Parámetros:
//  - libroId: id del libro.
//  - tienePortada: flag del LibroResponseDTO. Si viene false se muestra el
//    placeholder directo. Si viene undefined (p.ej. FavoritoResponseDTO no
//    trae el flag), se intenta cargar igual y se cae al placeholder ante
//    404 (libro sin portada binaria).
@Component({
  selector: 'app-portada-libro',
  imports: [],
  templateUrl: './portada-libro.component.html'
})
export class PortadaLibroComponent implements OnChanges, OnDestroy {
  @Input() libroId!: number;
  @Input() tienePortada?: boolean;

  imagenUrl: string | null = null;
  cargando: boolean = false;

  constructor(private libroService: LibroService) {}

  ngOnChanges(): void {
    this.recargar();
  }

  ngOnDestroy(): void {
    this.liberarUrl();
  }

  private recargar(): void {
    this.liberarUrl();
    this.cargando = false;
    if (this.tienePortada === false || this.libroId === undefined) {
      return;
    }
    this.cargando = true;
    this.libroService.obtenerPortada(this.libroId).subscribe({
      next: (blob) => {
        this.imagenUrl = URL.createObjectURL(blob);
        this.cargando = false;
      },
      error: () => {
        // 404 = el libro no tiene portada binaria: placeholder.
        this.imagenUrl = null;
        this.cargando = false;
      }
    });
  }

  private liberarUrl(): void {
    if (this.imagenUrl) {
      URL.revokeObjectURL(this.imagenUrl);
      this.imagenUrl = null;
    }
  }
}