const { Pool } = require('pg');

let rawUrl = process.env.DATABASE_URL;
if (!rawUrl && process.env.DB_URL) {
    rawUrl = process.env.DB_URL.replace(/^jdbc:/, '');
}
let pool = null;
let poolConfig = null;
// true = TLS activado, false = plaintext. Lo consume dump.js para PGSSLMODE de pg_dump.
let sslEnabled = false;

function parseDbSslFlag(value) {
    if (value === undefined || value === null) return null;
    const v = String(value).trim().toLowerCase();
    if (v === '') return null; // definida pero vacia = no definida
    if (['true', '1', 'yes', 'y', 'on', 'require'].includes(v)) return true;
    if (['false', '0', 'no', 'n', 'off', 'disable'].includes(v)) return false;
    console.warn(`[db] DB_SSL='${value}' no reconocido, se ignora y se sigue con sslmode/host`);
    return null;
}

function sslFromSslMode(sslmode) {
    if (!sslmode) return null;
    const v = String(sslmode).trim().toLowerCase();
    if (['require', 'verify-ca', 'verify-full'].includes(v)) return true;
    if (v === 'disable') return false;
    return null; // prefer/allow: delegan al fallback por host
}

function sslFromHost(host) {
    const h = String(host || '').trim().toLowerCase();
    if (['localhost', '127.0.0.1', '::1', 'postgres'].includes(h)) return false;
    if (h.endsWith('.neon.tech') || h.endsWith('.neon.db') || h.includes('amazonaws.com')) return true;
    return false; // default seguro para dev: prod debe fijar DB_SSL=true o ?sslmode=require
}

// Precedencia: DB_SSL explicita > ?sslmode= de la URL > heuristica por host.
function resolveSsl(parsed) {
    const fromEnv = parseDbSslFlag(process.env.DB_SSL);
    if (fromEnv !== null) {
        console.log(`[db] ssl: ${fromEnv ? 'on' : 'off'} (origen: DB_SSL)`);
        return fromEnv;
    }
    const fromMode = sslFromSslMode(parsed.searchParams.get('sslmode'));
    if (fromMode !== null) {
        console.log(`[db] ssl: ${fromMode ? 'on' : 'off'} (origen: ?sslmode=)`);
        return fromMode;
    }
    const fromHost = sslFromHost(parsed.hostname);
    console.log(`[db] ssl: ${fromHost ? 'on' : 'off'} (origen: heuristica host '${parsed.hostname}')`);
    return fromHost;
}

if (!rawUrl) {
    console.warn('[db] DATABASE_URL vacia, pool no inicializado, /health retornara 503 hasta configurar env');
    pool = { query: async () => { throw new Error('DB no configurada'); }, end: async () => {}, connect: async () => { throw new Error('DB no configurada'); } };
} else {
    rawUrl = rawUrl.trim().replace(/^["']|["']$/g, '');
    console.log('[db] URL detectada (primeros 30 chars):', rawUrl.substring(0, 30) + '...');
    console.log('[db] Contiene neon.tech:', rawUrl.includes('neon.tech'));
    try {
        const parsed = new URL(rawUrl);
        const host = parsed.hostname;
        const port = parseInt(parsed.port) || 5432;
        const database = parsed.pathname.replace(/^\//, '');
        const user = decodeURIComponent(parsed.username);
        const password = decodeURIComponent(parsed.password);
        console.log('[db] host:', host);
        console.log('[db] port:', port);
        console.log('[db] database:', database);
        console.log('[db] user:', user);
        console.log('[db] password length:', password ? password.length : 'UNDEFINED/EMPTY');
        if (!password || typeof password !== 'string' || password.length === 0) {
            throw new Error('[db] La contrasena esta vacia tras parsear la URL.');
        }
        poolConfig = { host, port, database, user, password, query_timeout: 15000 };
        sslEnabled = resolveSsl(parsed);
        // Neon/prod exige TLS (cert autofirmado -> sin verificacion, como antes);
        // el Postgres local de compose corre con ssl=off, asi que va plaintext.
        if (sslEnabled) poolConfig.ssl = { rejectUnauthorized: false };
        pool = new Pool(poolConfig);
    } catch (e) {
        console.error('[db] Error parseando URL:', e.message);
        pool = { query: async () => { throw new Error('DB no configurada: ' + e.message); }, end: async () => {}, connect: async () => { throw new Error('DB no configurada'); } };
    }
}

function getConnectionString() { return rawUrl; }
function isSslEnabled() { return sslEnabled; }
module.exports = { pool, getConnectionString, isSslEnabled };
