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
- OpenAPI/Swagger y documentación de configuración actualizada.
- Prueba unitaria de la transición de estado; `./mvnw test` pasa.

## Pendiente recomendado

- Pruebas de integración con MongoDB desechable (Testcontainers) y pruebas HTTP de seguridad/controladores.
- Paginación, ordenamiento y filtros avanzados para reportes, comentarios y notificaciones.
- Flujo controlado para promover usuarios a `MODERADOR` o `ADMIN` y datos iniciales del primer administrador.
- Auditoría, observabilidad y límites de tasa.
- `User-Agent` de Nominatim con un contacto real (hoy `contacto@ejemplo.com`), configurable.
- Regla de fotos: permitir a moderadores y restringir por estado del reporte.
- Limpiar el frontend duplicado (`src/App.tsx` vs `src/app/App.tsx`, `src/pages` vs `src/app/pages`).
- Automatización de despliegue y configuración por perfiles (`local`, `test`, `prod`).

## Decisiones vigentes

- `Calle` se mantiene embebida en `Barrio` y `HistorialEstado` embebido en `Reporte`.
- Las categorías se activan o desactivan; no se eliminan físicamente.
- Los reportes no exponen una operación de borrado y los rechazados se conservan.
- La integridad referencial se valida desde los servicios porque MongoDB no la impone.
