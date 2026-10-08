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

## Cambios recientes

### Datos reales en el inicio

La sección **Reportes de la comunidad** ya no muestra tarjetas de demostración. Consume
`GET /api/reportes` y, mientras la consulta no tenga resultados o no pueda completarse,
presenta un estado vacío que invita a crear el primer reporte. Las estadísticas también se
calculan con esa respuesta:

- Reportes ciudadanos: cantidad de reportes recibidos.
- Problemas resueltos: reportes con estado `RESUELTO`.
- Barrios participando: `0` por ahora, hasta contar con una relación directa entre un reporte
  y su barrio.
- Reciben respuesta: porcentaje de reportes cuyo estado ya no es `PENDIENTE`.

### Creación de reportes y dirección geográfica

El formulario de creación ya no solicita una calle del catálogo. Al seleccionar un punto en
el mapa, el cliente consulta una previsualización de dirección y muestra la vía detectada.
Al guardar, el backend vuelve a resolver la dirección por sus propios medios y almacena el
resultado en el nuevo campo opcional `direccionOsm` del reporte; nunca toma la dirección del
cliente como fuente de verdad.

El servicio usa la geocodificación inversa pública de OpenStreetMap/Nominatim:

- `GET /api/geocoding/inverso?lat={latitud}&lng={longitud}` es público y devuelve
  `{ "direccionOsm": "..." }`, o `null` si no se puede resolver.
- Las consultas se identifican con un `User-Agent` propio, tienen un tiempo máximo de espera
  de cinco segundos y se limitan a una petición por segundo.
- Un error o tiempo de espera de Nominatim no bloquea la creación de un reporte; la dirección
  queda vacía.
- El constructor principal de `GeocodingService` está marcado con `@Autowired` para que Spring
  inyecte `ObjectMapper` de forma explícita, mientras el constructor alternativo queda para
  pruebas.

La categoría también pasó a ser texto libre. El formulario ofrece la guía “Ejemplo: Hueco,
alumbrado” y el backend conserva ese texto en `categoriaId` sin exigir que exista una categoría
en MongoDB.

### Prioridad y moderación

Los ciudadanos ya no pueden enviar ni modificar la prioridad. Todo reporte creado inicia con
prioridad `MEDIA`. Solo un `ADMIN` o `MODERADOR` puede definirla durante un cambio de estado,
en la misma solicitud administrativa:

```text
PATCH /api/admin/reportes/{id}/estado
```

El cuerpo acepta `prioridad` opcional (`BAJA`, `MEDIA` o `ALTA`) además de `nuevoEstado` y
`comentario`. El panel de moderación incluye el selector correspondiente antes de revisar o
rechazar un reporte.

### Contratos actualizados

- `POST /api/reportes` ya no recibe `calleId` ni `prioridad`; recibe categoría como texto,
  descripción y coordenadas.
- `PUT /api/reportes/{id}` tampoco modifica la prioridad.
- Las respuestas de reportes incluyen `direccionOsm`, que puede ser `null`.

### Fotos adjuntas

Los reportes permiten adjuntar hasta tres fotos reales en formato JPEG, PNG o WEBP. El
formulario muestra miniaturas antes de enviar y crea el reporte antes de transferir los archivos
mediante `POST /api/reportes/{id}/fotos` como `multipart/form-data` bajo la clave `archivos`.
Cada archivo puede pesar hasta 5 MB.

El backend valida la firma binaria y la extensión de cada imagen, verifica que quien la sube sea
el propietario del reporte y genera un nombre UUID para almacenarla fuera de los datos MongoDB.
Las rutas públicas generadas se exponen en el arreglo `fotos` de cada respuesta y se sirven desde
`/uploads/**`. El volumen `uploads_data` conserva esos archivos en Docker aunque se reconstruya
el contenedor del backend.

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
