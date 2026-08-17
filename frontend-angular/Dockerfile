# Stage 1: build Angular app
# Pinado por digest sha256 (ver docs/DIGESTS-LOG.md): node:20-alpine,
# obtenido con `docker pull` + `docker inspect` el 2026-07-22.
FROM node@sha256:fb4cd12c85ee03686f6af5362a0b0d56d50c58a04632e6c0fb8363f609372293 AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Stage 2: serve with nginx
# Pinado por digest sha256 (ver docs/DIGESTS-LOG.md): nginx:1.25-alpine,
# obtenido con `docker pull` + `docker inspect` el 2026-07-22.
FROM nginx@sha256:516475cc129da42866742567714ddc681e5eed7b9ee0b9e9c015e464b4221a00
COPY --from=build /app/dist/frontend-angular/browser/ /usr/share/nginx/html/
# Red de seguridad adicional (Bloque A.4.2): _redirects es el formato
# de fallback SPA que usan los Static Site de Render/Netlify. No hace
# falta si Render sirve este contenedor via Docker (nginx.conf ya trae
# `try_files $uri $uri/ /index.html;`), pero se copia igual por si el
# equipo mantiene el servicio como Static Site manual en vez de migrar
# a render.yaml -- ver docs/despliegue/DEPLOYMENT.md. Se copia aparte
# porque angular.json no tiene registrada la carpeta public/ en su
# arreglo "assets", así que `ng build` no la copia sola a dist/ todavia.
COPY public/_redirects /usr/share/nginx/html/_redirects
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
