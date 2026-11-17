-- Vistas de análisis sobre la réplica: lo que en MongoDB necesita agregaciones ($group, $unwind, $lookup) aquí es un
-- JOIN. Sirven para Metabase, Power BI o consultas directas.

-- Reportes por categoría y estado, con el porcentaje resuelto (sin contar rechazados).
CREATE VIEW v_reportes_por_categoria AS
SELECT c.id                                                  AS categoria_id,
       c.nombre                                              AS categoria,
       COUNT(r.id)                                           AS total,
       COUNT(r.id) FILTER (WHERE r.estado = 'PENDIENTE')     AS pendientes,
       COUNT(r.id) FILTER (WHERE r.estado = 'EN_REVISION')   AS en_revision,
       COUNT(r.id) FILTER (WHERE r.estado = 'EN_PROCESO')    AS en_proceso,
       COUNT(r.id) FILTER (WHERE r.estado = 'RESUELTO')      AS resueltos,
       COUNT(r.id) FILTER (WHERE r.estado = 'RECHAZADO')     AS rechazados,
       ROUND(100.0 * COUNT(r.id) FILTER (WHERE r.estado = 'RESUELTO')
             / NULLIF(COUNT(r.id) FILTER (WHERE r.estado <> 'RECHAZADO'), 0), 1) AS porcentaje_resuelto
FROM categorias c
LEFT JOIN reportes r ON r.categoria_id = c.id
GROUP BY c.id, c.nombre;

-- Cada reporte con su autor, su categoría y cuánto tardó la primera respuesta y la resolución (en días).
CREATE VIEW v_reportes_detalle AS
SELECT r.id,
       r.fecha_creacion,
       r.estado,
       r.prioridad,
       c.nombre                                  AS categoria,
       u.nombre                                  AS autor,
       r.barrio_osm                              AS barrio,
       r.latitud,
       r.longitud,
       (SELECT COUNT(*) FROM confirmaciones cf WHERE cf.reporte_id = r.id) AS confirmaciones,
       (SELECT COUNT(*) FROM comentarios co WHERE co.reporte_id = r.id)    AS comentarios,
       EXTRACT(EPOCH FROM (primera.fecha - r.fecha_creacion)) / 86400.0   AS dias_primera_respuesta,
       EXTRACT(EPOCH FROM (resuelto.fecha - r.fecha_creacion)) / 86400.0  AS dias_resolucion
FROM reportes r
LEFT JOIN categorias c ON c.id = r.categoria_id
LEFT JOIN usuarios u ON u.id = r.usuario_id
LEFT JOIN historial_estados primera ON primera.reporte_id = r.id AND primera.orden = 1
LEFT JOIN LATERAL (SELECT h.fecha FROM historial_estados h
                   WHERE h.reporte_id = r.id AND h.estado_nuevo = 'RESUELTO'
                   ORDER BY h.orden LIMIT 1) resuelto ON TRUE;

-- Trabajo de cada moderador o administrador: cuántos cambios de estado hizo y de qué tipo.
CREATE VIEW v_actividad_moderadores AS
SELECT u.id                                                     AS usuario_id,
       u.nombre,
       u.rol,
       COUNT(h.id)                                              AS cambios_de_estado,
       COUNT(h.id) FILTER (WHERE h.estado_nuevo = 'RESUELTO')   AS resueltos,
       COUNT(h.id) FILTER (WHERE h.estado_nuevo = 'RECHAZADO')  AS rechazados,
       MAX(h.fecha)                                             AS ultimo_cambio
FROM usuarios u
JOIN historial_estados h ON h.usuario_admin_id = u.id
GROUP BY u.id, u.nombre, u.rol;
