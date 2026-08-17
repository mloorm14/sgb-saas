/**
 * @type {import('puppeteer').Configuration}
 */
//
// skipDownload: true -- por defecto, 'npm install'/'npm ci' NO intenta
// descargar el Chromium de Puppeteer. Esto es intencional, no un
// descuido: se confirmó un incidente real donde esa descarga (contra
// storage.googleapis.com) devolvió 403 en una red con lista blanca de
// dominios, y Puppeteer no la maneja como una falla no bloqueante --
// termina el proceso con código de salida distinto de cero, lo que
// hace fallar 'npm ci' completo (no solo los tests: bloquea build,
// todo). Mecanismo documentado en el propio código de instalación de
// Puppeteer (ver node_modules/puppeteer/src/node/install.ts, función
// getConfiguration() vía cosmiconfig, y el mensaje de error real:
// 'Set "PUPPETEER_SKIP_DOWNLOAD" env variable to skip download.').
//
// Esto es seguro porque frontend-angular/karma.conf.js ya no depende
// solo de este binario: primero intenta CHROME_BIN si ya está seteado,
// después un Chrome/Edge/Chromium instalado en el sistema, y recién
// como último recurso la ruta de Puppeteer -- que en el caso normal
// (skipDownload activo) simplemente no va a existir, y el navegador
// del sistema cubre el caso real. Ver docs/despliegue/DEPLOYMENT.md,
// sección "Navegador para los tests (Karma/Puppeteer)".
//
// Quien quiera igual tener el Chromium propio de Puppeteer disponible
// (por ejemplo, en una máquina sin ningún navegador instalado) puede
// pedirlo a mano en cualquier momento con:
//   npx puppeteer browsers install chrome
// o correr 'npm ci' con PUPPETEER_SKIP_DOWNLOAD=false para permitir la
// descarga automática en esa corrida puntual.
module.exports = {
  skipDownload: true,
};
