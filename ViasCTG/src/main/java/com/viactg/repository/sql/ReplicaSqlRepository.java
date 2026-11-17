package com.viactg.repository.sql;

import com.viactg.model.Barrio;
import com.viactg.model.Calle;
import com.viactg.model.Categoria;
import com.viactg.model.Comentario;
import com.viactg.model.Confirmacion;
import com.viactg.model.EstadoReporte;
import com.viactg.model.HistorialEstado;
import com.viactg.model.PrioridadReporte;
import com.viactg.model.RegistroAuditoria;
import com.viactg.model.Reporte;
import com.viactg.model.Seguimiento;
import com.viactg.model.Usuario;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Escrituras en la réplica PostgreSQL (esquema en db/migration/V1__esquema_replica.sql). Todas son "upsert"
 * ({@code INSERT ... ON CONFLICT DO UPDATE}): copiar dos veces el mismo documento deja la misma fila. Los hijos que en
 * MongoDB van embebidos (calles, fotos, historial) se reemplazan completos cada vez que se copia el padre.
 * <p>
 * Las llaves foráneas opcionales se escriben con una subconsulta ({@code (SELECT id FROM usuarios WHERE id = ?)}) que
 * da NULL si la fila referida no existe, y las filas cuyo padre obligatorio falta se omiten ({@code WHERE EXISTS}):
 * así un documento huérfano en Mongo no rompe la copia.
 */
public class ReplicaSqlRepository {
    /** Tablas en orden de dependencia: los padres antes que los hijos. */
    public static final List<String> TABLAS = List.of("usuarios", "categorias", "barrios", "calles", "reportes", "reporte_fotos",
            "historial_estados", "comentarios", "confirmaciones", "seguimientos", "auditoria");

    private static final String UPSERT_USUARIO = """
            INSERT INTO usuarios (id, nombre, email, telefono, rol, activo, email_verificado, fecha_registro)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET nombre = EXCLUDED.nombre, email = EXCLUDED.email, telefono = EXCLUDED.telefono,
                rol = EXCLUDED.rol, activo = EXCLUDED.activo, email_verificado = EXCLUDED.email_verificado,
                fecha_registro = EXCLUDED.fecha_registro""";
    private static final String UPSERT_CATEGORIA = """
            INSERT INTO categorias (id, nombre, descripcion, activa) VALUES (?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET nombre = EXCLUDED.nombre, descripcion = EXCLUDED.descripcion, activa = EXCLUDED.activa""";
    private static final String UPSERT_BARRIO = """
            INSERT INTO barrios (id, nombre, localidad) VALUES (?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET nombre = EXCLUDED.nombre, localidad = EXCLUDED.localidad""";
    private static final String INSERTAR_CALLE = """
            INSERT INTO calles (id, barrio_id, nombre, codigo_postal) VALUES (?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET barrio_id = EXCLUDED.barrio_id, nombre = EXCLUDED.nombre, codigo_postal = EXCLUDED.codigo_postal""";
    private static final String UPSERT_REPORTE = """
            INSERT INTO reportes (id, usuario_id, categoria_id, descripcion, latitud, longitud, direccion_osm, barrio_osm, estado,
                                  prioridad, fecha_creacion, fecha_actualizacion)
            VALUES (?, (SELECT id FROM usuarios WHERE id = ?), (SELECT id FROM categorias WHERE id = ?), ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET usuario_id = EXCLUDED.usuario_id, categoria_id = EXCLUDED.categoria_id,
                descripcion = EXCLUDED.descripcion, latitud = EXCLUDED.latitud, longitud = EXCLUDED.longitud,
                direccion_osm = EXCLUDED.direccion_osm, barrio_osm = EXCLUDED.barrio_osm, estado = EXCLUDED.estado,
                prioridad = EXCLUDED.prioridad, fecha_creacion = EXCLUDED.fecha_creacion,
                fecha_actualizacion = EXCLUDED.fecha_actualizacion""";
    private static final String INSERTAR_FOTO = "INSERT INTO reporte_fotos (reporte_id, orden, ruta) VALUES (?, ?, ?)";
    private static final String INSERTAR_HISTORIAL = """
            INSERT INTO historial_estados (id, reporte_id, orden, usuario_admin_id, estado_anterior, estado_nuevo, comentario, fecha)
            VALUES (?, ?, ?, (SELECT id FROM usuarios WHERE id = ?), ?, ?, ?, ?)""";
    private static final String UPSERT_COMENTARIO = """
            INSERT INTO comentarios (id, reporte_id, usuario_id, texto, fecha, editado)
            SELECT ?, ?, ?, ?, ?, ?
            WHERE EXISTS (SELECT 1 FROM reportes WHERE id = ?) AND EXISTS (SELECT 1 FROM usuarios WHERE id = ?)
            ON CONFLICT (id) DO UPDATE SET texto = EXCLUDED.texto, fecha = EXCLUDED.fecha, editado = EXCLUDED.editado""";
    private static final String INSERTAR_CONFIRMACION = """
            INSERT INTO confirmaciones (id, reporte_id, usuario_id, fecha)
            SELECT ?, ?, ?, ?
            WHERE EXISTS (SELECT 1 FROM reportes WHERE id = ?) AND EXISTS (SELECT 1 FROM usuarios WHERE id = ?)
            ON CONFLICT DO NOTHING""";
    private static final String INSERTAR_SEGUIMIENTO = """
            INSERT INTO seguimientos (id, reporte_id, usuario_id, arreglado, fecha)
            SELECT ?, ?, ?, ?, ?
            WHERE EXISTS (SELECT 1 FROM reportes WHERE id = ?) AND EXISTS (SELECT 1 FROM usuarios WHERE id = ?)
            ON CONFLICT DO NOTHING""";
    private static final String INSERTAR_AUDITORIA = """
            INSERT INTO auditoria (id, fecha, actor_id, actor_nombre, accion, entidad, entidad_id, detalle)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING""";

