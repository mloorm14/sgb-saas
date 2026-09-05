import { Component, input, output } from '@angular/core';
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
}
