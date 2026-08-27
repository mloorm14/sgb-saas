/** @type {import('tailwindcss').Config} */
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
        "secondary-container": "#76f4e0",
        "surface": "#f8f9ff",
        "surface-container-highest": "#d5e3fc",
        "on-error": "#ffffff",
        "error-container": "#ffdad6",
        "surface-container": "#e6eeff",
        "on-secondary-fixed": "#00201c",
        "on-secondary-container": "#006f63",
        "surface-bright": "#f8f9ff",
        "tertiary": "#503a00",
        "surface-variant": "#d5e3fc",
        "outline-variant": "#c4c6d5",
        "inverse-primary": "#b4c5ff",
        "primary-container": "#1e4db7",
        "on-tertiary-fixed-variant": "#5b4300",
        "on-primary-fixed-variant": "#003ea8",
        "inverse-on-surface": "#eaf1ff",
        "surface-dim": "#ccdbf3",
        "error": "#ba1a1a",
        "on-tertiary-fixed": "#261a00",
        "primary-fixed-dim": "#b4c5ff",
        "on-tertiary": "#ffffff",
        "on-surface-variant": "#434653",
        "secondary-fixed": "#79f7e3",
        "background": "#f8f9ff",
        "on-background": "#0d1c2e",
        "tertiary-fixed": "#ffdf9e",
        "on-surface": "#0d1c2e",
        "primary-fixed": "#dbe1ff",
        "surface-container-lowest": "#ffffff",
        "on-tertiary-container": "#fec004",
        "surface-container-high": "#dce9ff",
        "surface-container-low": "#eff4ff",
        "tertiary-container": "#6c5000",
        "surface-tint": "#2c57c1",
        "primary": "#003694",
        "on-secondary": "#ffffff",
        "on-primary-fixed": "#00174b",
        "outline": "#747684",
        "on-secondary-fixed-variant": "#005047",
        "secondary": "#006b5f",
        "tertiary-fixed-dim": "#fabd00",
        "secondary-fixed-dim": "#59dbc7",
        "on-primary-container": "#b8c8ff",
        "on-error-container": "#93000a",
        "on-primary": "#ffffff",
        "inverse-surface": "#233144",
        // Tokens funcionales del mockup de "Mis préstamos" (semáforo de
        // días restantes: verde >3, amarillo 1-3, rojo vencido). Mismo
        // valor que en la paleta de los mockups de Rama B.
        "success": "#1d9e75",
        "warning": "#fec004"
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