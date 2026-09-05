require('dotenv').config();
const express = require('express');
const cron = require('node-cron');
const { runBackup } = require('./dump');
const { pool } = require('./db');
const cors = require('cors');

const app = express();
app.use(express.json());
app.use(cors());

// Cerrojo en memoria: evita que un backup manual y el cron automático
// corran a la vez (el chequeo en BD tiene una ventana de carrera mientras
// se inserta el registro). Node es single-thread: el flag basta.
let backupEnCurso = false;

app.get('/health', async (req, res) => {
    if (!process.env.DATABASE_URL && !process.env.DB_URL) return res.status(503).json({ status: 'DOWN', db: 'no DATABASE_URL' });
    try { await pool.query('SELECT 1'); res.json({ status: 'UP' }); } catch (e) { res.status(503).json({ status: 'DOWN', error: e.message }); }
});

// Middleware de seguridad para validar la API Key interna
app.use('/api/v1/trigger', (req, res, next) => {
    const expectedKey = process.env.INTERNAL_API_KEY;
    if (expectedKey && expectedKey.trim() !== '') {
        const providedKey = req.headers['x-internal-api-key'];
        if (providedKey !== expectedKey) {
            return res.status(401).json({ error: 'No autorizado: API Key interna inválida o faltante' });
        }
    }
    next();
});

// Endpoint para disparar un backup manual (llamado por el proxy de Spring Boot)
app.post('/api/v1/trigger', async (req, res) => {
    const { usuarioId } = req.body;
    if (backupEnCurso) return res.status(429).json({ mensaje: 'Ya hay un respaldo en ejecucion', retryAfter: 60 });
    
    // Adquirir el cerrojo sincrónicamente antes de ceder el control al event loop
    backupEnCurso = true;

    res.status(202).json({ message: 'Backup completo iniciado en background' });
    try {
        await runBackup('manual', usuarioId);
    } finally {
        backupEnCurso = false;
    }
});

// Cron job para leer configuracion_respaldo
// Se ejecuta cada hora al minuto 0
cron.schedule('0 * * * *', async () => {
    console.log('Revisando si hay backups programados pendientes...');
    try {
        const res = await pool.query(`SELECT * FROM configuracion_respaldo ORDER BY id DESC LIMIT 1`);
        if (res.rows.length === 0) return;
        
        const config = res.rows[0];
        if (!config.habilitado) return;
        
        const now = new Date();
        const proxima = new Date(config.proxima_ejecucion);
        
        if (now >= proxima && !backupEnCurso) {
            console.log('Ejecutando backup automático...');
            backupEnCurso = true;
            try {
                // Ejecutamos el backup
                await runBackup('automatico', config.actualizado_por);

                // Calculamos próxima ejecución
                const nuevaProxima = new Date(now.getTime() + config.frecuencia_horas * 60 * 60 * 1000);
                await pool.query(
                    `UPDATE configuracion_respaldo SET ultima_ejecucion = $1, proxima_ejecucion = $2 WHERE id = $3`,
                    [now, nuevaProxima, config.id]
                );
            } finally {
                backupEnCurso = false;
            }
        }
    } catch (err) {
        console.error('Error en cron automático:', err);
    }
});

const PORT = process.env.PORT || 3000;
const server = app.listen(PORT, () => {
    console.log(`Backup Service iniciado en puerto ${PORT}`);
    console.log(`Cron job configurado (revisión cada hora)`);
});

// Al arrancar, limpiar cualquier registro que quedó atascado en 'ejecutando'
// por un reinicio abrupto o un crash anterior del servicio (sin este barrido
// el guard del trigger responde 429 para siempre). El gracefulShutdown ya
// cubre SIGTERM; esto cubre kill -9 / caídas sin señal.
try {
    pool.query(
        `UPDATE registros_respaldo
         SET estado = 'fallido',
             mensaje_error = 'Servicio reiniciado durante ejecución',
             finalizado_en = now()
         WHERE estado = 'ejecutando'`
    ).then(
        res => { if (res.rowCount > 0) console.log(`Auto-limpieza: ${res.rowCount} registro(s) atascado(s) marcado(s) como fallido`); },
        err => console.warn('Aviso: no se pudieron limpiar registros atascados:', err.message)
    );
} catch (err) {
    console.warn('Aviso: pool no inicializado, se omite auto-limpieza:', err.message);
}

function gracefulShutdown(signal) {
    console.log(`Recibido ${signal}, cerrando...`);
    const { abortCurrentDump } = require('./dump');
    abortCurrentDump();
    server.close(async () => {
        try {
            await pool.query(
                `UPDATE registros_respaldo SET estado='fallido', mensaje_error='Cancelado por ${signal}', finalizado_en=now() WHERE estado='ejecutando'`
            );
        } catch (_) {}
        try { await pool.end(); } catch (_) {}
        process.exit(0);
    });
    setTimeout(() => process.exit(1), 10000).unref();
}

process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
process.on('SIGINT', () => gracefulShutdown('SIGINT'));
