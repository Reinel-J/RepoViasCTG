# ViaCTG

ViaCTG es una API REST para registrar y hacer seguimiento a daños viales de Cartagena. Usa Java 21, Spring Boot 3.3.13, Spring Data MongoDB, Spring Security y JWT.

## Requisitos y configuración

- JDK 21 o superior.
- MongoDB accesible desde la aplicación.
- Un secreto JWT en Base64 de 32 bytes o más.

```bash
export MONGODB_URI='mongodb://localhost:27017/viasctg_db'
export JWT_SECRET="$(openssl rand -base64 32)"
./mvnw spring-boot:run
```

Variables opcionales: `ADMIN_EMAIL` y `ADMIN_PASSWORD` (al arrancar crean ese usuario como `ADMIN`; si ya existe como ciudadano o moderador lo promueven, **le asignan `ADMIN_PASSWORD` y cierran sus sesiones**, porque otra persona pudo registrar antes ese correo; un administrador existente conserva su contraseña; esa cuenta queda con el correo verificado), `CORS_ALLOWED_ORIGINS` (orígenes permitidos separados por coma; por defecto `http://localhost:5173,http://localhost:3000`) , `UPLOADS_DIR` (carpeta de fotos; por defecto `uploads` relativa al directorio de ejecución), `SEGUIMIENTO_DIAS` (días sin cambios antes de preguntar «¿ya lo arreglaron?»; por defecto `30`) `SEGUIMIENTO_CRON` (hora de esa tarea, zona `America/Bogota`; por defecto `0 0 9 * * *`), `CIERRE_COMUNITARIO` (`false` por defecto; `true` permite que los votos cierren un reporte en proceso, ver «¿Ya lo arreglaron?») con `CIERRE_COMUNITARIO_MINIMO` (`3`), `CIERRE_COMUNITARIO_PROPORCION` (`0.75`) y `CIERRE_COMUNITARIO_GRACIA_HORAS` (`72`), `NOMINATIM_URL` y `NOMINATIM_USER_AGENT` (servidor e identificación para la geocodificación; la política de OpenStreetMap pide un User-Agent propio, idealmente con un correo de contacto; `NOMINATIM_URL` vacío la desactiva), `ZONA_HABILITADA`, `ZONA_NOMBRE`, `ZONA_LAT_MIN`, `ZONA_LAT_MAX`, `ZONA_LNG_MIN` y `ZONA_LNG_MAX` (área de servicio; por defecto Cartagena), `SEED_CATEGORIAS` (`true` por defecto: crea las 7 categorías del documento de visión si el catálogo está vacío) `SWAGGER_ENABLED` (`false` oculta Swagger UI y `/v3/api-docs`) y `TRUSTED_PROXIES` (IP del proxy inverso cuyo `X-Forwarded-For` se acepta, como expresión regular; vacía por defecto: no se confía en ninguno).

No se guardan credenciales en el repositorio. Mongo crea al iniciar los índices únicos de `usuarios.email`, `confirmaciones(reporteId, usuarioId)` y `seguimientos(reporteId, usuarioId)`, y los de consulta `reportes(estado, fechaCreacion)`, `reportes.fechaCreacion`, `reportes(usuarioId, fechaCreacion)`, `comentarios(reporteId, fecha)`, `notificaciones(usuarioId, fecha)` y `notificaciones(usuarioId, leida)`.

`GET /actuator/health` (público, sin detalles) indica si la API y MongoDB responden; lo usa el healthcheck de Docker.

## Arquitectura

El código se organiza bajo `com.viactg` por responsabilidad: `controller`, `service`, `repository`, `model`, `dto`, `mapper`, `security`, `exception` y `config`. Los controladores solo usan DTOs; los servicios concentran reglas de negocio y validan referencias MongoDB.

`Calle` está embebida en `Barrio`, y `HistorialEstado` en `Reporte`. Las demás entidades tienen su propia colección. El nombre de un barrio es único sin distinguir tildes, mayúsculas ni espacios repetidos (`nombreClave`, índice único); las calles se agregan y quitan con `$push`/`$pull` atómicos desde sus propios endpoints. `PUT /api/barrios/{id}` solo cambia nombre y localidad (si el cuerpo trae `calles`, responde `400`): antes las reemplazaba y, como el panel no las envía, editar un barrio borraba todas sus calles.

El cliente React vive en el directorio hermano `../FrontEnd`. Consume esta API en desarrollo desde `http://localhost:5173` y usa `http://localhost:8080/api` como URL base.

## Geolocalización de reportes

Cada reporte guarda `latitud`, `longitud` y una ubicación GeoJSON en el campo `ubicacion`. Este último tiene un índice geoespacial MongoDB `2dsphere`, creado automáticamente al iniciar la aplicación, y almacena las coordenadas en el orden requerido por GeoJSON: `[longitud, latitud]`.

