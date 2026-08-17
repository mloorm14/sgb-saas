import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { LibroPublicoService } from '../../core/services/libro-publico.service';
import { Libro } from '../../core/models/libro.model';

// Detalle de libro del portal público (Rama C, mockup 13). Sin sesión:
// portada directa en <img>, y las acciones que requieren cuenta se
// bloquean con el cartel "requieren una cuenta" + Crear cuenta.
@Component({
  selector: 'app-detalle-publico',
  imports: [CommonModule, RouterLink],
  templateUrl: './detalle-publico.component.html'
})
export class DetallePublicoComponent implements OnInit {
  libro: Libro | null = null;
  errorMsg: string = '';

  constructor(
    private route: ActivatedRoute,
    private libroPublicoService: LibroPublicoService
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!id) {
      this.errorMsg = 'Libro no encontrado';
      return;
    }
    this.libroPublicoService.obtener(id).subscribe({
      next: (libro) => (this.libro = libro),
      error: () => (this.errorMsg = 'Error al cargar el libro')
    });
  }

  portadaUrl(libroId: number): string {
    return this.libroPublicoService.portadaUrl(libroId);
  }

  categoriasTexto(): string {
    return this.libro?.categorias?.length ? this.libro.categorias.join(' · ') : '';
  }

  autoresTexto(): string {
    return this.libro?.autores?.length ? this.libro.autores.join(', ') : '—';
  }

  // "Robert C. Martin — Prentice Hall, 2008": se omiten las partes vacías.
  detalleAutor(): string {
    if (!this.libro) return '';
    const partes = [this.autoresTexto(), this.libro.editorial, String(this.libro.anioPublicacion)]
      .filter(p => p && p !== '—');
    return partes.join(' — ');
  }
}