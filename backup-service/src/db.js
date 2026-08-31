const { Pool } = require('pg');

// Tomar la URL de conexión — prioridad: DATABASE_URL, luego DB_URL (quita 'jdbc:' de Java)
let connectionString = process.env.DATABASE_URL;
if (!connectionString && process.env.DB_URL) {
    connectionString = process.env.DB_URL.replace(/^jdbc:/, '');
}

if (!connectionString) {
    throw new Error('No se encontró una variable de base de datos (DATABASE_URL o DB_URL). Configúrala en Render.');
}

// Si la URL de Neon no trae sslmode, lo añadimos nosotros
if (!connectionString.includes('sslmode') && connectionString.includes('neon.tech')) {
    connectionString += (connectionString.includes('?') ? '&' : '?') + 'sslmode=require';
}

// Pool simple — dejamos que pg parsee todo desde la URL, sin configuración extra que entre en conflicto
const pool = new Pool({ connectionString });

// Exponer la URL limpia para que pg_dump también pueda conectarse
function getConnectionString() {
    return connectionString;
}

module.exports = { pool, getConnectionString };

