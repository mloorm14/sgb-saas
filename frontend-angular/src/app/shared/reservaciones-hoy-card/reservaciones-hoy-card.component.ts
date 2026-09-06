import { Component, input, output, computed, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ReservacionHoy } from '../../core/models/reservacion.model';

// Card compartida "Reservaciones de hoy" (antes duplicada en los
// homes de Bibliotecario y Gerente/Admin). Solo presenta datos:
// la carga y el cambio de estado los maneja el home anfitrión.
@Component({
  selector: 'app-reservaciones-hoy-card',
  standalone: true,
  templateUrl: './reservaciones-hoy-card.component.html'
})
export class ReservacionesHoyCardComponent {
  readonly reservaciones = input<ReservacionHoy[]>([]);
  readonly cargando = input(false);
  readonly error = input<string | null>(null);
  /** Gerente usa layout responsive de items; bibliotecario simple. */
  readonly modoResponsivo = input(false);

  readonly marcarListaParaRetiro = output<number>();

  pagina = signal(0);
  readonly tamanoPagina = 10;

  paginadas = computed(() => {
    const inicio = this.pagina() * this.tamanoPagina;
    return this.reservaciones().slice(inicio, inicio + this.tamanoPagina);
  });

  totalPaginas = computed(() => Math.max(1, Math.ceil(this.reservaciones().length / this.tamanoPagina)));
  puedeAnterior = computed(() => this.pagina() > 0);
  puedeSiguiente = computed(() => this.pagina() < this.totalPaginas() - 1);
  paginas = computed(() => Array.from({ length: this.totalPaginas() }, (_, i) => i));

  constructor(private router: Router) {}

  irAPagina(p: number) {
    if (p >= 0 && p < this.totalPaginas() && p !== this.pagina()) this.pagina.set(p);
  }
  paginaAnterior() { if (this.puedeAnterior()) this.pagina.update(p => p - 1); }
  paginaSiguiente() { if (this.puedeSiguiente()) this.pagina.update(p => p + 1); }

  irAReservarAhora(correo: string) {
    this.router.navigate(['/dashboard-bibliotecario/prestamos/gestion'], { queryParams: { q: correo } });
  }
}
