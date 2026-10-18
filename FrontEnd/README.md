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

`VITE_NOTIFICACIONES_INTERVALO_MS` (opcional, por defecto `60000`) fija cada cuánto la campana consulta
el número de notificaciones sin leer. Como `VITE_API_URL`, se fija al construir.

Los errores del backend llegan como `ErrorResponse {status, message, validationErrors}` y el cliente
los convierte en `error.message` (mensaje en español más el detalle de cada campo inválido), que las
pantallas muestran tal cual. Si la respuesta no trae `message`, se usa un texto por código HTTP; nunca
se muestra el genérico «Request failed with status code …».

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
- `notificaciones.ts`: listado, conteo de no leídas (`contarSinLeer`, para la campana) y marcado como leída.
- `usuarios.ts`: perfil propio y actualización de nombre y teléfono.

## Mapa y geolocalización

`MapaSeleccionPunto` permite escoger las coordenadas pulsando el mapa o usando la
ubicación del navegador. `MapaReportes` dibuja los reportes recibidos por la API
como círculos coloreados por estado (`COLOR_ESTADO` en `mapaBase.ts`) y encuadra
los reportes visibles al cargar o al cambiar un filtro. La ventanita de cada reporte
se abre al pasar el mouse (o con un toque en el celular) y muestra la primera foto o,
si no tiene, la ilustración de su categoría (`imagenCategoria`, SVG en
`public/img/categorias/`) rotulada como «Imagen ilustrativa», junto con la categoría,
el estado, la descripción, la dirección OSM y el enlace al detalle.

Los mapas usan tiles de OpenStreetMap y conservan la atribución visible
`© OpenStreetMap` con enlace a su página de derechos. Esta opción es apropiada para la demo; para un
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
| `/reportes` | Lista con búsqueda sin tildes y filtros por estado (incluye rechazados), categoría y barrio | Público |
| `/reportes/:id` | Detalle con foto, mapa, dirección, comentarios, confirmaciones y línea de tiempo con cada cambio de estado | Público (comentar y confirmar requieren sesión) |
| `/mapa` | Mapa general con filtros por estado (por defecto, daños activos), categoría y barrio, y la lista de lo visible en la barra lateral | Público |
| `/reportes/nuevo` | Formulario en 3 pasos: ubicación en el mapa, categoría libre con descripción y hasta 3 fotos, y revisión | Sesión iniciada |
| `/reportes/:id/editar` | Editar descripción y ubicación de un reporte propio `PENDIENTE` | Sesión iniciada |
| `/mis-reportes` | Reportes creados por el usuario (`GET /reportes/mios`), con pestañas por estado y enlace para editar los `PENDIENTE` | Sesión iniciada |
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
- El detalle solo depende de la petición del reporte: si fallan comentarios, confirmaciones o seguimiento,
  se muestra igual con un aviso de lo que no se pudo cargar.
- `/mapa` distingue cargando, error y cargado; si ningún reporte coincide con los filtros (o la base está
  vacía) muestra el mapa centrado en Cartagena y el aviso «No hay reportes con este filtro».
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
- La campana de la cabecera (`components/CampanaNotificaciones.tsx`, en la portada y en `PortalShell`) muestra
  las no leídas (tope «9+») con `GET /notificaciones/sin-leer`: consulta al montarse, al cambiar de página, al
  volver a la pestaña, al marcar notificaciones como leídas (evento de `components/notificacionesEventos.ts`) y
  cada `VITE_NOTIFICACIONES_INTERVALO_MS` solo con la pestaña visible; si el backend falla, la espera se duplica
  hasta 5 minutos. En pantallas de 980 px o menos la campana se oculta, como el resto de botones de icono.
- Con sesión iniciada, la cabecera muestra «Mis reportes» (en la portada, junto a «Cómo funciona»), y el menú
  lateral de la cuenta reúne Mi perfil, Mis reportes y Notificaciones.
  Al guardar el perfil se actualiza también el nombre mostrado en la barra superior.
- Cada pantalla se carga bajo demanda (`lazy` del router) y Leaflet solo se descarga al abrir un
  mapa (`components/MapasDiferidos.tsx`), así que la carga inicial de la portada pesa ~370 kB sin
  comprimir y ~120 kB con gzip. Antes Leaflet y su CSS se precargaban en todas las páginas por la regla
  de `manualChunks`.

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

## Rendimiento

- Los mapas (Leaflet y su CSS) se cargan con `import()` dinámico desde `src/components/MapasDiferidos.tsx`:
  la portada, el listado y el login no los descargan. No importes `MapaReportes`, `MapaSeleccionPunto` ni
  `MapaDensidad` directamente desde una pantalla; usa los de `MapasDiferidos`.
- La portada usa `GET /reportes/resumen` y `GET /reportes?limite=3` en lugar de descargar todos los reportes.
- `src/utils/imagenes.ts` reduce las fotos a 1600 px y las vuelve a codificar en JPEG antes de subirlas, lo
  que además elimina sus metadatos EXIF (ubicación GPS, modelo del teléfono).
- nginx comprime con gzip y cachea un año los assets con hash.

## Pendiente

- Paginación del lado del servidor (el listado y el mapa aún descargan todos los reportes).
- Pruebas de componentes y de extremo a extremo (hoy hay pruebas unitarias con Vitest).
- El token JWT se guarda en `localStorage`, expuesto a XSS; agregar una Content-Security-Policy en nginx y
  considerar cookies `HttpOnly`.
- En móvil, la tarjeta para confirmar un reporte queda al final, debajo de los comentarios.

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
npm test        # Vitest
npm run build
```
