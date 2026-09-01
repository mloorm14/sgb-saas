/** @type {import('tailwindcss').Config} */

// Modo oscuro (Bloque UI/UX): los tokens de color de M3 de abajo NO son hex
// fijos -- son funciones que leen variables CSS (--color-xxx: "R G B", sin
// comas) definidas en src/styles.scss bajo :root (valores claros) y .dark
// (valores oscuros, activados por la clase 'dark' en <html> que controla
// ThemeService). Esto da cobertura de modo oscuro automática a CUALQUIER
// componente que ya use estos tokens (bg-surface, text-on-surface, etc.)
// sin tener que agregar variantes dark: una por una en cada plantilla --
// solo los ~12 tokens "-fixed"/"-fixed-dim"/"on-*-fixed(-variant)" de abajo
// se dejan como hex fijo a proposito: en M3 esos roles son "fixed", es
// decir, deliberadamente IGUALES en ambos temas (ver comentario junto a
// ellos). Paleta oscura derivada de la clara con HSL (hue conservado,
// lightness invertida) y verificada con calculo de contraste WCAG real
// (ver commit de este cambio para el script de verificacion) -- no es la
// tonal palette exacta que generaria Material Theme Builder desde la
// semilla original (esa herramienta no estaba disponible en esta sesion),
// pero todas las combinaciones texto/fondo usadas en la app cumplen AA
// (>=4.5:1) o mejor.
function withOpacity(variableName) {
  return ({ opacityValue }) => {
    if (opacityValue !== undefined) {
      return `rgba(var(${variableName}), ${opacityValue})`;
    }
    return `rgb(var(${variableName}))`;
  };
}

module.exports = {
  content: [
    "./src/**/*.{html,ts}",
  ],
  darkMode: "class",
  theme: {
    extend: {
      screens: {
        tablet: '840px',
      },
      colors: {
        "secondary-container": withOpacity("--color-secondary-container"),
        "surface": withOpacity("--color-surface"),
        "surface-container-highest": withOpacity("--color-surface-container-highest"),
        "on-error": withOpacity("--color-on-error"),
        "error-container": withOpacity("--color-error-container"),
        "surface-container": withOpacity("--color-surface-container"),
        // "-fixed"/"-fixed-variant" (aquí y las 9 líneas similares de abajo):
        // roles M3 deliberadamente IGUALES en tema claro y oscuro (p.ej. el
        // resaltado del enlace de navegación activo) -- se dejan como hex
        // fijo a propósito, no leen variable CSS.
        "on-secondary-fixed": "#00201c",
        "on-secondary-container": withOpacity("--color-on-secondary-container"),
        "surface-bright": withOpacity("--color-surface-bright"),
        "tertiary": withOpacity("--color-tertiary"),
        "surface-variant": withOpacity("--color-surface-variant"),
        "outline-variant": withOpacity("--color-outline-variant"),
        "inverse-primary": withOpacity("--color-inverse-primary"),
        "primary-container": withOpacity("--color-primary-container"),
        "on-tertiary-fixed-variant": "#5b4300",
        "on-primary-fixed-variant": "#003ea8",
        "inverse-on-surface": withOpacity("--color-inverse-on-surface"),
        "surface-dim": withOpacity("--color-surface-dim"),
        "error": withOpacity("--color-error"),
        "on-tertiary-fixed": "#261a00",
        "primary-fixed-dim": "#b4c5ff",
        "on-tertiary": withOpacity("--color-on-tertiary"),
        "on-surface-variant": withOpacity("--color-on-surface-variant"),
        "secondary-fixed": "#79f7e3",
        "background": withOpacity("--color-background"),
        "on-background": withOpacity("--color-on-background"),
        "tertiary-fixed": "#ffdf9e",
        "on-surface": withOpacity("--color-on-surface"),
        "primary-fixed": "#dbe1ff",
        "surface-container-lowest": withOpacity("--color-surface-container-lowest"),
        "on-tertiary-container": withOpacity("--color-on-tertiary-container"),
        "surface-container-high": withOpacity("--color-surface-container-high"),
        "surface-container-low": withOpacity("--color-surface-container-low"),
        "tertiary-container": withOpacity("--color-tertiary-container"),
        "surface-tint": withOpacity("--color-surface-tint"),
        "primary": withOpacity("--color-primary"),
        "on-secondary": withOpacity("--color-on-secondary"),
        "on-primary-fixed": "#00174b",
        "outline": withOpacity("--color-outline"),
        "on-secondary-fixed-variant": "#005047",
        "secondary": withOpacity("--color-secondary"),
        "tertiary-fixed-dim": "#fabd00",
        "secondary-fixed-dim": "#59dbc7",
        "on-primary-container": withOpacity("--color-on-primary-container"),
        "on-error-container": withOpacity("--color-on-error-container"),
        "on-primary": withOpacity("--color-on-primary"),
        "inverse-surface": withOpacity("--color-inverse-surface"),
        // Tokens funcionales del mockup de "Mis préstamos" (semáforo de
        // días restantes: verde >3, amarillo 1-3, rojo vencido). Mismo
        // valor que en la paleta de los mockups de Rama B.
        "success": withOpacity("--color-success"),
        "warning": withOpacity("--color-warning")
      },
      borderRadius: {
        DEFAULT: "0.125rem",
        lg: "0.25rem",
        xl: "0.5rem",
        full: "0.75rem"
      },
      spacing: {
        gutter: "24px",
        xl: "40px",
        lg: "24px",
        md: "16px",
        "container-max": "1280px",
        sm: "8px",
        xs: "4px",
        "margin-mobile": "16px",
        base: "4px"
      },
      fontFamily: {
        "headline-lg": ["Plus Jakarta Sans"],
        "body-md": ["Inter"],
        "label-md": ["Inter"],
        "headline-lg-mobile": ["Plus Jakarta Sans"],
        "headline-xl": ["Plus Jakarta Sans"],
        "label-sm": ["Inter"],
        "body-lg": ["Inter"],
        "body-sm": ["Inter"],
        "headline-md": ["Plus Jakarta Sans"]
      },
      fontSize: {
        "headline-lg": ["32px", { lineHeight: "40px", letterSpacing: "-0.02em", fontWeight: "700" }],
        "body-md": ["16px", { lineHeight: "24px", fontWeight: "400" }],
        "label-md": ["14px", { lineHeight: "20px", letterSpacing: "0.05em", fontWeight: "600" }],
        "headline-lg-mobile": ["24px", { lineHeight: "32px", fontWeight: "700" }],
        "headline-xl": ["40px", { lineHeight: "48px", letterSpacing: "-0.02em", fontWeight: "700" }],
        "label-sm": ["12px", { lineHeight: "16px", fontWeight: "500" }],
        "body-lg": ["18px", { lineHeight: "28px", fontWeight: "400" }],
        "body-sm": ["14px", { lineHeight: "20px", fontWeight: "400" }],
        "headline-md": ["24px", { lineHeight: "32px", fontWeight: "600" }]
      }
    }
  },
  plugins: [
    require("@tailwindcss/forms"),
    require("@tailwindcss/container-queries"),
  ],
};