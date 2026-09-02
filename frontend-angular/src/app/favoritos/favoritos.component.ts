import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
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
  imports: [CommonModule, FormsModule, RouterLink, PortadaLibroComponent],
  templateUrl: './favoritos.component.html'
})
export class FavoritosComponent implements OnInit {
  favoritos: Favorito[] = [];
  cargando: boolean = false;
  errorMsg: string = '';

  pagina = 0;
  tamanoPagina = 10;

  get totalPaginas(): number {
    return Math.max(1, Math.ceil(this.favoritos.length / this.tamanoPagina));
  }
  get datosPaginados() {
    const start = this.pagina * this.tamanoPagina;
    return this.favoritos.slice(start, start + this.tamanoPagina);
  }
  get paginasVisibles(): number[] {
    const windowSize = 4;
    let start = Math.max(0, this.pagina - 1);
    let end = Math.min(this.totalPaginas, start + windowSize);
    if (end - start < windowSize) start = Math.max(0, end - windowSize);
    return Array.from({ length: end - start }, (_, i) => start + i);
  }
  get puedeAnterior(): boolean { return this.pagina > 0; }
  get puedeSiguiente(): boolean { return this.pagina < this.totalPaginas - 1; }
  irAPagina(p: number): void {
    if (p < 0 || p >= this.totalPaginas || p === this.pagina) return;
    this.pagina = p;
  }
  paginaAnterior(): void { if (this.puedeAnterior) this.pagina--; }
  paginaSiguiente(): void { if (this.puedeSiguiente) this.pagina++; }
  cambiarTamano(n: number): void {
    this.tamanoPagina = Number(n);
    this.pagina = 0;
  }

  constructor(private favoritoService: FavoritoService) {}

  ngOnInit(): void {
    this.cargarFavoritos();
  }

  private cargarFavoritos(): void {
    this.cargando = true;
    this.favoritoService.listar().subscribe({
      next: (favoritos) => {
        this.favoritos = favoritos;
        this.pagina = 0;
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