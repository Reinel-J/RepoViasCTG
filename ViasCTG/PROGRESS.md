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

## Pendiente recomendado

- Migrar a Spring Boot 4.x: toda la rama 3.x quedó sin soporte de código abierto el 30 de junio de 2026
  (3.3.13 es solo el último parche de 3.3). Implica Spring Framework 7, Jackson 3 y springdoc 3.
- Pruebas de integración con MongoDB desechable (Testcontainers) y pruebas HTTP de seguridad/controladores
  (`@WebMvcTest`) que verifiquen qué rutas son públicas y cuáles exigen rol.
- Paginación en `GET /api/reportes`, comentarios y notificaciones (el listado y el mapa aún descargan todos).
- Verificar el correo al registrarse (hoy cualquiera puede registrar un correo ajeno).
- Quitar también en el servidor los metadatos EXIF de las fotos (hoy lo hace el navegador antes de subirlas).
- Content-Security-Policy en nginx y, a mediano plazo, sesión en cookie `HttpOnly` en lugar de `localStorage`.
- No exponer los id internos de quienes confirman en `GET /confirmaciones` (bastan el conteo y `confirmadoPorMi`).
- Regla de fotos: permitir a moderadores y restringir por estado del reporte.
- Mover el límite de intentos a un almacén compartido si se despliegan varias réplicas del backend.
- Configuración por perfiles (`local`, `test`, `prod`) y despliegue automatizado.
- Opción de resolver automáticamente un reporte `EN_PROCESO` cuando suficientes votos digan «ya está arreglado».
- Decidir si el modelo vuelve a relacionar reportes con el catálogo de barrios/calles (hoy usa el barrio de OSM).

## Decisiones vigentes

- `Calle` se mantiene embebida en `Barrio` y `HistorialEstado` embebido en `Reporte`.
- Las categorías se activan o desactivan; no se eliminan físicamente.
- Los reportes no exponen una operación de borrado y los rechazados se conservan.
- La integridad referencial se valida desde los servicios porque MongoDB no la impone.
- Los votos «¿ya lo arreglaron?» no cambian el estado del reporte; lo decide un moderador.
- La persistencia es MongoDB (no PostgreSQL/JPA/Flyway como plantea el documento de visión); ver la sección de
  decisiones del README raíz.
- Toda modificación de un documento existente usa actualizaciones parciales atómicas, no `save()`.
