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

## Pendiente recomendado

- Migrar a Spring Boot 4.x: toda la rama 3.x quedó sin soporte de código abierto el 30 de junio de 2026
  (3.3.13 es solo el último parche de 3.3). Implica Spring Framework 7, Jackson 3 y springdoc 3.
- Pruebas de integración con MongoDB desechable (Testcontainers) y pruebas HTTP de seguridad/controladores
  (`@WebMvcTest`) que verifiquen qué rutas son públicas y cuáles exigen rol.
- Paginación en `GET /api/reportes`, comentarios y notificaciones (el listado y el mapa aún descargan todos).
- Verificar el correo al registrarse (hoy cualquiera puede registrar un correo ajeno).
- Quitar también en el servidor los metadatos EXIF de las fotos (hoy lo hace el navegador antes de subirlas).
- Content-Security-Policy en nginx y, a mediano plazo, sesión en cookie `HttpOnly` en lugar de `localStorage`.
- No exponer los id internos de usuarios en respuestas públicas: `GET /confirmaciones` (bastan el conteo y
  `confirmadoPorMi`), `ReporteResponse.usuarioId` y sobre todo `ComentarioResponse`, que junta `usuarioId` con
  `usuarioNombre` y permite, sin sesión, listar todos los reportes (con coordenadas) de una persona con nombre.
- Regla de fotos: permitir a moderadores y restringir por estado del reporte.
- Mover el límite de intentos a un almacén compartido si se despliegan varias réplicas del backend.
- Configuración por perfiles (`local`, `test`, `prod`) y despliegue automatizado. Hoy los valores por defecto de
  `application.properties` son de desarrollo y en producción una variable olvidada no falla: `MONGODB_URI` cae en
  localhost, `FRONTEND_URL` deja enlaces de recuperación a `localhost:5173`, el CORS solo admite localhost y Swagger
  queda público. `prod` exigiría esas variables sin valor por defecto (falla al arrancar si faltan), apagaría
  Swagger y bajaría el nivel de log; `test` fijaría un `JWT_SECRET` de prueba y apagaría Nominatim; `local`
  conservaría los valores actuales.
- Opción de resolver automáticamente un reporte `EN_PROCESO` cuando suficientes votos digan «ya está arreglado».
- Decidir si el modelo vuelve a relacionar reportes con el catálogo de barrios/calles (hoy usa el barrio de OSM).

### Hallazgos de las pruebas de caja negra (18 de octubre de 2026)

- **Bloqueo de cuentas ajenas**: 5 contraseñas malas para un correo, desde IPs distintas, bloquean esa cuenta 15
  minutos también para su dueña (su contraseña correcta recibe 429); repitiéndolo, un moderador queda fuera
  indefinidamente. `AuthService.login` cuenta los fallos solo por correo (`login-email:`): contarlos por
  correo + IP y dejar un umbral global por correo mucho más alto.
- **`X-Forwarded-For` aceptado desde cualquier red privada**: con `server.forward-headers-strategy=native`, Tomcat
  confía en la cabecera si la conexión viene de 10.x, 172.16–31.x, 192.168.x o 127.x. Como Compose publica el
  8080 en todas las interfaces, cualquiera en la red local puede saltarse todos los límites por IP (login,
  registro, recuperación, geocodificación). Al desplegar: que solo el proxy alcance el backend y fijar
  `server.tomcat.remoteip.internal-proxies` a su IP.
- **Barrios sin unicidad**: dos `POST /api/barrios` con el mismo nombre crean dos barrios (no hay índice único ni
  comparación sin tildes/mayúsculas como en categorías), y `BarrioService.actualizar` y el alta de calles usan
  `save()` sobre el documento existente.
- Menores: una contraseña de más de 72 bytes da 409 en lugar de 400; una URL mal formada en `/uploads/` devuelve
  la página HTML de error de Tomcat en vez de `ErrorResponse`; `EditarReporte` (frontend) no comprueba al cargar
  si el reporte sigue `PENDIENTE` ni si quien edita es el autor (el backend sí lo impide, pero el aviso llega al
  guardar).

## Decisiones vigentes

- `Calle` se mantiene embebida en `Barrio` y `HistorialEstado` embebido en `Reporte`.
- Las categorías se activan o desactivan; no se eliminan físicamente.
- Los reportes no exponen una operación de borrado y los rechazados se conservan.
- La integridad referencial se valida desde los servicios porque MongoDB no la impone.
- Los votos «¿ya lo arreglaron?» no cambian el estado del reporte; lo decide un moderador.
- La persistencia es MongoDB (no PostgreSQL/JPA/Flyway como plantea el documento de visión); ver la sección de
  decisiones del README raíz.
- Toda modificación de un documento existente usa actualizaciones parciales atómicas, no `save()`.
