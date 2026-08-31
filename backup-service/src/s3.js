const { S3Client, PutObjectCommand, DeleteObjectCommand } = require('@aws-sdk/client-s3');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const GCM_IV_LENGTH = 12;

// Load config from env
const storageUrl = process.env.BACKUP_STORAGE_URL || '';
const encryptionKey = process.env.BACKUP_ENCRYPTION_KEY || '';

let isR2 = storageUrl.startsWith('s3://');
let bucket = '';
if (isR2) {
    bucket = storageUrl.replace('s3://', '').split('/')[0];
    if (!bucket && process.env.R2_BUCKET_NAME) {
        bucket = process.env.R2_BUCKET_NAME;
    }
}
const s3Client = isR2 ? new S3Client({
    region: 'auto',
    endpoint: process.env.R2_ENDPOINT,
    credentials: {
        accessKeyId: process.env.R2_ACCESS_KEY_ID || '',
        secretAccessKey: process.env.R2_SECRET_ACCESS_KEY || ''
    },
    forcePathStyle: true
}) : null;

function isEncryptionEnabled() {
    return encryptionKey && encryptionKey.trim().length > 0;
}

function decodeKey() {
    const k = encryptionKey.trim();
    try {
        const d = Buffer.from(k, 'base64');
        if (d.length === 32) return d;
    } catch (e) {}
    
    const raw = Buffer.from(k, 'utf8');
    const out = Buffer.alloc(32);
    raw.copy(out, 0, 0, Math.min(raw.length, 32));
    return out;
}

function encrypt(dataBuffer) {
    const kb = decodeKey();
    const iv = crypto.randomBytes(GCM_IV_LENGTH);
    const cipher = crypto.createCipheriv('aes-256-gcm', kb, iv);
    
    let encrypted = cipher.update(dataBuffer);
    encrypted = Buffer.concat([encrypted, cipher.final()]);
    const tag = cipher.getAuthTag();
    
    // Spring Boot cipher.doFinal() appends the tag to the ciphertext automatically.
    // In Node.js, we must manually append it.
    // The format in Java is IV + Ciphertext + Tag.
    return Buffer.concat([iv, encrypted, tag]);
}

async function upload(key, dataBuffer) {
    const toStore = isEncryptionEnabled() ? encrypt(dataBuffer) : dataBuffer;
    
    if (isR2 && s3Client) {
        const command = new PutObjectCommand({
            Bucket: bucket,
            Key: key,
            Body: toStore,
            ContentLength: toStore.length
        });
        await s3Client.send(command);
        return `s3://${bucket}/${key}`;
    } else {
        let base = './backups';
        if (storageUrl && !isR2) {
            base = storageUrl;
        }
        if (!fs.existsSync(base)) {
            fs.mkdirSync(base, { recursive: true });
        }
        const safeKey = key.replace(/[\/\\]/g, '_');
        const filePath = path.join(base, safeKey);
        fs.writeFileSync(filePath, toStore);
        return filePath;
    }
}

async function deleteBackup(key) {
    if (isR2 && s3Client) {
        const command = new DeleteObjectCommand({
            Bucket: bucket,
            Key: key
        });
        await s3Client.send(command);
    } else {
        let base = './backups';
        if (storageUrl && !isR2) {
            base = storageUrl;
        }
        const safeKey = key.replace(/[\/\\]/g, '_');
        const filePath = path.join(base, safeKey);
        if (fs.existsSync(filePath)) {
            fs.unlinkSync(filePath);
        }
    }
}

module.exports = {
    upload,
    deleteBackup,
    isEncryptionEnabled
};
