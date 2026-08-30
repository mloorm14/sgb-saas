// Helper único para serializar fechaRetiro hacia el backend.
// El backend espera OffsetDateTime (ReservacionRequestDTO.java:25) en formato
// ISO-8601 con offset, ej. "2026-08-30T12:00:00-05:00". Usar siempre offset
// local (getTimezoneOffset) para evitar isBefore(ahora) falso y el bug de
// toISOString().slice(-6) que producía "T00:00:007.477Z" → 400.
export function toOffsetDateTime(fechaYYYYMMDD: string, hora: string = '12:00:00'): string {
  const off = -new Date().getTimezoneOffset();
  const sign = off >= 0 ? '+' : '-';
  const hh = String(Math.floor(Math.abs(off) / 60)).padStart(2, '0');
  const mm = String(Math.abs(off) % 60).padStart(2, '0');
  return `${fechaYYYYMMDD}T${hora}${sign}${hh}:${mm}`;
}
