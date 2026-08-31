const { Pool } = require('pg');

let connectionString = process.env.DATABASE_URL;
if (!connectionString && process.env.DB_URL) {
    connectionString = process.env.DB_URL.replace(/^jdbc:/, '');
}
if (!connectionString) {
    connectionString = 'postgresql://sgb_user:changeme@localhost:5432/sgb_db';
}

// Para desarrollo local sin SSL
const isProd = process.env.NODE_ENV === 'production' || connectionString.includes('neon.tech');

const poolConfig = {
    connectionString,
    ssl: isProd ? { rejectUnauthorized: false } : false
};

if (process.env.DB_USER) {
    poolConfig.user = process.env.DB_USER;
}
if (process.env.DB_PASSWORD) {
    poolConfig.password = process.env.DB_PASSWORD;
}

const pool = new Pool(poolConfig);

// Exponer las variables para que pg_dump las tome automáticamente
if (process.env.DB_USER && !process.env.PGUSER) {
    process.env.PGUSER = process.env.DB_USER;
}
if (process.env.DB_PASSWORD && !process.env.PGPASSWORD) {
    process.env.PGPASSWORD = process.env.DB_PASSWORD;
}

module.exports = {
    pool,
    connectionString
};