    private final JdbcTemplate jdbc;

    public ReplicaSqlRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- usuarios, categorías y barrios

    public void guardarUsuarios(Collection<Usuario> usuarios) {
        lote(UPSERT_USUARIO, usuarios.stream().map(u -> new Object[]{u.getId(), u.getNombre(), u.getEmail(), u.getTelefono(),
                u.getRol() == null ? "CIUDADANO" : u.getRol().name(), u.isActivo(), u.isEmailVerificado(), fecha(u.getFechaRegistro())}).toList());
    }

    public void guardarCategorias(Collection<Categoria> categorias) {
        lote(UPSERT_CATEGORIA, categorias.stream().map(c -> new Object[]{c.getId(), c.getNombre(), c.getDescripcion(), c.isActiva()}).toList());
    }

    /** Guarda los barrios y reemplaza sus calles por las que tienen ahora en Mongo. */
    public void guardarBarrios(Collection<Barrio> barrios) {
        lote(UPSERT_BARRIO, barrios.stream().map(b -> new Object[]{b.getId(), b.getNombre(), b.getLocalidad()}).toList());
        lote("DELETE FROM calles WHERE barrio_id = ?", barrios.stream().map(b -> new Object[]{b.getId()}).toList());
        List<Object[]> calles = new ArrayList<>();
        for (Barrio barrio : barrios) {
            for (Calle calle : lista(barrio.getCalles())) {
                calles.add(new Object[]{calle.getId(), barrio.getId(), calle.getNombre(), calle.getCodigoPostal()});
            }
        }
        lote(INSERTAR_CALLE, calles);
    }

    public void eliminarBarrio(String id) {
        jdbc.update("DELETE FROM barrios WHERE id = ?", id);
    }

    // ---------------------------------------------------------------- reportes con sus fotos e historial

    /** Guarda los reportes y reemplaza sus fotos e historial (en Mongo van embebidos en el reporte). */
    public void guardarReportes(Collection<Reporte> reportes) {
        lote(UPSERT_REPORTE, reportes.stream().map(r -> new Object[]{r.getId(), r.getUsuarioId(),
                r.getCategoria() == null ? null : r.getCategoria().getId(), r.getDescripcion() == null ? "" : r.getDescripcion(),
                r.getLatitud(), r.getLongitud(), r.getDireccionOsm(), r.getBarrioOsm(),
                (r.getEstado() == null ? EstadoReporte.PENDIENTE : r.getEstado()).name(),
                (r.getPrioridad() == null ? PrioridadReporte.MEDIA : r.getPrioridad()).name(), fecha(r.getFechaCreacion()),
                fecha(r.getFechaActualizacion())}).toList());
        List<Object[]> ids = reportes.stream().map(r -> new Object[]{r.getId()}).toList();
        lote("DELETE FROM reporte_fotos WHERE reporte_id = ?", ids);
        lote("DELETE FROM historial_estados WHERE reporte_id = ?", ids);
        List<Object[]> fotos = new ArrayList<>();
        List<Object[]> historial = new ArrayList<>();
        for (Reporte reporte : reportes) {
            List<String> rutas = lista(reporte.getFotos());
            for (int i = 0; i < rutas.size(); i++) fotos.add(new Object[]{reporte.getId(), i + 1, rutas.get(i)});
            List<HistorialEstado> cambios = lista(reporte.getHistorialEstados());
            for (int i = 0; i < cambios.size(); i++) {
                HistorialEstado cambio = cambios.get(i);
                // El id del historial es un UUID; si faltara, se arma uno estable con el reporte y la posición.
                String id = cambio.getId() == null ? reporte.getId() + "-" + (i + 1) : cambio.getId();
                historial.add(new Object[]{id, reporte.getId(), i + 1, cambio.getUsuarioAdminId(), nombre(cambio.getEstadoAnterior()),
                        nombre(cambio.getEstadoNuevo()), cambio.getComentarioAdmin(), fecha(cambio.getFecha())});
            }
        }
        lote(INSERTAR_FOTO, fotos);
        lote(INSERTAR_HISTORIAL, historial);
    }

