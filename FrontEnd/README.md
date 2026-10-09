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

El cliente Axios está configurado en `src/api/client.ts`. La URL base sale de la variable
`VITE_API_URL` y, si no existe, es:

```text
http://localhost:8080/api
```

Si la API responde `401` con un token guardado (sesión vencida o inválida), el cliente limpia
la sesión y redirige a `/login`.

Si existe un token en `localStorage` bajo la clave `token`, lo envía como
`Authorization: Bearer <token>`. La sesión completa se conserva bajo
`authSession` e incluye identificador, nombre y rol del usuario.

Los módulos API disponibles están en `src/api/`:

- `auth.ts`: login y registro.
- `reportes.ts`: listado, detalle, creación, actualización, subida de fotos y búsqueda cercana.
- `geocoding.ts`: previsualización de la dirección de un punto (OpenStreetMap/Nominatim).
- `barrios.ts` y `categorias.ts`: catálogos disponibles en la API; el formulario de reporte ya no
  los usa (la categoría es texto libre).
- `comentarios.ts` y `confirmaciones.ts`: interacciones por reporte.
- `admin.ts`: reportes pendientes, transición de estado, historial y gestión de usuarios.
- `barrios.ts` y `categorias.ts` también incluyen las operaciones de administración.
- `notificaciones.ts`: listado y marcado como leída.
- `usuarios.ts`: perfil propio y actualización de nombre y teléfono.

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

Las rutas se declaran en `src/app/routes.tsx`:

| Ruta | Pantalla | Acceso |
|------|----------|--------|
| `/` | Inicio con estadísticas y reportes recientes | Público |
| `/login`, `/registro` | Inicio de sesión y registro | Público |
| `/reportes` | Lista con filtro por estado, dirección detectada y paginación (10 por página) | Público |
| `/reportes/:id` | Detalle con fotos, mapa, dirección, comentarios y confirmaciones | Público (comentar y confirmar requieren sesión) |
| `/mapa` | Mapa general de reportes | Público |
| `/reportes/nuevo` | Formulario: categoría libre, descripción, hasta 3 fotos y ubicación en el mapa | Sesión iniciada |
| `/notificaciones` | Notificaciones del usuario, con marcado como leída | Sesión iniciada |
| `/perfil` | Ver y editar nombre y teléfono | Sesión iniciada |
| `/admin` | Panel de administración por pestañas (ver abajo) | `MODERADOR` o `ADMIN` |

Notas de comportamiento:

- Al crear un reporte, las fotos se suben en una segunda petición (`POST /api/reportes/{id}/fotos`).
- El backend resuelve la dirección de forma asíncrona, así que un reporte recién creado puede
  llegar con `direccionOsm` en `null`. El detalle lo vuelve a consultar a los 4 segundos.
- Los comentarios muestran el nombre real del autor (`usuarioNombre`, que entrega el backend).
- La paginación de la lista se hace en el cliente sobre la respuesta completa de `GET /api/reportes`;
  mientras el backend no pagine, conviene pasar a paginación del lado del servidor si crece el volumen.
- Las notificaciones las genera el backend cuando un moderador cambia el estado de un reporte.
  Al guardar el perfil se actualiza también el nombre mostrado en la barra superior.
- Cada pantalla se carga bajo demanda (`lazy` del router) y Leaflet solo se descarga al abrir un
  mapa, así que el paquete inicial pesa ~377 kB (antes ~555 kB).

## Panel de administración

Vive en `src/pages/admin/`. La pestaña activa va en la URL (`/admin?seccion=reportes&estado=PENDIENTE`),
así que se puede enlazar directamente.

| Pestaña | Rol | Qué permite |
|---------|-----|-------------|
| Resumen | Moderador y admin | Totales, pendientes, activos con prioridad alta, % atendido, antigüedad del pendiente más viejo y distribución por estado y prioridad. Los indicadores enlazan a Reportes filtrado |
| Reportes | Moderador y admin | Todos los reportes con filtro por estado, búsqueda y paginación; mover al siguiente estado (`PENDIENTE → EN_REVISION → EN_PROCESO → RESUELTO`, o `RECHAZADO`) fijando prioridad y comentario; ver el historial |
| Usuarios | Admin | Buscar y filtrar por rol, cambiar el rol y activar/desactivar cuentas (no sobre uno mismo) |
| Barrios | Admin | Crear, editar y eliminar barrios; agregar y quitar calles |
| Categorías | Admin | Crear, editar, activar y desactivar (no se eliminan) |

Las transiciones permitidas están en `src/pages/admin/estados.ts` y deben coincidir con
`ReporteService.validarTransicion` del backend. Las acciones destructivas piden confirmación.

## Pendiente

- Editar un reporte propio mientras está `PENDIENTE`.
- Paginación del lado del servidor.
- Estadística «Barrios participando» (muestra `0`: el reporte no tiene relación con un barrio).
- Contador de notificaciones sin leer en la barra superior.
- Pruebas automatizadas.
- El token JWT se guarda en `localStorage`, expuesto a XSS; considerar cookies `HttpOnly`.

## Roles

- `CIUDADANO`: puede crear reportes, comentar y confirmar con sesión iniciada.
- `MODERADOR`: puede revisar reportes pendientes y cambiar su estado.
- `ADMIN`: conserva los permisos de moderación y las funciones administrativas
  del backend.

## Estructura

```text
src/
├── api/         # Cliente Axios, tipos y módulos REST
├── app/         # Router, layout, componentes de UI y página de inicio
├── components/  # Mapas y corrección de iconos Leaflet
├── context/     # Sesión: AuthContext (proveedor), useAuth (hook) y authContextValue
├── pages/       # Pantallas de la aplicación
└── routes/      # Protección por sesión y rol
```

`src/App.tsx` solo reexporta `src/app/App.tsx`, que monta el router.

## Verificación

```bash
npm run lint
npm run build
```
