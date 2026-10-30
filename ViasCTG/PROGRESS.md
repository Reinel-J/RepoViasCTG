# Progreso de ViaCTG

## Completado

- Migración de la capa de persistencia desde PostgreSQL/JPA a Spring Data MongoDB.
- Arquitectura por capas bajo `com.viactg`.
- Documentos MongoDB, enums, documentos embebidos e índices únicos requeridos.
- DTOs validados, mappers y manejo centralizado de errores.
- CRUD operativo para barrios, categorías, reportes, confirmaciones y comentarios, respetando las restricciones de borrado definidas.
- Autenticación JWT, BCrypt, roles `CIUDADANO`, `MODERADOR` y `ADMIN`, y protección por endpoint.
- Cambio de estado de reportes con historial embebido mediante una actualización atómica de MongoDB.
- Geoconsultas de reportes cercanos mediante GeoJSON, índice `2dsphere`, validación de coordenadas y endpoint público de mapa.
- Carga física de hasta 3 fotos por reporte (JPEG/PNG/WEBP, 5 MB) servidas desde `/uploads/**`.
- Geocodificación inversa con Nominatim, asíncrona y recalculada al editar coordenadas.
- CORS configurable por entorno (`CORS_ALLOWED_ORIGINS`).
- Notificación automática al autor cuando cambia el estado de su reporte, y nombre del autor en los comentarios.
- Gestión de usuarios para `ADMIN` (rol y activación), eliminación de calles y administrador inicial por `ADMIN_EMAIL`/`ADMIN_PASSWORD`; los tokens de cuentas desactivadas dejan de valer.
- Límite de intentos en login, registro y recuperación; recuperación de contraseña por correo con token de un solo uso; cambio de contraseña; revocación de sesiones por versión de token.
- OpenAPI/Swagger y documentación de configuración actualizada.
- Prueba unitaria de la transición de estado; `./mvnw test` pasa.
- Inspirado en FixMyStreet: detección de posibles duplicados (activos a ≤100 m) al crear un reporte.
- Inspirado en FixMyStreet: «¿ya lo arreglaron?» con votos por usuario (colección `seguimientos`) y recordatorio
  diario programado al autor y a quienes confirmaron; los votos son una señal para el moderador.
- Inspirado en FixMyStreet: estadísticas para moderadores en una agregación `$facet` (tiempos de respuesta y
  resolución, serie mensual, categorías y densidad de daños activos).
- Auditoría (octubre 2026):
  - Geocodificación sin bloquear hilos de Tomcat (antes una ráfaga a `/api/geocoding/inverso` dejaba la API sin
    hilos), con caché, límite por IP y User-Agent configurable; guarda también el barrio (`barrioOsm`).
  - Administrador inicial: al promover una cuenta existente se le asigna `ADMIN_PASSWORD` y se cierran sus sesiones.
  - Actualizaciones parciales atómicas en usuarios y reportes (adiós a las escrituras perdidas con `save()`), y la
    edición de un reporte ya no borra su dirección (se comparaban `Double` con `!=`).
  - Límite de intentos atómico; límites anti-spam para reportes y comentarios; contraseñas de más de 72 bytes.
  - Área de servicio (coordenadas dentro de Cartagena), categorías normalizadas al catálogo y catálogo inicial,
    sin auto-confirmaciones ni confirmaciones de casos cerrados, edición de comentarios solo por su autor y aviso a
    quienes confirmaron cuando el caso se cierra.
  - `GET /api/reportes` ordenado y con `limite`, `GET /api/reportes/resumen`, índices de consulta, Actuator,
    Docker sin root, Mongo solo en localhost y CI en GitHub Actions.
- «Mis reportes» (`GET /api/reportes/mios`, índice `reportes(usuarioId, fechaCreacion)`) y contador de
  notificaciones sin leer (`GET /api/notificaciones/sin-leer`, índice `notificaciones(usuarioId, leida)`) para la
  campana del frontend.
- Hallazgos de las pruebas de caja negra:
  - El login cuenta los fallos por correo + IP (5 / 15 min) con un techo de 50 intentos por correo: desde otra red ya
    no se puede bloquear una cuenta ajena. Restablecer la contraseña quita los bloqueos del correo.
  - `X-Forwarded-For` solo se acepta de los proxies de `TRUSTED_PROXIES` (vacío por defecto); antes, de cualquier red
    privada.
  - Barrios con nombre único (sin tildes ni mayúsculas, índice `nombreClave`) y modificaciones atómicas
    (`$set`/`$push`/`$pull`) en lugar de `save()`.
  - Los roles también se exigen por URL en `SecurityConfig`: sin permiso, 403 antes de validar el cuerpo (antes, 400
    con los errores del formulario).
  - Contraseña de más de 72 bytes: 400 (antes 409). URL mal formadas: `ErrorResponse` 400 en JSON, tanto las que
    rechaza el firewall de Spring Security (antes 401 «inicia sesión») como las que rechaza Tomcat (antes su
    página HTML).
  - `EditarReporte` avisa al cargar si el reporte no es de quien lo abre o ya no está pendiente.
- Cierre por la comunidad (opcional): un reporte `EN_PROCESO` se propone como resuelto con ≥ 3 votos «arreglado» y
  ≥ 75 % (el del autor vale doble, solo votos desde `EN_PROCESO`); 72 horas de gracia con aviso a los moderadores,
  «Mantener abierto» desde el panel y cierre en la tarea diaria.
