# ViaCTG

ViaCTG es una API REST para registrar y hacer seguimiento a daños viales de Cartagena. Usa Java 21, Spring Boot 3.3.4, Spring Data MongoDB, Spring Security y JWT.

## Requisitos y configuración

- JDK 21 o superior.
- MongoDB accesible desde la aplicación.
- Un secreto JWT en Base64 de 32 bytes o más.

```bash
export MONGODB_URI='mongodb://localhost:27017/viasctg_db'
export JWT_SECRET="$(openssl rand -base64 32)"
./mvnw spring-boot:run
```

Variables opcionales: `ADMIN_EMAIL` y `ADMIN_PASSWORD` (al arrancar crean ese usuario como `ADMIN`, o lo promueven si ya existe sin cambiar su contraseña), `CORS_ALLOWED_ORIGINS` (orígenes permitidos separados por coma; por defecto `http://localhost:5173,http://localhost:3000`) , `UPLOADS_DIR` (carpeta de fotos; por defecto `uploads` relativa al directorio de ejecución), `SEGUIMIENTO_DIAS` (días sin cambios antes de preguntar «¿ya lo arreglaron?»; por defecto `30`) y `SEGUIMIENTO_CRON` (hora de esa tarea, zona `America/Bogota`; por defecto `0 0 9 * * *`).

No se guardan credenciales en el repositorio. Mongo crea al iniciar los índices únicos de `usuarios.email`, `confirmaciones(reporteId, usuarioId)` y `seguimientos(reporteId, usuarioId)`.

## Arquitectura

El código se organiza bajo `com.viactg` por responsabilidad: `controller`, `service`, `repository`, `model`, `dto`, `mapper`, `security`, `exception` y `config`. Los controladores solo usan DTOs; los servicios concentran reglas de negocio y validan referencias MongoDB.

`Calle` está embebida en `Barrio`, y `HistorialEstado` en `Reporte`. Las demás entidades tienen su propia colección.

El cliente React vive en el directorio hermano `../FrontEnd`. Consume esta API en desarrollo desde `http://localhost:5173` y usa `http://localhost:8080/api` como URL base.

## Geolocalización de reportes

Cada reporte guarda `latitud`, `longitud` y una ubicación GeoJSON en el campo `ubicacion`. Este último tiene un índice geoespacial MongoDB `2dsphere`, creado automáticamente al iniciar la aplicación, y almacena las coordenadas en el orden requerido por GeoJSON: `[longitud, latitud]`.

Al crear o actualizar un reporte, las coordenadas son obligatorias y se validan antes de llegar al servicio:

- Latitud: entre `-90` y `90`.
- Longitud: entre `-180` y `180`.

Ejemplo de cuerpo para crear o actualizar un reporte:

```json
{
  "categoriaId": "Hueco",
  "descripcion": "Hueco de gran tamaño cerca de la intersección.",
  "latitud": 10.391,
  "longitud": -75.479
}
```

`categoriaId` es texto libre. La prioridad no la envía el ciudadano: todo reporte inicia en `MEDIA` y solo `ADMIN` o `MODERADOR` la modifican al cambiar el estado (`prioridad` opcional en `PATCH /api/admin/reportes/{id}/estado`). La edición (`PUT`) solo aplica a reportes `PENDIENTE` y solo recibe descripción y coordenadas.

### Dirección (OpenStreetMap)

El backend resuelve `direccionOsm` con Nominatim (geocodificación inversa). Se hace de forma asíncrona: la respuesta de `POST /api/reportes` trae `direccionOsm: null` y el valor se completa segundos después. Si cambian las coordenadas en un `PUT`, se recalcula. `GET /api/geocoding/inverso?lat=..&lng=..` es público y sirve para previsualizar la dirección.

### Comentarios y notificaciones

`ComentarioResponse` incluye `usuarioNombre` (nombre del autor, `null` si el usuario ya no existe). Cada vez que un `ADMIN` o `MODERADOR` cambia el estado de un reporte, se crea una notificación para su autor (por ejemplo: «Tu reporte "…" cambió a en revisión»), visible en `GET /api/notificaciones`.

### Fotos

`POST /api/reportes/{id}/fotos` (`multipart/form-data`, clave `archivos`) acepta hasta 3 imágenes JPEG, PNG o WEBP de máximo 5 MB cada una. Solo el propietario puede subirlas. Se guardan en `UPLOADS_DIR` y se sirven desde `/uploads/**`; las rutas quedan en el arreglo `fotos` del reporte.

La consulta pública de reportes cercanos usa este índice geoespacial:

