const { Pool } = require('pg');

let rawUrl = process.env.DATABASE_URL;
if (!rawUrl && process.env.DB_URL) {
    rawUrl = process.env.DB_URL.replace(/^jdbc:/, '');
}
let pool = null;
let poolConfig = null;

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
        poolConfig = { host, port, database, user, password, ssl: { rejectUnauthorized: false }, query_timeout: 15000 };
        pool = new Pool(poolConfig);
    } catch (e) {
        console.error('[db] Error parseando URL:', e.message);
        pool = { query: async () => { throw new Error('DB no configurada: ' + e.message); }, end: async () => {}, connect: async () => { throw new Error('DB no configurada'); } };
    }
}

function getConnectionString() { return rawUrl; }
module.exports = { pool, getConnectionString };