- Revisión del frontend (26 de octubre de 2026): editar un barrio desde el panel borraba todas sus calles (el `PUT`
  las reemplazaba por la lista vacía). Ahora `PUT /api/barrios/{id}` solo cambia nombre y localidad. En el frontend:
  los mensajes del perfil y de «Editar reporte» salen junto a su botón (antes, arriba y fuera de la vista); el mapa ya
  no tapa el menú desplegable; la rueda del mouse solo hace zoom tras un clic en el mapa, y en móvil el mapa de
  `/mapa` mide el 60 % de la pantalla para que siempre quede por dónde desplazar la página.
- Verificación del correo al registrarse: enlace de un solo uso (48 h, solo su SHA-256 en `tokens_verificacion`),
  login bloqueado con 403 hasta confirmar, reenvío con límites, y restablecer la contraseña también confirma el correo
  (así se recupera una cuenta que otra persona registró con un correo ajeno). Las cuentas anteriores se marcan como
  verificadas al arrancar (`MigracionEmailVerificado`).
- Segunda tanda de la revisión del frontend (27 de octubre de 2026): las ventanitas del mapa se desplazan para verse
  enteras; el mapa del detalle se vuelve a centrar al cambiar de tamaño; diálogos propios en lugar de
  `window.confirm`/`window.prompt`; campana visible en móvil; textos de estados vacíos según el filtro; tarjetas de
  notificación ordenadas; plurales sin «(s)» (también en mensajes del backend); filtros de la lista en la URL con
  «Limpiar filtros»; la lupa de la portada enfoca el buscador; el detalle ya no muestra el id interno; `/login` y
  `/registro` redirigen con sesión; ilustraciones para Hundimiento, Derrumbe y Obstáculo (y «pare» ya no coincide
  con «reparar»); fotos de la portada y del login servidas desde el proyecto (antes, desde Unsplash); la tarea diaria solo en la pestaña Reportes; sin el aviso de
  `HydrateFallback`. Cierre por la comunidad: `progresoCierre` explica los votos ponderados en el detalle, el
  encabezado de notificaciones de moderadores lo menciona y el resultado de «Mantener abierto» sale en la tarjeta.
- Pruebas HTTP de todos los controladores con `@WebMvcTest` y la seguridad real (paquete `controller` de los tests):
  rutas públicas, 401 sin sesión, 403 por rol, tokens inválidos o revocados, validaciones y códigos de error.

## Pendiente recomendado

- Migrar a Spring Boot 4.x: toda la rama 3.x quedó sin soporte de código abierto el 30 de junio de 2026
  (3.3.13 es solo el último parche de 3.3). Implica Spring Framework 7, Jackson 3 y springdoc 3.
- Pruebas de integración con MongoDB desechable (Testcontainers) para índices, consultas geoespaciales y
  actualizaciones atómicas.
- Paginación en `GET /api/reportes`, comentarios y notificaciones (el listado y el mapa aún descargan todos).
- Quitar también en el servidor los metadatos EXIF de las fotos (hoy lo hace el navegador antes de subirlas).
- Content-Security-Policy en nginx y, a mediano plazo, sesión en cookie `HttpOnly` en lugar de `localStorage`.
- No exponer los id internos de usuarios en respuestas públicas: `GET /confirmaciones` (bastan el conteo y
  `confirmadoPorMi`), `ReporteResponse.usuarioId` y sobre todo `ComentarioResponse`, que junta `usuarioId` con
  `usuarioNombre` y permite, sin sesión, listar todos los reportes (con coordenadas) de una persona con nombre.
- Regla de fotos: permitir a moderadores y restringir por estado del reporte.
- Borrar las cuentas que nunca confirmaron su correo (por ejemplo, a los 7 días): hoy quedan ocupando el correo hasta
  que su dueña lo recupera con «¿Olvidaste tu contraseña?».
- Mover el límite de intentos a un almacén compartido si se despliegan varias réplicas del backend.
- Configuración por perfiles (`local`, `test`, `prod`) y despliegue automatizado. Hoy los valores por defecto de
  `application.properties` son de desarrollo y en producción una variable olvidada no falla: `MONGODB_URI` cae en
  localhost, `FRONTEND_URL` deja enlaces de recuperación a `localhost:5173`, el CORS solo admite localhost y Swagger
  queda público. `prod` exigiría esas variables sin valor por defecto (falla al arrancar si faltan), apagaría
  Swagger y bajaría el nivel de log; `test` fijaría un `JWT_SECRET` de prueba y apagaría Nominatim; `local`
  conservaría los valores actuales.
- Decidir si el modelo vuelve a relacionar reportes con el catálogo de barrios/calles (hoy usa el barrio de OSM).

### Hallazgos de las pruebas de caja negra (18 de octubre de 2026)

Corregidos el 26 de octubre de 2026 (ver «Completado»). Queda una tarea de despliegue: si se pone un proxy inverso
delante del backend, definir `TRUSTED_PROXIES` con su IP y no publicar el puerto 8080 a otras máquinas.

## Decisiones vigentes

- `Calle` se mantiene embebida en `Barrio` y `HistorialEstado` embebido en `Reporte`.
- Las categorías se activan o desactivan; no se eliminan físicamente.
- Los reportes no exponen una operación de borrado y los rechazados se conservan.
- La integridad referencial se valida desde los servicios porque MongoDB no la impone.
- Los votos «¿ya lo arreglaron?» no cambian el estado del reporte; lo decide un moderador. Excepción opcional, apagada
  por defecto: el cierre por la comunidad (`CIERRE_COMUNITARIO`), con 72 horas para que un moderador lo evite.
- La persistencia es MongoDB (no PostgreSQL/JPA/Flyway como plantea el documento de visión); ver la sección de
  decisiones del README raíz.
- Toda modificación de un documento existente usa actualizaciones parciales atómicas, no `save()`.
