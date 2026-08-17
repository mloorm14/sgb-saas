// Entorno de desarrollo: se reemplaza por environment.prod.ts en el build
// de producción vía fileReplacements (angular.json).
//
// Hallazgo kappa del reporte: la cookie de refresh con SameSite=Strict no
// cruza localhost <-> onrender.com. En desarrollo hay que correr el backend
// en local (http://localhost:8080) y apuntar aca, no al backend de Render.
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api',
};