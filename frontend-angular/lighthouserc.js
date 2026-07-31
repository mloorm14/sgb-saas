// lighthouserc.js — Bloque C.5: Lighthouse CI real contra el frontend
// levantado en Docker (http://localhost:4200), perfil móvil con
// throttling de red Slow 4G simulado, tal como exige la guía.
module.exports = {
    ci: {
        collect: {
            url: ['http://localhost:4200/'],
            numberOfRuns: 1,
            settings: {
                // Perfil móvil + Slow 4G: throttling "simulate" con los
                // valores estándar de mobileSlow4G, dejado explícito aquí
                // en vez de confiar en el default implícito para que quede
                // documentado. Sin "preset: perf" para que se auditen las
                // 4 categorías (Performance, Accessibility, Best
                // Practices, SEO), no solo Performance.
                onlyCategories: ['performance', 'accessibility', 'best-practices', 'seo'],
                formFactor: 'mobile',
                throttlingMethod: 'simulate',
                throttling: {
                    rttMs: 150,
                    throughputKbps: 1638.4,
                    cpuSlowdownMultiplier: 4,
                    requestLatencyMs: 150 * 3.75,
                    downloadThroughputKbps: 1638.4 * 0.9,
                    uploadThroughputKbps: 675 * 0.9,
                },
                screenEmulation: {
                    mobile: true,
                    width: 360,
                    height: 640,
                    deviceScaleFactor: 2,
                    disabled: false,
                },
            },
        },
        upload: {
            target: 'filesystem',
            outputDir: '../docs/mediciones/lighthouse/.lhci-raw',
        },
    },
};
