import {
  requiereConfirmacionHoraLimite,
  fechaMananaISO,
  fechaMaxRetiroISO,
  aFechaStr
} from './hora-limite';

describe('hora-limite (OBS-20)', () => {
  // 2026-09-04: fechas fijas locales (sin zona) para determinismo total.
  const manana10h = new Date(2026, 8, 4, 10, 0, 0);
  const tarde19h = new Date(2026, 8, 4, 19, 30, 0);
  const justo18h = new Date(2026, 8, 4, 18, 0, 0);

  it('no requiere confirmación antes de las 18:00 aunque sea hoy', () => {
    expect(requiereConfirmacionHoraLimite('2026-09-04', manana10h)).toBeFalse();
  });

  it('requiere confirmación a las 18:00 o después cuando es hoy', () => {
    expect(requiereConfirmacionHoraLimite('2026-09-04', justo18h)).toBeTrue();
    expect(requiereConfirmacionHoraLimite('2026-09-04', tarde19h)).toBeTrue();
  });

  it('no requiere confirmación si la fecha ya es futura (mañana elegido a mano)', () => {
    expect(requiereConfirmacionHoraLimite('2026-09-05', tarde19h)).toBeFalse();
  });

  it('respeta una hora límite distinta (regla configurable)', () => {
    expect(requiereConfirmacionHoraLimite('2026-09-04', tarde19h, 20)).toBeFalse();
    expect(requiereConfirmacionHoraLimite('2026-09-04', tarde19h, 19)).toBeTrue();
  });

  it('fechaMananaISO suma un día calendario', () => {
    expect(fechaMananaISO(tarde19h)).toBe('2026-09-05');
  });

  it('fechaMananaISO cruza fin de mes correctamente', () => {
    expect(fechaMananaISO(new Date(2026, 7, 31, 19, 0, 0))).toBe('2026-09-01');
  });

  it('fechaMaxRetiroISO es hoy + 14 días por defecto', () => {
    expect(fechaMaxRetiroISO(manana10h)).toBe('2026-09-18');
  });

  it('aFechaStr usa hora local sin desplazar el día', () => {
    expect(aFechaStr(new Date(2026, 0, 5, 0, 30, 0))).toBe('2026-01-05');
  });
});
