FROM node:20-alpine

# Instalar postgresql-client para tener pg_dump
RUN apk add --no-cache postgresql-client

WORKDIR /app

COPY package*.json ./
RUN npm install --production

COPY src/ ./src/

ENV NODE_ENV=production

EXPOSE 3000

CMD ["npm", "start"]
