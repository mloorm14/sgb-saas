const { Pool } = require('pg');

// Tomar la URL de conexión
let rawUrl = process.env.DATABASE_URL;
if (!rawUrl && process.env.DB_URL) {
    rawUrl = process.env.DB_URL.replace(/^jdbc:/, '');
}
if (!rawUrl) {
    throw new Error('No se encontró DATABASE_URL ni DB_URL. Configúrala en Render.');
}

// Parsear la URL manualmente para extraer cada componente por separado.
// Esto evita el error SCRAM cuando el password tiene caracteres especiales (@, #, !, %)
// que no están URL-encodeados en la cadena de conexión.
let poolConfig;
try {
    const parsed = new URL(rawUrl);
    poolConfig = {
        host:     parsed.hostname,
        port:     parseInt(parsed.port) || 5432,
        database: parsed.pathname.replace(/^\//, ''),
        user:     decodeURIComponent(parsed.username),
        password: decodeURIComponent(parsed.password),
        ssl:      { rejectUnauthorized: false }
    };
} catch (e) {
    // Si falla el parseo, intentar con connectionString directo
    console.warn('No se pudo parsear la URL, usando connectionString directo:', e.message);
    poolConfig = { connectionString: rawUrl, ssl: { rejectUnauthorized: false } };
}

const pool = new Pool(poolConfig);

function getConnectionString() {
    return rawUrl;
}

module.exports = { pool, getConnectionString };