```text
GET /api/reportes/cercanos?lat=10.391&lng=-75.479&radioKm=2&estado=PENDIENTE
```

`radioKm` debe ser mayor que cero; `estado` es opcional. La respuesta es una lista de
`ReporteResponse` ordenada por cercanía. El frontend la puede consumir para mostrar
marcadores en un mapa Leaflet/OpenStreetMap.

## Funciones inspiradas en FixMyStreet

### Posibles duplicados

`GET /api/reportes/posibles-duplicados?lat=..&lng=..` (público) devuelve hasta 5 reportes **activos**
(`PENDIENTE`, `EN_REVISION`, `EN_PROCESO`) a menos de 100 m, del más cercano al más lejano, como
`{reporte, distanciaMetros, confirmaciones}`. El formulario de creación lo usa para ofrecer «Es el mismo:
confirmar» en vez de crear un reporte repetido (`DuplicadoService`).

### «¿Ya lo arreglaron?»

- `POST /api/reportes/{id}/seguimiento` con `{"arreglado": true|false}` registra o cambia el voto del usuario
  (uno por usuario y reporte, colección `seguimientos`). Solo para reportes activos (si no, `409`).
- `GET /api/reportes/{id}/seguimiento` (público) devuelve `{reporteId, arreglado, sigue, ultimaRespuesta, miRespuesta}`.
- Los votos **no cambian el estado**: son una señal para el moderador, que los ve en
  `GET /api/admin/reportes/seguimiento` (una agregación `$group` por reporte).
- Una tarea diaria (`SeguimientoService.tareaDiaria`) busca reportes activos sin cambios ni recordatorios en
  `SEGUIMIENTO_DIAS` y avisa al autor y a quienes lo confirmaron, con notificación y correo. Cada reporte se
  marca en `fechaUltimoSeguimiento` antes de avisar, así que dos ejecuciones no envían el mismo aviso.
  `POST /api/admin/reportes/seguimiento/recordatorios` (`ADMIN`) la ejecuta en el momento.

### Estadísticas

`GET /api/admin/estadisticas` (`ADMIN` o `MODERADOR`) calcula en una sola agregación `$facet` sobre `reportes`:
totales por estado, porcentaje resuelto (sin contar rechazados), días promedio hasta la primera respuesta y
hasta resolver (a partir de `historialEstados`), creados y resueltos por mes en los últimos 12 meses (hora de
Colombia), las 8 categorías con más reportes (sin distinguir mayúsculas ni espacios) y la densidad de reportes
activos en celdas de ~110 m (coordenadas redondeadas a 3 decimales).

## Seguridad

| Acción | Acceso |
| --- | --- |
| Registro y login | Público |
| Consultar reportes, posibles duplicados y votos de seguimiento, barrios y categorías; geocodificación inversa; `/uploads/**` | Público |
| Crear/editar reportes, comentar, confirmar, votar «¿ya lo arreglaron?», consultar comentarios/confirmaciones y datos propios | Usuario autenticado |
| Cambiar estado e historial de reportes; votos de seguimiento de todos los reportes; estadísticas | `ADMIN` o `MODERADOR` |
| Ejecutar los recordatorios de seguimiento en el momento | `ADMIN` |
| Administrar barrios, calles y categorías; listar usuarios, cambiar su rol y activarlos/desactivarlos | `ADMIN` |

El registro público crea un usuario `CIUDADANO`. El primer `ADMIN` se crea con `ADMIN_EMAIL`/`ADMIN_PASSWORD`. Un administrador no puede cambiar su propio rol ni desactivarse. Los permisos se leen de la base en cada petición: un cambio de rol o una desactivación aplica de inmediato, incluso sobre tokens ya emitidos (un usuario desactivado recibe 401). Usa el JWT recibido en el encabezado `Authorization: Bearer <token>`.

## Protección de cuentas

- `security/LimitadorIntentos` cuenta intentos por clave en una ventana deslizante en memoria (sirve para una sola instancia). `AuthService` lo aplica al login (20 por IP y 5 fallos por correo / 15 min), registro (5 por IP / hora), recuperación (5 por IP y 3 por correo / hora) y cambio de contraseña (5 fallos / 15 min). Excederlo responde `429`.
- Detrás de un proxy, `server.forward-headers-strategy=native` toma la IP real de `X-Forwarded-For` solo si viene de un proxy interno.
- El JWT incluye el claim `ver` con `Usuario.versionToken`; el filtro rechaza tokens con una versión distinta. Cambiar o restablecer la contraseña y `cerrar-sesiones` incrementan la versión.
- La recuperación genera 32 bytes aleatorios, guarda solo su SHA-256 en `tokens_recuperacion` (índice TTL) y envía `FRONTEND_URL/restablecer?token=…`. Sin SMTP configurado (`SPRING_MAIL_HOST`), el enlace se registra en el log.