Al crear o actualizar un reporte, las coordenadas son obligatorias y se validan antes de llegar al servicio:

- Latitud: entre `-90` y `90`.
- Longitud: entre `-180` y `180`.
- Además deben caer dentro del área de servicio (`AreaServicio`, por defecto un rectángulo que cubre Cartagena, sus corregimientos cercanos, Barú y las Islas del Rosario); fuera de ella la API responde `409`.

Ejemplo de cuerpo para crear o actualizar un reporte:

```json
{
  "categoriaId": "Hueco",
  "descripcion": "Hueco de gran tamaño cerca de la intersección.",
  "latitud": 10.391,
  "longitud": -75.479
}
```

`categoriaId` es texto libre (máximo 100 caracteres), pero si coincide con una categoría del catálogo sin distinguir mayúsculas, tildes ni espacios repetidos se guarda con el nombre oficial («inundacion » → «Inundación»), así filtros y estadísticas no se fragmentan. El catálogo no admite nombres duplicados. La prioridad no la envía el ciudadano: todo reporte inicia en `MEDIA` y solo `ADMIN` o `MODERADOR` la modifican al cambiar el estado (`prioridad` opcional en `PATCH /api/admin/reportes/{id}/estado`). La edición (`PUT`) solo aplica a reportes `PENDIENTE` y solo recibe descripción y coordenadas.

### Dirección (OpenStreetMap)

El backend resuelve `direccionOsm` («vía, barrio») y `barrioOsm` con Nominatim (geocodificación inversa). Se hace de forma asíncrona: la respuesta de `POST /api/reportes` trae ambos en `null` y se completan segundos después, solo si el reporte sigue en las mismas coordenadas. Si cambian las coordenadas en un `PUT`, se recalculan.

`GET /api/geocoding/inverso?lat=..&lng=..` es público y sirve para previsualizar la ubicación: devuelve `{direccionOsm, barrioOsm, dentroDelArea}`. Respeta la política de Nominatim: como máximo una petición por segundo para todo el servidor (con un turno reservado sin bloquear hilos: si la cola supera unos 2 s devuelve la dirección en `null` en lugar de esperar), caché de 5 000 puntos (celdas de ~11 m) y 30 consultas por minuto por IP.

### Comentarios y notificaciones

`ComentarioResponse` incluye `usuarioNombre` (nombre del autor, `null` si el usuario ya no existe). Cada vez que un `ADMIN` o `MODERADOR` cambia el estado de un reporte, se crea una notificación para su autor (por ejemplo: «Tu reporte "…" cambió a en revisión»), visible en `GET /api/notificaciones`. Cuando el reporte se resuelve o se rechaza, también se avisa a quienes lo confirmaron. `PATCH /api/notificaciones/leidas` marca todas como leídas y `GET /api/notificaciones/sin-leer` devuelve solo el conteo (`{"total": 3}`), pensado para que el frontend lo consulte cada minuto sin descargar la lista.

Solo el autor de un comentario puede editarlo; el autor o un moderador pueden eliminarlo. El autor de un reporte no puede confirmarlo y los reportes resueltos o rechazados no admiten confirmaciones.

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
- `GET /api/reportes/{id}/seguimiento` (público) devuelve `{reporteId, arreglado, sigue, ultimaRespuesta, miRespuesta,
  cierrePrevisto}`.
- Por defecto los votos **no cambian el estado**: son una señal para el moderador, que los ve en
  `GET /api/admin/reportes/seguimiento` (una agregación `$group` por reporte).
- **Cierre por la comunidad** (opcional, `CIERRE_COMUNITARIO=true`): un reporte `EN_PROCESO` pasa a ser candidato a
  cierre cuando, contando solo los votos emitidos desde que pasó a `EN_PROCESO`, hay al menos 3 votos «ya está
  arreglado» y son al menos el 75 % (el voto del autor cuenta doble; `CierreComunitario` es la regla, pura y probada caso
  por caso). Se avisa a los moderadores y `cierrePrevisto` indica cuándo se cerrará: si en 72 horas nadie vota «sigue
  igual» (lo que puede romper la regla) ni un moderador usa `POST /api/admin/reportes/{id}/mantener-abierto`, la tarea
  diaria lo pasa a `RESUELTO` con el comentario «Resuelto por la comunidad: …» y avisa como cualquier cierre.
  «Mantener abierto» descarta la propuesta y desde ese momento solo cuentan los votos nuevos. Umbrales configurables con
  `CIERRE_COMUNITARIO_MINIMO`, `CIERRE_COMUNITARIO_PROPORCION` y `CIERRE_COMUNITARIO_GRACIA_HORAS`.
