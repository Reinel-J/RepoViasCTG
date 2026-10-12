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
- `reportes.ts`: listado, detalle, creación, actualización, subida de fotos, búsqueda cercana y posibles duplicados.
- `reporteVista.ts`: `cargarReportes` / `cargarReporte` sobre `reportes.ts`, que convierten el DTO en el
  `Reporte` que pintan la portada y las pantallas públicas (texto y clase del estado, fecha relativa,
  historial) y `fotoReporte`, que devuelve la URL de la primera foto o `null`.
- `seguimiento.ts`: votos «¿ya lo arreglaron?» de un reporte.
- `geocoding.ts`: previsualización de la dirección de un punto (OpenStreetMap/Nominatim).
- `barrios.ts` y `categorias.ts`: catálogos disponibles en la API; el formulario de reporte ya no
  los usa (la categoría es texto libre).
- `comentarios.ts` y `confirmaciones.ts`: interacciones por reporte.
- `admin.ts`: reportes pendientes, transición de estado, historial, votos de seguimiento, estadísticas y gestión de usuarios.
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

Las rutas se declaran en `src/app/routes.tsx`. La portada está en `src/app/pages/Home.tsx` y el resto de
pantallas son exports de `src/app/pages/Screens.tsx`, que el router carga bajo demanda por nombre:

| Ruta | Pantalla | Acceso |
|------|----------|--------|
| `/` | Inicio con estadísticas y reportes recientes, siempre calculados con datos reales de la API | Público |
| `/login`, `/registro` | Inicio de sesión y registro | Público |
| `/recuperar`, `/restablecer?token=` | Solicitar enlace y crear contraseña nueva | Público |
| `/reportes` | Lista con búsqueda y filtro por estado (incluye rechazados) | Público |
| `/reportes/:id` | Detalle con foto, mapa, dirección, comentarios, confirmaciones y línea de tiempo con cada cambio de estado | Público (comentar y confirmar requieren sesión) |
| `/mapa` | Mapa general de reportes con la lista completa en la barra lateral | Público |
| `/reportes/nuevo` | Formulario en 3 pasos: ubicación en el mapa, categoría libre con descripción y hasta 3 fotos, y revisión | Sesión iniciada |
| `/reportes/:id/editar` | Editar descripción y ubicación de un reporte propio `PENDIENTE` | Sesión iniciada |
| `/notificaciones` | Notificaciones del usuario, con marcado como leída | Sesión iniciada |
| `/perfil` | Editar nombre y teléfono, cambiar contraseña y cerrar sesión en todos los dispositivos | Sesión iniciada |
| `/admin` | Panel de administración por pestañas (ver abajo) | `MODERADOR` o `ADMIN` |

Notas de comportamiento:

- Al crear un reporte, las fotos se suben en una segunda petición (`POST /api/reportes/{id}/fotos`). El
  formulario valida antes el tipo, el peso (5 MB) y el máximo de 3, y permite quitar cada foto. Si el reporte
  se crea pero la subida falla, se abre igual el detalle con el aviso «El reporte se creó, pero no se pudieron
  subir las fotos», para que reenviar el formulario no lo duplique.
- Un reporte sin fotos muestra un placeholder «Sin fotografía»; nunca una imagen de stock.
- La línea de tiempo del detalle empieza en «Reporte recibido» y lista cada entrada de `historialEstados`
  (estado nuevo, comentario del moderador y fecha), de la más antigua a la más reciente.
- `/mapa` distingue cargando, error y cargado; con la base vacía muestra el mapa centrado en Cartagena y
  el mensaje «Aún no hay reportes en el mapa».
- Al marcar el punto, el formulario consulta `GET /api/reportes/posibles-duplicados` y, si hay reportes
  activos a menos de 100 m, muestra «¿Ya lo reportaron?» (primero los de la misma categoría, comparada sin
  mayúsculas ni tildes) con la opción «Es el mismo: confirmar», que confirma el existente y abre su detalle.
- El detalle muestra la tarjeta «¿Ya lo arreglaron?» con los votos de la comunidad; con sesión se responde
  «Ya está arreglado» o «Sigue igual»; la tarjeta se oculta en reportes `RESUELTO` o `RECHAZADO`.
- El backend resuelve la dirección de forma asíncrona, así que un reporte recién creado puede
  llegar con `direccionOsm` en `null`; mientras tanto se muestran las coordenadas.
- Al cambiar la contraseña el backend devuelve un token nuevo y `renovarSesion` lo guarda, así que la sesión actual sigue abierta y las demás se cierran.
- Los comentarios muestran el nombre real del autor (`usuarioNombre`, que entrega el backend).
- La lista filtra en el cliente sobre la respuesta completa de `GET /api/reportes` y no pagina;
  conviene pasar a paginación del lado del servidor si crece el volumen.
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
| Reportes | Moderador y admin | Todos los reportes con filtro por estado, búsqueda y paginación; mover al siguiente estado (`PENDIENTE → EN_REVISION → EN_PROCESO → RESUELTO`, o `RECHAZADO`) fijando prioridad y comentario; ver el historial; etiqueta «Comunidad: ya arreglado / sigue igual» según los votos |
| Estadísticas | Moderador y admin | Días promedio hasta la primera respuesta y hasta resolver, % resuelto, columnas de creados y resueltos por mes (con tooltip y vista de tabla), categorías con más reportes y mapa de densidad de daños activos |
| Usuarios | Admin | Buscar y filtrar por rol, cambiar el rol y activar/desactivar cuentas (no sobre uno mismo) |
| Barrios | Admin | Crear, editar y eliminar barrios; agregar y quitar calles |
| Categorías | Admin | Crear, editar, activar y desactivar (no se eliminan) |

Las transiciones permitidas están en `src/pages/admin/estados.ts` y deben coincidir con
`ReporteService.validarTransicion` del backend. Las acciones destructivas piden confirmación.

## Pendiente

- Paginación del lado del servidor.
- Estadística «Barrios participando» (muestra `0`: el reporte no tiene relación con un barrio).
- Contador de notificaciones sin leer en la barra superior.
- El icono de los marcadores Leaflet se ve roto («Mark»): `leafletIconFix.ts` debe borrar
  `L.Icon.Default.prototype._getIconUrl` antes de `mergeOptions`.
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
├── api/            # Cliente Axios, tipos, módulos REST y reporteVista.ts (normalización para la vista)
├── app/
│   ├── routes.tsx  # Rutas (carga diferida por nombre de export)
│   ├── pages/      # Home.tsx (portada) y Screens.tsx (todas las demás pantallas y su PortalShell)
│   └── components/ # UI.tsx: iconos y etiquetas que usa el panel de administración
├── components/     # Mapas y corrección de iconos Leaflet
├── context/        # Sesión: AuthContext (proveedor), useAuth (hook) y authContextValue
├── pages/admin/    # Panel de administración (pestañas)
├── routes/         # Protección por sesión y rol
└── index.css       # Único archivo de estilos
```

`src/App.tsx` solo reexporta `src/app/App.tsx`, que monta el router.

## Verificación

```bash
npm run lint
npm run build
```
