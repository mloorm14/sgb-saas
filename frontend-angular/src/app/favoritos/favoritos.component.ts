import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FavoritoService } from '../core/services/favorito.service';
import { Favorito } from '../core/models/favorito.model';
import { PortadaLibroComponent } from '../shared/portada-libro/portada-libro.component';

// Mis favoritos (Rama B, mockup 06). FavoritoService.listar devuelve array
// plano sin paginación. Gap real documentado: FavoritoResponseDTO no trae
// tienePortada/portadaNombre, así que la portada se intenta cargar igual y
// PortadaLibroComponent cae al placeholder si el backend responde 404.
@Component({
  standalone: true,
  selector: 'app-favoritos',
  imports: [CommonModule, RouterLink, PortadaLibroComponent],
  templateUrl: './favoritos.component.html'
})
export class FavoritosComponent implements OnInit {
  favoritos: Favorito[] = [];
  cargando: boolean = false;
  errorMsg: string = '';

  constructor(private favoritoService: FavoritoService) {}

  ngOnInit(): void {
    this.cargarFavoritos();
  }

  private cargarFavoritos(): void {
    this.cargando = true;
    this.favoritoService.listar().subscribe({
      next: (favoritos) => {
        this.favoritos = favoritos;
        this.cargando = false;
      },
      error: () => {
        this.errorMsg = 'Error al cargar los favoritos';
        this.cargando = false;
      }
    });
  }

  quitar(favorito: Favorito): void {
    this.favoritoService.quitar(favorito.libroId).subscribe({
      next: () => {
        this.favoritos = this.favoritos.filter(f => f.libroId !== favorito.libroId);
      },
      error: () => (this.errorMsg = 'Error al quitar de favoritos')
    });
  }

  // "12 ago 2026": el backend envía ISO (LocalDateTime). Formato corto en
  // español, igual que en los mockups 06 y 08.
  formatearFecha(iso: string): string {
    if (!iso) return '';
    const fecha = new Date(iso);
    const meses = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
    return `${fecha.getDate()} ${meses[fecha.getMonth()]} ${fecha.getFullYear()}`;
  }
}