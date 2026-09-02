const { Pool } = require('pg');

// Tomar la URL de conexión
let rawUrl = process.env.DATABASE_URL;
if (!rawUrl && process.env.DB_URL) {
    rawUrl = process.env.DB_URL.replace(/^jdbc:/, '');
}
if (!rawUrl) {
    throw new Error('No se encontró DATABASE_URL ni DB_URL. Configúrala en Render.');
}

// Limpiar espacios, saltos de línea y comillas que puedan rodear la URL
rawUrl = rawUrl.trim().replace(/^["']|["']$/g, '');

// DIAGNÓSTICO — muestra los primeros 30 caracteres y si detecta el host esperado
console.log('[db] URL detectada (primeros 30 chars):', rawUrl.substring(0, 30) + '...');
console.log('[db] Contiene neon.tech:', rawUrl.includes('neon.tech'));

let poolConfig;
try {
    const parsed = new URL(rawUrl);

    const host     = parsed.hostname;
    const port     = parseInt(parsed.port) || 5432;
    const database = parsed.pathname.replace(/^\//, '');
    const user     = decodeURIComponent(parsed.username);
    const password = decodeURIComponent(parsed.password);

    // DIAGNÓSTICO — muestra los campos parseados (sin exponer la contraseña)
    console.log('[db] host:', host);
    console.log('[db] port:', port);
    console.log('[db] database:', database);
    console.log('[db] user:', user);
    console.log('[db] password length:', password ? password.length : 'UNDEFINED/EMPTY');

    if (!password || typeof password !== 'string' || password.length === 0) {
        throw new Error('[db] La contraseña está vacía tras parsear la URL. Verifica DATABASE_URL en Render.');
    }

    poolConfig = {
        host,
        port,
        database,
        user,
        password,
        ssl: { rejectUnauthorized: false }
    };
} catch (e) {
    console.error('[db] Error parseando URL:', e.message);
    throw e;
}

const pool = new Pool(poolConfig);

function getConnectionString() {
    return rawUrl;
}

module.exports = { pool, getConnectionString };
