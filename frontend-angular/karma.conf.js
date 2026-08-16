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
//
// Puppeteer ya está en package.json (devDependency) y descarga su propio
// Chromium al hacer `npm install`/`npm ci` -- fijar CHROME_BIN a esa
// ruta ANTES de que Karma arranque hace que ChromeHeadless use siempre
// ese binario, sin depender de que la máquina tenga Chrome/Edge
// instalado en ninguna ruta particular.
process.env.CHROME_BIN = require('puppeteer').executablePath();

// El resto de este archivo replica exactamente la configuración interna
// que genera @angular-devkit/build-angular cuando NO se especifica un
// karmaConfig propio (ver getBuiltInKarmaConfig() en
// node_modules/@angular-devkit/build-angular/src/builders/karma/index.js
// y el template en
// node_modules/@schematics/angular/config/files/karma.conf.js.template) --
// al declarar `karmaConfig` en angular.json, el builder deja de aplicar
// esa configuración por defecto, así que hay que reproducirla acá para
// no perder cobertura/reporters/frameworks ya en uso.
module.exports = function (config) {
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
      dir: require('path').join(__dirname, './coverage/frontend-angular'),
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
