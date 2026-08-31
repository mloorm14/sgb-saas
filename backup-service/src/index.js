require('dotenv').config();
const express = require('express');
const cron = require('node-cron');
const { runBackup } = require('./dump');
const { pool } = require('./db');
const cors = require('cors');

const app = express();
app.use(express.json());
app.use(cors());

// Endpoint para disparar un backup manual (llamado por el proxy de Spring Boot)
app.post('/api/v1/trigger', async (req, res) => {
    // Esto corre asíncrono para no bloquear la respuesta HTTP
    // El frontend hará polling o simplemente leerá la tabla de registros
    const { usuarioId } = req.body;
    
    // Respondemos inmediatamente que se ha encolado
    res.status(202).json({ message: 'Backup completo iniciado en background' });
    
    // Ejecutamos el backup
    await runBackup('manual', usuarioId);
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
        
        if (now >= proxima) {
            console.log('Ejecutando backup automático...');
            // Ejecutamos el backup
            await runBackup('automatico', config.actualizado_por);
            
            // Calculamos próxima ejecución
            const nuevaProxima = new Date(now.getTime() + config.frecuencia_horas * 60 * 60 * 1000);
            await pool.query(
                `UPDATE configuracion_respaldo SET ultima_ejecucion = $1, proxima_ejecucion = $2 WHERE id = $3`,
                [now, nuevaProxima, config.id]
            );
        }
    } catch (err) {
        console.error('Error en cron automático:', err);
    }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
    console.log(`Backup Service iniciado en puerto ${PORT}`);
    console.log(`Cron job configurado (revisión cada hora)`);
});
