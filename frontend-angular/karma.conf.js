// Karma configuration file, see link for more information
// https://karma-runner.github.io/6.4/config/configuration-file.html
//
// D.1/R1: `make all` debe correr en cualquier máquina desde una
// clonación limpia, no solo en las que ya tengan Chrome instalado en
// C:\Program Files\Google\Chrome\Application\chrome.exe (el default que
// busca karma-chrome-launcher si CHROME_BIN no está seteado). Se
// encontró esto real en una máquina Windows sin Chrome: el "test"
// builder de Angular (@angular-devkit/build-angular:karma) usa su
// configuración interna por defecto (que no fija CHROME_BIN) a menos
// que se le pase un karma.conf.js propio via la opción `karmaConfig`
// (ver angular.json, projects.frontend-angular.architect.test.options) --
// de ahí que este archivo exista: es la única manera soportada de
// intervenir antes de que karma-chrome-launcher resuelva el binario.

const {existsSync} = require('node:fs');
const path = require('node:path');

/**
 * Orden de resolución de CHROME_BIN:
 *   1) Si ya está seteado en el entorno, se respeta tal cual -- alguien
 *      ya lo resolvió a mano para su máquina, no hay que pisarlo.
 *   2) Un Chrome/Edge/Chromium ya instalado en el sistema (rutas
 *      típicas por SO + PATH). Cubre la gran mayoría de máquinas
 *      reales: casi cualquier laptop de desarrollo, runner de CI o
 *      máquina de laboratorio universitario tiene alguno de estos.
 *   3) Como último recurso, el Chromium que instala 'puppeteer'
 *      (devDependency) -- si llegó a descargarse (ver
 *      .puppeteerrc.cjs: por defecto npm install/ci NO intenta esa
 *      descarga, justo por el incidente de abajo).
 *
 * Por qué este orden y no ir directo a (3): se confirmó en un ambiente
 * con lista blanca de dominios que la descarga de Puppeteer
 * (storage.googleapis.com) puede fallar con 403 en redes restringidas
 * (universidad, corporativas) -- justo el tipo de red que no se puede
 * probar de antemano contra la sede real de la sustentación. Depender
 * solo de esa descarga es un punto único de falla de red; probar
 * primero un navegador que la máquina ya tiene instalado evita esa
 * dependencia por completo en el caso común. Ver
 * docs/despliegue/DEPLOYMENT.md, sección "Navegador para los tests
 * (Karma/Puppeteer)" para el detalle del incidente.
 */
function resolveChromeBin() {
  if (process.env.CHROME_BIN) {
    return process.env.CHROME_BIN;
  }

  const delSistema = buscarNavegadorDelSistema();
  if (delSistema) {
    return delSistema;
  }

  // executablePath() no descarga nada: solo calcula la ruta donde
  // puppeteer pondría (o ya puso) su Chromium. Si nunca se descargó,
  // esa ruta simplemente no existe en disco, y karma-chrome-launcher
  // va a fallar con un error claro de "binario no encontrado" al
  // arrancar el navegador -- no un cuelgue silencioso ni un error
  // distinto al que ya se documenta en el Makefile.
  return require('puppeteer').executablePath();
}

function buscarNavegadorDelSistema() {
  for (const ruta of rutasTipicasPorSO()) {
    if (existsSync(ruta)) {
      return ruta;
    }
  }

  // Cualquier directorio del PATH -- cubre instalaciones en Linux/macOS
  // donde google-chrome-stable/chromium sí suelen quedar en el PATH
  // (a diferencia de Windows, donde el instalador de Chrome/Edge
  // normalmente NO se agrega al PATH, de ahí la lista de rutas típicas
  // de arriba).
  const nombresBuscados = process.platform === 'win32'
    ? ['chrome.exe', 'msedge.exe']
    : ['google-chrome-stable', 'google-chrome', 'chromium-browser', 'chromium',
       'microsoft-edge-stable', 'microsoft-edge'];
  const directoriosPath = (process.env.PATH || '').split(path.delimiter).filter(Boolean);
  for (const dir of directoriosPath) {
    for (const nombre of nombresBuscados) {
      const candidato = path.join(dir, nombre);
      if (existsSync(candidato)) {
        return candidato;
      }
    }
  }

  return null;
}

function rutasTipicasPorSO() {
  switch (process.platform) {
    case 'win32': {
      const bases = [
        process.env['PROGRAMFILES'],
        process.env['PROGRAMFILES(X86)'],
        process.env['LOCALAPPDATA']
      ].filter(Boolean);
      const relativos = [
        ['Google', 'Chrome', 'Application', 'chrome.exe'],
        ['Microsoft', 'Edge', 'Application', 'msedge.exe']
      ];
      return bases.flatMap((base) => relativos.map((partes) => path.join(base, ...partes)));
    }
    case 'darwin':
      return [
        '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
        '/Applications/Chromium.app/Contents/MacOS/Chromium',
        '/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge'
      ];
    default: // linux y otros unix
      return [
        '/usr/bin/google-chrome-stable',
        '/usr/bin/google-chrome',
        '/usr/bin/chromium-browser',
        '/usr/bin/chromium',
        '/usr/bin/microsoft-edge-stable',
        '/usr/bin/microsoft-edge',
        '/snap/bin/chromium'
      ];
  }
}

process.env.CHROME_BIN = resolveChromeBin();

// El resto de este archivo replica exactamente la configuración interna
// que genera @angular-devkit/build-angular cuando NO se especifica un
// karmaConfig propio (ver getBuiltInKarmaConfig() en
// node_modules/@angular-devkit/build-angular/src/builders/karma/index.js
// y el template en
// node_modules/@schematics/angular/config/files/karma.conf.js.template) --
// al declarar `karmaConfig` en angular.json, el builder deja de aplicar
// esa configuración por defecto, así que hay que reproducirla acá para
// no perder cobertura/reporters/frameworks ya en uso.
module.exports = function karmaConfig(config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma')
    ],
    client: {
      jasmine: {},
      clearContext: false
    },
    jasmineHtmlReporter: {
      suppressAll: true
    },
    coverageReporter: {
      dir: path.join(__dirname, './coverage/frontend-angular'),
      subdir: '.',
      reporters: [
        { type: 'html' },
        { type: 'text-summary' }
      ]
    },
    reporters: ['progress', 'kjhtml'],
    // ChromeHeadless (no 'Chrome'): es el modo que ya usan Makefile y
    // ci.yml (--browsers=ChromeHeadless), que sobreescribe este default
    // de todas formas -- se deja acá consistente por si alguien corre
    // `ng test` sin ese flag.
    browsers: ['ChromeHeadless'],
    restartOnFileChange: true
  });
};
