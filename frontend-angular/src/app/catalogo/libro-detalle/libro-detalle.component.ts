import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { LibroService } from '../../core/services/libro.service';
import { FavoritoService } from '../../core/services/favorito.service';
import { Libro } from '../../core/models/libro.model';
import { PortadaLibroComponent } from '../../shared/portada-libro/portada-libro.component';

// Detalle de libro del consumidor (Rama B). El estado de favoritos se
// resuelve con FavoritoService.listar al montar, igual que en el catálogo.
// El link "Sugerir adquisición" va siempre visible (el mockup 05 corrigió
// que solo aparezca con stock 0) y prellena el título del formulario.
@Component({
  selector: 'app-libro-detalle',
  imports: [CommonModule, RouterLink, PortadaLibroComponent],
  templateUrl: './libro-detalle.component.html'
})
export class LibroDetalleComponent implements OnInit {
  libro: Libro | null = null;
  favoritosIds = new Set<number>();
  errorMsg: string = '';

  constructor(
    private route: ActivatedRoute,
    private libroService: LibroService,
    private favoritoService: FavoritoService
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!id) {
      this.errorMsg = 'Libro no encontrado';
      return;
    }
    this.libroService.obtener(id).subscribe({
      next: (libro) => (this.libro = libro),
      error: () => (this.errorMsg = 'Error al cargar el libro')
    });
    this.favoritoService.listar().subscribe({
      next: (favoritos) => {
        this.favoritosIds = new Set(favoritos.map(f => f.libroId));
      },
      error: () => {}
    });
  }

  esFavorito(): boolean {
    return this.libro !== null && this.favoritosIds.has(this.libro.id);
  }

  // El icono Material star se rellena con font-variation-settings 'FILL' 1
  // (ver catalogo.component: sin comillas internas en el binding del template).
  estiloIconoFavorito(): string {
    return this.esFavorito() ? '"FILL" 1' : '"FILL" 0';
  }

  alternarFavorito(): void {
    if (!this.libro) return;
    if (this.esFavorito()) {
      this.favoritoService.quitar(this.libro.id).subscribe({
        next: () => this.favoritosIds.delete(this.libro!.id),
        error: () => (this.errorMsg = 'Error al quitar de favoritos')
      });
    } else {
      this.favoritoService.agregar(this.libro.id).subscribe({
        next: () => this.favoritosIds.add(this.libro!.id),
        error: () => (this.errorMsg = 'Error al agregar a favoritos')
      });
    }
  }

  categoriasTexto(): string {
    return this.libro?.categorias?.length ? this.libro.categorias.join(' · ') : '';
  }

  autoresTexto(): string {
    return this.libro?.autores?.length ? this.libro.autores.join(', ') : '—';
  }
}