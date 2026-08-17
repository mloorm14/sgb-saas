import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../core/services/auth.service';
import { PrestamoService } from '../core/services/prestamo.service';
import { LibroService } from '../core/services/libro.service';
import { Prestamo, PrestamoActivo } from '../core/models/prestamo.model';

@Component({
  selector: 'app-prestamos-lector',
  imports: [CommonModule],
  templateUrl: './prestamos-lector.component.html'
})
export class PrestamosLectorComponent implements OnInit {
  prestamos: Prestamo[] = [];
  prestamosActivos: PrestamoActivo[] = [];
  totalPages: number = 0;
  currentPage: number = 0;
  pageSize: number = 10;
  cargando: boolean = false;
  cargandoActivos: boolean = false;
  errorMsg: string = '';
  errorMsgActivos: string = '';
  renovandoId: number | null = null;

  // Mapa local de estados: EstadoPrestamo existe como entidad + repository
  // en el backend, pero ningún controller expone el catálogo (gap del
  // roadmap, mismo criterio que se usó con tipoNotificacionId). Workaround
  // temporal hasta que exista un endpoint de catálogo. IDs/nombres
  // confirmados contra db/seed.sql y V10__seed_catalogos_y_admin.sql
  // (inserts en orden SERIAL: 1 ACTIVO, 2 RENOVADO, 3 DEVUELTO, 4 VENCIDO).
  readonly estadosPrestamo: Record<number, string> = {
    1: 'Activo',
    2: 'Renovado',
    3: 'Devuelto',
    4: 'Vencido'
  };

  // Cache de títulos por libroId: PrestamoResponseDTO (historial) solo trae
  // libroId; se resuelve con LibroService.obtener() UNA vez por libro para
  // no repetir N requests idénticos cuando varias filas comparten libro.
  private titulosLibros = new Map<number, string>();
  private titulosEnCarga = new Set<number>();

  constructor(
    private prestamoService: PrestamoService,
    private libroService: LibroService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.cargarActivos();
    this.cargarPrestamos();
  }

  cargarActivos(): void {
    const miId = this.authService.getUserId();
    if (!miId) return;
    this.cargandoActivos = true;
    this.prestamoService.activosPorUsuario(miId).subscribe({
      next: (activos) => {
        this.prestamosActivos = activos;
        this.cargandoActivos = false;
      },
      error: () => {
        this.errorMsgActivos = 'Error al cargar tus préstamos activos';
        this.cargandoActivos = false;
      }
    });
  }

  cargarPrestamos(): void {
    const miId = this.authService.getUserId();
    if (!miId) {
      this.errorMsg = 'No se pudo identificar al usuario logueado';
      return;
    }
    this.cargando = true;
    this.prestamoService.listarPorUsuario(miId, {
      page: this.currentPage,
      size: this.pageSize,
      sort: 'id,desc'
    }).subscribe({
      next: (data) => {
        this.prestamos = data.content;
        this.totalPages = data.totalPages;
        this.cargando = false;
      },
      error: () => {
        this.errorMsg = 'Error al cargar tus préstamos';
        this.cargando = false;
      }
    });
  }

  tituloLibro(libroId: number): string {
    const cacheado = this.titulosLibros.get(libroId);
    if (cacheado) return cacheado;
    if (!this.titulosEnCarga.has(libroId)) {
      this.titulosEnCarga.add(libroId);
      this.libroService.obtener(libroId).subscribe({
        next: (libro) => this.titulosLibros.set(libroId, libro.titulo),
        error: () => this.titulosLibros.set(libroId, `Libro #${libroId}`)
      });
    }
    return `Libro #${libroId}`; // placeholder mientras llega la respuesta
  }

  renovar(prestamoId: number): void {
    this.renovandoId = prestamoId;
    this.prestamoService.renovar(prestamoId).subscribe({
      next: () => {
        this.renovandoId = null;
        this.cargarActivos();
      },
      error: () => {
        this.renovandoId = null;
        this.errorMsgActivos = 'No se pudo renovar el préstamo';
      }
    });
  }

  // Semáforo de días restantes: verde >3, amarillo 1-3, rojo vencido.
  claseDias(dias: number): string {
    if (dias < 0) return 'text-error';
    if (dias <= 3) return 'text-warning';
    return 'text-success';
  }

  claseLinea(dias: number): string {
    if (dias < 0) return 'bg-error';
    if (dias <= 3) return 'bg-warning';
    return 'bg-success';
  }

  textoVencimiento(dias: number): string {
    if (dias < 0) return `Venció hace ${-dias} ${-dias === 1 ? 'día' : 'días'}`;
    if (dias === 0) return 'Vence hoy';
    return `Vence en ${dias} ${dias === 1 ? 'día' : 'días'}`;
  }

  claseEstado(estadoId: number): string {
    switch (estadoId) {
      case 3: return 'bg-surface-container-low text-secondary';            // Devuelto
      case 4: return 'bg-error-container text-on-error-container';         // Vencido
      default: return 'bg-secondary-container text-on-secondary-container'; // Activo / Renovado
    }
  }

  paginaAnterior(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.cargarPrestamos();
    }
  }

  paginaSiguiente(): void {
    if (this.currentPage < this.totalPages - 1) {
      this.currentPage++;
      this.cargarPrestamos();
    }
  }
}