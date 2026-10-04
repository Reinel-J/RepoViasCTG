# --- Etapa 1: build ---
FROM node:22-alpine AS build
WORKDIR /app

# Argumento que se inyecta desde docker-compose.yml
ARG VITE_API_URL
ENV VITE_API_URL=${VITE_API_URL}

# Copiamos primero los archivos de dependencias para aprovechar el cache de capas.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# --- Etapa 2: servir con nginx ---
FROM nginx:stable-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