## Errores

Toda respuesta de error tiene el mismo cuerpo, con el mensaje en español y pensado para mostrarse tal cual:

```json
{"timestamp": "…", "status": 400, "error": "Bad Request",
 "message": "La solicitud contiene campos inválidos",
 "validationErrors": {"descripcion": "La descripción debe tener entre 10 y 2000 caracteres"}}
```

| Código | Cuándo |
|--------|--------|
| 400 | Campos inválidos (detalle por campo en `validationErrors`), JSON ilegible, parámetro faltante o con valor no válido |
| 401 | Sin token, token vencido o revocado, o credenciales incorrectas en el login |
| 403 | Sesión válida sin permiso para la operación |
| 404 | El recurso o la dirección no existe |
| 405 / 415 | Método o tipo de contenido no admitido |
| 409 | Regla de negocio (transición no permitida, máximo de fotos, duplicados…) |
| 413 | Archivo de más de 5 MB |
| 429 | Demasiados intentos de login, registro o recuperación |
| 500 | Error no controlado (el detalle solo va al log) |

Cada restricción de los DTO declara su `message`; al agregar una nueva hay que escribirlo, porque el texto por
defecto de Bean Validation sale en inglés.

## Rutas principales

| Recurso | Rutas |
| --- | --- |
| Autenticación | `POST /api/auth/registro`, `POST /api/auth/login`, `POST /api/auth/recuperar` (`{"email"}`, siempre `202`), `POST /api/auth/restablecer` (`{"token","passwordNueva"}`) |
| Perfil y usuarios | `GET/PUT /api/usuarios/me`, `PUT /api/usuarios/me/password` (`{"passwordActual","passwordNueva"}`, devuelve un token nuevo), `POST /api/usuarios/me/cerrar-sesiones`; `GET /api/usuarios`, `PATCH /api/usuarios/{id}/rol` (`{"rol":"MODERADOR"}`), `PATCH /api/usuarios/{id}/activo?activo=false` (`ADMIN`) |
| Reportes | `GET/POST /api/reportes`, `GET /api/reportes/cercanos`, `GET /api/reportes/posibles-duplicados`, `GET/PUT /api/reportes/{id}`, `POST /api/reportes/{id}/fotos` |
| Seguimiento | `GET/POST /api/reportes/{reporteId}/seguimiento` |
| Geocodificación | `GET /api/geocoding/inverso` |
| Administración de reportes | `GET /api/admin/reportes/pendientes`, `PATCH /api/admin/reportes/{id}/estado`, `GET /api/admin/reportes/{id}/historial`, `GET /api/admin/reportes/seguimiento`, `POST /api/admin/reportes/seguimiento/recordatorios` (`ADMIN`) |
| Estadísticas | `GET /api/admin/estadisticas` |
| Barrios y calles | `GET/POST /api/barrios`, `GET/PUT/DELETE /api/barrios/{id}`, `POST /api/barrios/{id}/calles`, `DELETE /api/barrios/{id}/calles/{calleId}` |
| Categorías | `GET/POST /api/categorias`, `GET/PUT /api/categorias/{id}`, `PATCH /api/categorias/{id}/estado` |
| Confirmaciones | `GET/POST /api/reportes/{reporteId}/confirmaciones`, `DELETE /api/reportes/{reporteId}/confirmaciones/me` |
| Comentarios | `GET/POST /api/reportes/{reporteId}/comentarios`, `PUT/DELETE /api/comentarios/{id}` |
| Notificaciones | `GET /api/notificaciones`, `PATCH /api/notificaciones/{id}/leida` |

La documentación interactiva está en `http://localhost:8080/swagger-ui/index.html`.

## Verificación

```bash
./mvnw test
./mvnw clean verify
```

## Desarrollo con frontend

En una terminal, inicia la API con MongoDB local y un secreto JWT temporal:

```bash
export MONGODB_URI='mongodb://localhost:27017/viasctg_db'
export JWT_SECRET="$(openssl rand -base64 32)"
./mvnw spring-boot:run
```

En otra terminal:

```bash
cd ../FrontEnd
npm install
npm run dev
```

La API queda disponible en `http://localhost:8080`, Swagger en
`http://localhost:8080/swagger-ui/index.html` y Vite normalmente en
`http://localhost:5173`.

Consulta [HELP.md](HELP.md) para solucionar problemas comunes y [PROGRESS.md](PROGRESS.md) para conocer el trabajo pendiente.
