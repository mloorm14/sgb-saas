// ============================================================================
// k6/libros-listado-test.js — Bloque C.1: prueba de carga real contra
// GET /api/v1/libros (el endpoint con cache Redis vía @Cacheable("libros")
// en LibroService), para medir el efecto de cache caliente vs cache frío
// sobre la latencia.
//
// Dos escenarios, cada uno con el mismo perfil de VUs/duración importado
// de k6/opts.js (50 VUs, ramp-up 10s / sostenido 30s / ramp-down 10s):
//
//   - cache_caliente: siempre pide page=0&size=10 (misma key de cache en
//     LibroService#listar). Tras el primer hit, el resto de peticiones de
//     ese mismo Pageable leen del cache Redis -> cache caliente.
//   - cache_frio: pide una página distinta en cada request (PRNG propio,
//     semilla fija = 42, ver justificación de determinismo en k6/opts.js),
//     lo que genera una key de @Cacheable distinta cada vez -> siempre
//     cache miss -> siempre golpea la base de datos.
//
// Los escenarios corren en tiempos separados (cache_frio arranca tras el
// fin de cache_caliente) para no mezclar ambas cargas sobre el mismo
// backend en el mismo instante y así poder atribuir la latencia observada
// a cada condición de cache de forma limpia.
// ============================================================================
import http from 'k6/http';
import { check, sleep } from 'k6';
import { options as baseOptions } from './opts.js';

const BASE_URL = __ENV.BASE_URL || 'http://backend:8080';
const ADMIN_CORREO = __ENV.ADMIN_CORREO || 'admin@sgb-saas.local';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'Admin123!';

// PRNG determinista (mulberry32, semilla fija 42) para elegir la página en
// el escenario cache_frio — ver k6/opts.js: "si un script concreto
// necesita aleatoriedad real, ESE script debe implementar su propio PRNG
// determinista sembrado con una constante fija".
function mulberry32(seed) {
    return function () {
        seed |= 0;
        seed = (seed + 0x6D2B79F5) | 0;
        let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
        t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
}
const rng = mulberry32(42);

export const options = {
    scenarios: {
        cache_caliente: {
            executor: 'ramping-vus',
            exec: 'cacheCaliente',
            startVUs: 0,
            stages: baseOptions.stages,
            gracefulRampDown: '5s',
        },
        cache_frio: {
            executor: 'ramping-vus',
            exec: 'cacheFrio',
            startVUs: 0,
            stages: baseOptions.stages,
            startTime: '55s',
            gracefulRampDown: '5s',
        },
    },
    thresholds: {
        // Bloque C.1: p95 < 200ms con cache caliente, p95 < 500ms con cache frío.
        'http_req_duration{scenario:cache_caliente}': ['p(95)<200'],
        'http_req_duration{scenario:cache_frio}': ['p(95)<500'],
        http_req_failed: ['rate==0'],
    },
};

export function setup() {
    const res = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ correo: ADMIN_CORREO, password: ADMIN_PASSWORD }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    check(res, { 'login devuelve 200': (r) => r.status === 200 });
    const body = res.json();
    return { token: body.accessToken };
}

export function cacheCaliente(data) {
    const res = http.get(`${BASE_URL}/api/v1/libros?page=0&size=10`, {
        headers: { Authorization: `Bearer ${data.token}` },
    });
    check(res, { '200 (cache caliente)': (r) => r.status === 200 });
    sleep(1);
}

export function cacheFrio(data) {
    const page = Math.floor(rng() * 1000);
    const res = http.get(`${BASE_URL}/api/v1/libros?page=${page}&size=10`, {
        headers: { Authorization: `Bearer ${data.token}` },
    });
    check(res, { '200 (cache frio)': (r) => r.status === 200 });
    sleep(1);
}
