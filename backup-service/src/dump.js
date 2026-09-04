const { exec } = require('child_process');
const fs = require('fs');
const path = require('path');
const { upload } = require('./s3');
const { pool, getConnectionString } = require('./db');

let currentChildProcess = null;

async function runBackup(tipo, usuarioId = null) {
    let registroId = null;
    let tempFilePath = null;
    try {
        // 1. Insert into registros_respaldo
        const insertRes = await pool.query(
            `INSERT INTO registros_respaldo (tipo, estado, ejecutado_por, iniciado_en) 
             VALUES ($1, 'ejecutando', $2, now()) RETURNING id`,
            [tipo, usuarioId]
        );
        registroId = insertRes.rows[0].id;

        // 2. Generate dump
        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        const filename = `backup_completo_${timestamp}.dump`;
        tempFilePath = path.join(__dirname, '..', filename);

        // Env config is already set in db.js (PGUSER, PGPASSWORD)
        const dbUrl = getConnectionString();
        const cmd = `pg_dump --format=c --file="${tempFilePath}" "${dbUrl}"`;
        
        await new Promise((resolve, reject) => {
            const child = exec(cmd, (error, stdout, stderr) => {
                currentChildProcess = null;
                if (error) {
                    console.error(`Error de pg_dump: ${stderr}`);
                    reject(error);
                } else {
                    resolve();
                }
            });
            currentChildProcess = child;
        });

        // 3. Read file and upload
        const dataBuffer = fs.readFileSync(tempFilePath);
        const sizeBytes = dataBuffer.length;
        
        const rutaR2 = await upload(filename, dataBuffer);

        // 4. Update registro on success
        await pool.query(
            `UPDATE registros_respaldo 
             SET estado = 'exitoso', nombre_archivo = $1, tamano_archivo_bytes = $2, 
                 ruta_r2 = $3, finalizado_en = now() 
             WHERE id = $4`,
            [filename, sizeBytes, rutaR2, registroId]
        );

        console.log(`Backup ${tipo} exitoso: ${filename}`);

    } catch (err) {
        console.error(`Error en backup ${tipo}:`, err);
        // Update registro on failure
        if (registroId) {
            await pool.query(
                `UPDATE registros_respaldo 
                 SET estado = 'fallido', mensaje_error = $1, finalizado_en = now() 
                 WHERE id = $2`,
                [err.message.substring(0, 500), registroId]
            );
        }
    } finally {
        if (tempFilePath && fs.existsSync(tempFilePath)) {
            fs.unlinkSync(tempFilePath);
        }
    }
}

function abortCurrentDump() {
    if (currentChildProcess) {
        try { currentChildProcess.kill('SIGTERM'); } catch (_) {}
        currentChildProcess = null;
    }
}

module.exports = {
    runBackup,
    abortCurrentDump
};
