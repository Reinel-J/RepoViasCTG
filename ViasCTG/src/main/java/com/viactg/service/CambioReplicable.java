package com.viactg.service;

/**
 * Aviso de que algo cambió en MongoDB y debe copiarse a la réplica SQL. Lo publican los servicios después de escribir
 * (con {@code ApplicationEventPublisher}) y lo atiende {@link ReplicaSqlService} en segundo plano: vuelve a leer el
 * documento de Mongo por su id y deja la fila igual, o la borra si el documento ya no existe. Por eso el aviso solo
 * lleva el id y da igual cuántas veces llegue.
 */
public record CambioReplicable(Entidad entidad, String id) {

    public enum Entidad {
        USUARIO, CATEGORIA, BARRIO, REPORTE, COMENTARIO,
        /** {@code id} es el del reporte: se copian todas sus confirmaciones. */
        CONFIRMACIONES_DE_REPORTE,
        /** {@code id} es el del reporte: se copian todos sus votos "¿ya lo arreglaron?". */
        SEGUIMIENTOS_DE_REPORTE,
        AUDITORIA,
        /** Cambios en bloque (datos de demostración): se reconstruye la réplica completa; {@code id} no se usa. */
        TODO
    }

    public static CambioReplicable usuario(String id) { return new CambioReplicable(Entidad.USUARIO, id); }
    public static CambioReplicable categoria(String id) { return new CambioReplicable(Entidad.CATEGORIA, id); }
    public static CambioReplicable barrio(String id) { return new CambioReplicable(Entidad.BARRIO, id); }
    public static CambioReplicable reporte(String id) { return new CambioReplicable(Entidad.REPORTE, id); }
    public static CambioReplicable comentario(String id) { return new CambioReplicable(Entidad.COMENTARIO, id); }
    public static CambioReplicable confirmaciones(String reporteId) { return new CambioReplicable(Entidad.CONFIRMACIONES_DE_REPORTE, reporteId); }
    public static CambioReplicable seguimientos(String reporteId) { return new CambioReplicable(Entidad.SEGUIMIENTOS_DE_REPORTE, reporteId); }
    public static CambioReplicable auditoria(String id) { return new CambioReplicable(Entidad.AUDITORIA, id); }
    public static CambioReplicable todo() { return new CambioReplicable(Entidad.TODO, null); }
}
