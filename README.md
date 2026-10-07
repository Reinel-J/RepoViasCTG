# ViaCTG

ViaCTG es una plataforma para reportar y hacer seguimiento a daños viales (calles sin pavimentar o dañadas) en Cartagena, pensada para conductores y mototaxistas. Se basa en el modelo de [FixMyStreet](https://www.fixmystreet.com/): cualquier ciudadano reporta un daño marcándolo en un mapa, otros usuarios confirman que sigue vigente, y un administrador/moderador le da seguimiento hasta resolverlo.

Este repositorio es un **monorepo**: contiene el backend y el frontend como proyectos independientes que se ejecutan y despliegan por separado.

```
ViasCTG./
├── ViasCTG/      → Backend (API REST)
└── FrontEnd/     → Frontend (aplicación web)
```

## Backend — `ViasCTG/`

API REST en **Java 21 + Spring Boot 3 + Spring Data MongoDB + Spring Security + JWT**.

- Autenticación y autorización por rol (`CIUDADANO`, `MODERADOR`, `ADMIN`).
- CRUD de reportes, barrios, calles, categorías, confirmaciones y comentarios.
- Cambio de estado de reportes con historial embebido (`PENDIENTE → EN_REVISION → EN_PROCESO → RESUELTO / RECHAZADO`).
- Geolocalización: cada reporte guarda latitud/longitud y un índice geoespacial (`2dsphere`) para consultar reportes cercanos a un punto.
- Documentación interactiva vía Swagger UI (`/swagger-ui/index.html`).

Instrucciones de instalación, variables de entorno y rutas disponibles: ver [`ViasCTG/README.md`](./ViasCTG/README.md).

Arranque rápido:

```bash
cd ViasCTG
export MONGODB_URI='mongodb://localhost:27017/viasctg_db'
export JWT_SECRET="$(openssl rand -base64 32)"
./mvnw spring-boot:run
```

Por defecto corre en `http://localhost:8080`.

## Frontend — `FrontEnd/`

Aplicación web en **React + TypeScript + Vite**, consume la API del backend.

- Mapa interactivo con **Leaflet + OpenStreetMap** (sin API key) para marcar y visualizar reportes.
- Autenticación con JWT guardado en el navegador y rutas protegidas por rol.
- Páginas para crear/listar/consultar reportes, panel de administración y perfil de usuario.

Arranque rápido:

```bash
cd FrontEnd
npm install
npm run dev
```

Por defecto corre en `http://localhost:5173` y espera que el backend esté disponible en `http://localhost:8080/api`.

## Requisitos generales

- Java 21 (JDK)
- Node.js 18+ y npm
- MongoDB accesible (local o remoto)
- Ambos servicios deben correr al mismo tiempo para que el frontend pueda consumir la API

## Estado del proyecto

El avance detallado y las tareas pendientes de cada parte se documentan en su propio `PROGRESS.md`:

- Backend: [`ViasCTG/PROGRESS.md`](./ViasCTG/PROGRESS.md)
- Frontend: (por crear a medida que avance)

## Docker

Los tres servicios (MongoDB, backend y frontend) se orquestan con Docker Compose.

```bash
# 1. Crea el archivo .env a partir del ejemplo
cp .env.example .env

# 2. Genera un secreto real y pégalo en .env
openssl rand -base64 32
# Edita .env → JWT_SECRET=<el-valor-generado>

# 3. Levanta todo
docker compose up --build
```

| Servicio | URL |
|----------|-----|
| Backend (Swagger) | http://localhost:8080/swagger-ui/index.html |
| Frontend | http://localhost:5173 |
| MongoDB | localhost:27017 |

Para detener: `docker compose down` (agrega `-v` para borrar los datos de Mongo).

## Autor

Reinel José Villanueva Ayola — Tecnología en Desarrollo de Software, Tecnológico Comfenalco (Cartagena).
