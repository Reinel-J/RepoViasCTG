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

## Pendiente recomendado

- Pruebas de integración con MongoDB desechable (Testcontainers) y pruebas HTTP de seguridad/controladores.
- Paginación, ordenamiento y filtros avanzados para reportes, comentarios y notificaciones.
- Auditoría, observabilidad y límites de tasa.
- `User-Agent` de Nominatim con un contacto real (hoy `contacto@ejemplo.com`), configurable.
- Regla de fotos: permitir a moderadores y restringir por estado del reporte.
- Mover el límite de intentos a un almacén compartido si se despliegan varias réplicas del backend.
- Automatización de despliegue y configuración por perfiles (`local`, `test`, `prod`).
- `ReporteService.actualizar` y `subirFotos` guardan el documento completo con `save()`: una escritura
  concurrente (cambio de estado, dirección OSM, `fechaUltimoSeguimiento`) puede perderse. Usar `@Version` o
  actualizaciones parciales.
- Opción de resolver automáticamente un reporte `EN_PROCESO` cuando suficientes votos digan «ya está arreglado».
- Las estadísticas agrupan categorías sin distinguir mayúsculas, pero «Inundación» e «Inundacion» siguen separadas.

## Decisiones vigentes

- `Calle` se mantiene embebida en `Barrio` y `HistorialEstado` embebido en `Reporte`.
- Las categorías se activan o desactivan; no se eliminan físicamente.
- Los reportes no exponen una operación de borrado y los rechazados se conservan.
- La integridad referencial se valida desde los servicios porque MongoDB no la impone.
- Los votos «¿ya lo arreglaron?» no cambian el estado del reporte; lo decide un moderador.
