# Stage 1: build Angular app
# TODO(entrega-final): pinar por digest sha256 antes de la entrega final.
# Ver docs/DIGESTS-PENDIENTES.md para el comando exacto de obtencion.
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Stage 2: serve with nginx
# TODO(entrega-final): pinar por digest sha256 antes de la entrega final.
# Ver docs/DIGESTS-PENDIENTES.md para el comando exacto de obtencion.
FROM nginx:1.25-alpine
COPY --from=build /app/dist/frontend-angular/browser/ /usr/share/nginx/html/
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
