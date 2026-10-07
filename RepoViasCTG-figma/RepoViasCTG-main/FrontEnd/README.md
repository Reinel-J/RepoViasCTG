# ViaCTG Frontend

Cliente web de ViaCTG para consultar y registrar daños viales de Cartagena.
Está construido con React, TypeScript y Vite, y consume el backend Spring Boot
ubicado en `../ViasCTG`.

## Tecnologías

- React 19 y TypeScript.
- Vite para desarrollo y compilación.
- Axios para consumir la API REST.
- React Router para la navegación.
- Leaflet y React Leaflet para mostrar y seleccionar ubicaciones.
- OpenStreetMap como capa de mapa para la demo; no requiere una API key.

## Requisitos

- Node.js y npm.
- El backend de ViaCTG iniciado en `http://localhost:8080`.
- MongoDB disponible para el backend.

## Inicio local

Primero levanta el backend desde su directorio:

```bash
cd ../ViasCTG
export MONGODB_URI='mongodb://localhost:27017/viasctg_db'
export JWT_SECRET="$(openssl rand -base64 32)"
./mvnw spring-boot:run
```

Después inicia el cliente:

```bash
npm install
npm run dev
```

Vite muestra la URL local, normalmente `http://localhost:5173`. Para generar la
versión de producción:

```bash
npm run build
```

## Integración con la API

El cliente Axios está configurado en `src/api/client.ts` con:

```text
http://localhost:8080/api
```

Si existe un token en `localStorage` bajo la clave `token`, lo envía como
`Authorization: Bearer <token>`. La sesión completa se conserva bajo
`authSession` e incluye identificador, nombre y rol del usuario.

Los módulos API disponibles están en `src/api/`:

- `auth.ts`: login y registro.
- `reportes.ts`: listado, detalle, creación, actualización y búsqueda cercana.
- `barrios.ts` y `categorias.ts`: datos para el formulario de reporte.
- `comentarios.ts` y `confirmaciones.ts`: interacciones por reporte.
- `admin.ts`: reportes pendientes y transición de estado.

## Mapa y geolocalización

`MapaSeleccionPunto` permite escoger las coordenadas pulsando el mapa o usando la
ubicación del navegador. `MapaReportes` dibuja los reportes recibidos por la API
como marcadores con estado y prioridad.

Los mapas usan tiles de OpenStreetMap y conservan la atribución visible
`© OpenStreetMap contributors`. Esta opción es apropiada para la demo; para un
despliegue con tráfico relevante se debe contratar un proveedor de tiles o alojar
los propios.

El backend ofrece la búsqueda geográfica:

```text
GET /api/reportes/cercanos?lat=10.391&lng=-75.479&radioKm=2&estado=PENDIENTE
```

`estado` es opcional y el radio se expresa en kilómetros.

## Pantallas implementadas

- Inicio de sesión y registro.
- Lista de reportes con filtro por estado.
- Formulario de creación con ubicación geográfica.
- Detalle con mapa, comentarios y confirmaciones para usuarios autenticados.
- Mapa general de reportes.
- Panel de moderación para revisar reportes pendientes.

Las pantallas y componentes están preparados en `src/pages` y `src/components`.
La integración final de las rutas en `App.tsx` queda pendiente; mientras no se
complete, la aplicación muestra la pantalla inicial de Vite.

## Roles

- `CIUDADANO`: puede crear reportes, comentar y confirmar con sesión iniciada.
- `MODERADOR`: puede revisar reportes pendientes y cambiar su estado.
- `ADMIN`: conserva los permisos de moderación y las funciones administrativas
  del backend.

## Estructura

```text
src/
├── api/         # Cliente Axios, tipos y módulos REST
├── components/  # Mapas y corrección de iconos Leaflet
├── context/     # Sesión y autenticación
├── pages/       # Pantallas de la aplicación
└── routes/      # Protección por sesión y rol
```