- Una tarea diaria (`SeguimientoService.tareaDiaria`) busca reportes activos sin cambios ni recordatorios en
  `SEGUIMIENTO_DIAS` y avisa al autor y a quienes lo confirmaron, con notificación y correo. Cada reporte se
  marca en `fechaUltimoSeguimiento` antes de avisar, así que dos ejecuciones no envían el mismo aviso.
  La misma tarea aplica los cierres por la comunidad cuya espera venció. `POST /api/admin/reportes/seguimiento/recordatorios`
  (`ADMIN`) la ejecuta en el momento y devuelve `{reportesRecordados, reportesCerrados}`.

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
| Consultar reportes, su resumen, posibles duplicados y votos de seguimiento, barrios y categorías; geocodificación inversa; `/uploads/**`; `/actuator/health` | Público |
| Crear/editar reportes, comentar, confirmar, votar «¿ya lo arreglaron?», consultar comentarios/confirmaciones y datos propios | Usuario autenticado |
| Cambiar estado e historial de reportes; votos de seguimiento de todos los reportes; estadísticas | `ADMIN` o `MODERADOR` |
| Ejecutar los recordatorios de seguimiento en el momento | `ADMIN` |
| Administrar barrios, calles y categorías; listar usuarios, cambiar su rol y activarlos/desactivarlos | `ADMIN` |

Los roles se exigen por URL en `SecurityConfig` (antes de leer el cuerpo, así que sin permiso la respuesta es `403` y no errores de validación) y otra vez con `@PreAuthorize` en los controladores. El registro público crea un usuario `CIUDADANO` con el correo sin confirmar. El primer `ADMIN` se crea con `ADMIN_EMAIL`/`ADMIN_PASSWORD`. Un administrador no puede cambiar su propio rol ni desactivarse. Los permisos se leen de la base en cada petición: un cambio de rol o una desactivación aplica de inmediato, incluso sobre tokens ya emitidos (un usuario desactivado recibe 401). Usa el JWT recibido en el encabezado `Authorization: Bearer <token>`.

## Protección de cuentas

- `security/LimitadorIntentos` cuenta intentos por clave en una ventana deslizante en memoria (sirve para una sola instancia). Verificar y registrar es una sola operación atómica, así que enviar intentos en paralelo no permite superar el máximo; el login cuenta cada intento antes de comparar la contraseña y lo borra si es correcta. Se aplica al login (20 por IP, 5 fallos por correo desde una misma IP y un techo de 50 intentos por correo / 15 min: así nadie puede bloquear una cuenta ajena desde su propia red; restablecer la contraseña quita los bloqueos de ese correo), registro (5 por IP / hora), recuperación y reenvío de la confirmación (5 por IP y 3 por correo / hora cada uno), cambio de contraseña (5 fallos / 15 min), creación de reportes (10 por usuario / hora), comentarios (20 por usuario / 10 min) y geocodificación pública (30 por IP / minuto). Excederlo responde `429`.
- Usuarios y reportes se modifican con actualizaciones parciales atómicas (`$set`/`$inc`/`$push` con `findAndModify`), nunca reescribiendo el documento completo: un cambio de perfil no deshace una desactivación hecha a la vez, ni editar un reporte o subir fotos revierte un cambio de estado o su historial.
- BCrypt solo usa 72 bytes: contraseñas más largas (las letras con tilde cuentan doble) se rechazan con `400` y un mensaje claro.
- La IP de cada petición es la de la conexión: `X-Forwarded-For` se ignora salvo que la conexión venga de una IP que coincida con `TRUSTED_PROXIES` (expresión regular, por ejemplo `172\.18\.0\.5`; vacía por defecto). Antes Tomcat confiaba en cualquier red privada, y desde la red local se podían saltar todos los límites por IP. Si pones un proxy delante, fija ahí su IP y haz que solo él alcance el puerto del backend.
- El JWT incluye el claim `ver` con `Usuario.versionToken`; el filtro rechaza tokens con una versión distinta. Cambiar o restablecer la contraseña y `cerrar-sesiones` incrementan la versión.
- Verificación del correo: el registro no inicia sesión; crea la cuenta con `emailVerificado=false` y envía `FRONTEND_URL/verificar-correo?token=…` (válido 48 horas, `app.email-verification.expiration-hours`; un solo uso; solo el SHA-256 se guarda en `tokens_verificacion`, índice TTL). `POST /api/auth/verificar` confirma el correo y devuelve el JWT. Hasta entonces el login responde `403` con la contraseña correcta (con una incorrecta, el `401` de siempre, así que no revela nada). `POST /api/auth/reenviar-verificacion` responde siempre `202` (3 por correo y 5 por IP / hora) e invalida el enlace anterior. Restablecer la contraseña también confirma el correo: si alguien registró un correo ajeno, su dueña recupera la cuenta con «¿Olvidaste tu contraseña?» y la contraseña del intruso deja de valer. Al arrancar, `MigracionEmailVerificado` marca como verificadas las cuentas anteriores a esta función.
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
| 400 | Campos inválidos (detalle por campo en `validationErrors`), JSON ilegible, parámetro faltante o con valor no válido, contraseña de más de 72 bytes, URL mal formada (también las que Tomcat rechaza antes de llegar a Spring) |
| 401 | Sin token, token vencido o revocado, o credenciales incorrectas en el login |
| 403 | Sesión válida sin permiso para la operación (se decide antes de validar el cuerpo) |
| 404 | El recurso o la dirección no existe |
| 405 / 415 | Método o tipo de contenido no admitido |
| 409 | Regla de negocio (transición no permitida, máximo de fotos, duplicados…) |
| 413 | Archivo de más de 5 MB |
| 429 | Demasiados intentos (login, registro, recuperación, reportes, comentarios o geocodificación) |
| 500 | Error no controlado (el detalle solo va al log) |

