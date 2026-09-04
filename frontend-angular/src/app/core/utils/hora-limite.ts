// Helpers puros para la hora límite de retiro de reservaciones (OBS-20).
// El backend exige retirar el mismo día hasta las 18:00; pasado ese horario
// la reserva se reprograma para mañana. Funciones puras a propósito: reciben
// `ahora` por parámetro para que los tests sean determinísticos sin mockear
// el reloj global (jasmine.clock rompería el debounceTime de búsqueda).
// La hora límite es configurable (default 18) por si la regla cambia.
export function requiereConfirmacionHoraLimite(
  fechaRetiroYYYYMMDD: string,
  ahora: Date,
  horaLimite = 18
): boolean {
  const hoyStr = aFechaStr(ahora);
  return fechaRetiroYYYYMMDD === hoyStr && ahora.getHours() >= horaLimite;
}

// Suma un día calendario a `ahora` y devuelve YYYY-MM-DD (para reprogramar
// fechaRetiro tras confirmar el diálogo de hora límite superada).
export function fechaMananaISO(ahora: Date): string {
  const manana = new Date(ahora);
  manana.setDate(manana.getDate() + 1);
  return aFechaStr(manana);
}

// YYYY-MM-DD en hora local (mismo criterio que minFechaRetiro de catálogo).
// OJO: usa getFullYear/getMonth/getDate locales, NO toISOString (UTC),
// para no desplazar el día en zonas UTC-5.
export function aFechaStr(fecha: Date): string {
  const y = fecha.getFullYear();
  const m = String(fecha.getMonth() + 1).padStart(2, '0');
  const d = String(fecha.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

// Límite superior del date picker: hoy + 14 días (misma regla que ya usaba
// CatalogoComponent/LibroDetalleComponent en ngOnInit).
export function fechaMaxRetiroISO(ahora: Date, dias = 14): string {
  const max = new Date(ahora);
  max.setDate(max.getDate() + dias);
  return aFechaStr(max);
}
