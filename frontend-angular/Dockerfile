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
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
