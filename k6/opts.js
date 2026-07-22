// ============================================================================
// k6/opts.js — configuración base reutilizable para las pruebas de carga
// del Bloque C.1.
//
// SOLO configuración base. El script de prueba de carga en sí (las
// funciones que llaman a los endpoints, los checks, los thresholds de
// negocio, etc.) es un prompt aparte — esto únicamente deja fijo el
// perfil de VUs/duración/ramp-up que exige el Bloque C.1 (50 VUs, 30s de
// duración sostenida, con ramp-up declarado en vez de saltar directo a
// 50 VUs), para que cualquier script de prueba lo importe:
//
//     import { options } from '../k6/opts.js';
//     export { options };
//     export default function () { ... }
//
// ----------------------------------------------------------------------
// Sobre "determinismo" en pruebas de carga (Bloque B.2 exige semilla fija
// documentada para todo lo que genere aleatoriedad):
//
// k6 no tiene un RNG central con "semilla" tradicional como
// random.seed(42) en Python o --seed en otras herramientas. El
// generador de números aleatorios de JavaScript (Math.random(), si un
// script de prueba lo usara para variar payloads/paths) no es sembrable
// de forma nativa en k6. Por eso, para pruebas de carga, el equivalente
// correcto de "fijar la semilla" NO es sembrar un RNG — es fijar
// EXACTAMENTE el mismo perfil de carga (número de VUs, duración de cada
// etapa, curva de ramp-up/ramp-down) entre corridas que se quieran
// comparar. Dos corridas con las mismas `stages` de abajo son
// comparables entre sí (mismo estímulo aplicado al sistema); si un
// script de prueba concreto necesita aleatoriedad real (p. ej. elegir al
// azar entre varios endpoints con cierta probabilidad), ESE script debe
// implementar su propio PRNG determinista sembrado con una constante fija
// (documentado ahí, no aquí) en vez de depender de Math.random() sin
// sembrar — ver docs/mediciones/README.md, sección de semilla fija.
//
// En resumen: aquí el determinismo es "mismo perfil de carga siempre",
// no "mismo RNG siempre" — son dos conceptos de determinismo distintos y
// no deben confundirse al documentar una corrida en
// docs/mediciones/perf/.
// ============================================================================

export const options = {
    stages: [
        // Ramp-up declarado: sube gradualmente a 50 VUs en vez de saltar
        // directo, para no confundir un pico de arranque con el
        // comportamiento del sistema bajo carga sostenida.
        { duration: '10s', target: 50 },
        // Carga sostenida exigida por el Bloque C.1: 50 VUs durante 30s.
        { duration: '30s', target: 50 },
        // Ramp-down: baja a 0 VUs de forma ordenada antes de terminar.
        { duration: '10s', target: 0 },
    ],
};