Cada restricción de los DTO declara su `message`; al agregar una nueva hay que escribirlo, porque el texto por
defecto de Bean Validation sale en inglés.

## Rutas principales

| Recurso | Rutas |
| --- | --- |
| Autenticación | `POST /api/auth/registro` (devuelve el usuario, sin token), `POST /api/auth/verificar` (`{"token"}`, devuelve el JWT), `POST /api/auth/reenviar-verificacion` (`{"email"}`, siempre `202`), `POST /api/auth/login`, `POST /api/auth/recuperar` (`{"email"}`, siempre `202`), `POST /api/auth/restablecer` (`{"token","passwordNueva"}`) |
| Perfil y usuarios | `GET/PUT /api/usuarios/me`, `PUT /api/usuarios/me/password` (`{"passwordActual","passwordNueva"}`, devuelve un token nuevo), `POST /api/usuarios/me/cerrar-sesiones`; `GET /api/usuarios`, `PATCH /api/usuarios/{id}/rol` (`{"rol":"MODERADOR"}`), `PATCH /api/usuarios/{id}/activo?activo=false` (`ADMIN`) |
| Reportes | `GET/POST /api/reportes` (del más reciente al más antiguo; `?estado=` y `?limite=` opcionales), `GET /api/reportes/mios` (los del usuario autenticado, del más reciente al más antiguo; `?estado=` opcional; exige sesión aunque los demás GET de reportes sean públicos), `GET /api/reportes/resumen` (cifras de la portada), `GET /api/reportes/cercanos` (radio máximo 50 km), `GET /api/reportes/posibles-duplicados`, `GET/PUT /api/reportes/{id}`, `POST /api/reportes/{id}/fotos` |
| Seguimiento | `GET/POST /api/reportes/{reporteId}/seguimiento` |
| Geocodificación | `GET /api/geocoding/inverso` |
| Administración de reportes | `GET /api/admin/reportes/pendientes`, `PATCH /api/admin/reportes/{id}/estado`, `GET /api/admin/reportes/{id}/historial`, `GET /api/admin/reportes/seguimiento`, `POST /api/admin/reportes/seguimiento/recordatorios` (`ADMIN`) |
| Estadísticas | `GET /api/admin/estadisticas` |
| Barrios y calles | `GET/POST /api/barrios`, `GET/PUT/DELETE /api/barrios/{id}`, `POST /api/barrios/{id}/calles`, `DELETE /api/barrios/{id}/calles/{calleId}` |
| Categorías | `GET/POST /api/categorias`, `GET/PUT /api/categorias/{id}`, `PATCH /api/categorias/{id}/estado` |
| Confirmaciones | `GET/POST /api/reportes/{reporteId}/confirmaciones`, `DELETE /api/reportes/{reporteId}/confirmaciones/me` |
| Comentarios | `GET/POST /api/reportes/{reporteId}/comentarios`, `PUT/DELETE /api/comentarios/{id}` |
| Notificaciones | `GET /api/notificaciones`, `GET /api/notificaciones/sin-leer`, `PATCH /api/notificaciones/{id}/leida`, `PATCH /api/notificaciones/leidas` |
| Salud | `GET /actuator/health` |

La documentación interactiva está en `http://localhost:8080/swagger-ui/index.html`.

## Verificación

```bash
./mvnw test
./mvnw clean verify
```

Las pruebas no necesitan MongoDB: las de servicios usan Mockito y las de `controller/` levantan solo la capa web con
`@WebMvcTest` (seguridad, filtro JWT y manejo de errores reales; servicios simulados).

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
