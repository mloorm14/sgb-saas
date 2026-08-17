import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { AuthService } from './auth.service';
import { ReservacionService } from './reservacion.service';

// Estado compartido de reservaciones "vigentes" del LECTOR logueado entre
// CatalogoComponent y LibroDetalleComponent: evita duplicar el request de
// listarPorUsuario y la logica de "este libro ya esta reservado por mi".
// "Vigente" = PENDIENTE(1) o LISTA_PARA_RETIRO(2), mismo criterio de
// "reserva vigente" que PrestamoService.renovar() (no RETIRADA/EXPIRADA/
// CANCELADA) -- IDs confirmados contra db/seed.sql y
// V10__seed_catalogos_y_admin.sql.
@Injectable({
  providedIn: 'root'
})
export class ReservacionPendienteService {

  private pendientesIds = new Set<number>();
  private cargado = false;

  constructor(
    private reservacionService: ReservacionService,
    private authService: AuthService
  ) {}

  // Un solo fetch (size 100, escala demo) y cache en memoria: el catálogo
  // y el detalle comparten el Set sin repetir requests.
  cargar(): Observable<void> {
    const usuarioId = this.authService.getUserId();
    if (this.cargado || usuarioId === null) {
      return of(undefined);
    }
    return new Observable<void>((subscriber) => {
      this.reservacionService.listarPorUsuario(usuarioId, {
        page: 0,
        size: 100,
        sort: 'id,desc'
      }).subscribe({
        next: (page) => {
          this.pendientesIds = new Set(
            page.content
              .filter(r => r.estadoReservacionId === 1 || r.estadoReservacionId === 2)
              .map(r => r.libroId)
          );
          this.cargado = true;
          subscriber.next();
          subscriber.complete();
        },
        error: (err) => subscriber.error(err)
      });
    });
  }

  esPendiente(libroId: number): boolean {
    return this.pendientesIds.has(libroId);
  }

  // Se llama tras crear una reservación con éxito: el libro pasa a
  // "Ya reservado" sin re-consultar el backend.
  marcarReservada(libroId: number): void {
    this.pendientesIds.add(libroId);
  }
}