    public void eliminarReporte(String id) {
        jdbc.update("DELETE FROM reportes WHERE id = ?", id);
    }

    // ---------------------------------------------------------------- participación

    public void guardarComentarios(Collection<Comentario> comentarios) {
        lote(UPSERT_COMENTARIO, comentarios.stream().map(c -> new Object[]{c.getId(), c.getReporteId(), c.getUsuarioId(),
                c.getTexto() == null ? "" : c.getTexto(), fecha(c.getFecha()), c.isEditado(), c.getReporteId(), c.getUsuarioId()}).toList());
    }

    public void eliminarComentario(String id) {
        jdbc.update("DELETE FROM comentarios WHERE id = ?", id);
    }

    public void insertarConfirmaciones(Collection<Confirmacion> confirmaciones) {
        lote(INSERTAR_CONFIRMACION, confirmaciones.stream().map(c -> new Object[]{c.getId(), c.getReporteId(), c.getUsuarioId(),
                fecha(c.getFecha()), c.getReporteId(), c.getUsuarioId()}).toList());
    }

    /** Deja las confirmaciones de un reporte iguales a las de Mongo (alguien pudo retirar la suya). */
    public void reemplazarConfirmaciones(String reporteId, Collection<Confirmacion> confirmaciones) {
        jdbc.update("DELETE FROM confirmaciones WHERE reporte_id = ?", reporteId);
        insertarConfirmaciones(confirmaciones);
    }

    public void insertarSeguimientos(Collection<Seguimiento> seguimientos) {
        lote(INSERTAR_SEGUIMIENTO, seguimientos.stream().map(s -> new Object[]{s.getId(), s.getReporteId(), s.getUsuarioId(),
                s.isArreglado(), fecha(s.getFecha()), s.getReporteId(), s.getUsuarioId()}).toList());
    }

    /** Deja los votos de un reporte iguales a los de Mongo (un usuario puede cambiar su voto). */
    public void reemplazarSeguimientos(String reporteId, Collection<Seguimiento> seguimientos) {
        jdbc.update("DELETE FROM seguimientos WHERE reporte_id = ?", reporteId);
        insertarSeguimientos(seguimientos);
    }

    public void insertarAuditoria(Collection<RegistroAuditoria> registros) {
        lote(INSERTAR_AUDITORIA, registros.stream().map(a -> new Object[]{a.getId(), fecha(a.getFecha()), a.getActorId(),
                a.getActorNombre(), a.getAccion() == null ? "DESCONOCIDA" : a.getAccion(), a.getEntidad(), a.getEntidadId(),
                a.getDetalle()}).toList());
    }

    // ---------------------------------------------------------------- consultas y mantenimiento

    public boolean existe(String tabla, String id) {
        if (!TABLAS.contains(tabla)) throw new IllegalArgumentException("Tabla desconocida: " + tabla);
        Boolean existe = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM " + tabla + " WHERE id = ?)", Boolean.class, id);
        return Boolean.TRUE.equals(existe);
    }

    /** Filas por tabla, en el orden de {@link #TABLAS}. */
    public Map<String, Long> contarFilas() {
        Map<String, Long> filas = new LinkedHashMap<>();
        for (String tabla : TABLAS) filas.put(tabla, jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Long.class));
        return filas;
    }

    /** Vacía todas las tablas antes de reconstruir la réplica (dentro de la misma transacción que la copia). */
    public void vaciar() {
        jdbc.execute("TRUNCATE " + String.join(", ", TABLAS));
    }

    private void lote(String sql, List<Object[]> filas) {
        if (!filas.isEmpty()) jdbc.batchUpdate(sql, filas);
    }

    private static OffsetDateTime fecha(Instant instante) {
        return instante == null ? null : instante.atOffset(ZoneOffset.UTC);
    }

    private static String nombre(Enum<?> valor) {
        return valor == null ? null : valor.name();
    }

    private static <T> List<T> lista(List<T> valores) {
        return valores == null ? List.of() : valores;
    }
}
