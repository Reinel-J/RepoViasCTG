import { useCallback, useEffect, useState } from "react"
import * as adminApi from "../../api/admin"
import type { EstadoReplicaResponse } from "../../api/types"
import { mensajeError } from "./estados"

// Mientras se reconstruye, el estado se vuelve a pedir cada pocos segundos para mostrar cuándo terminó.
const INTERVALO_MIENTRAS_RECONSTRUYE_MS = 3_000

const NOMBRE_TABLA: Record<string, string> = {
  usuarios: "Usuarios (sin contraseñas)",
  categorias: "Categorías",
  barrios: "Barrios",
  calles: "Calles (embebidas en barrios)",
  reportes: "Reportes",
  reporte_fotos: "Fotos (embebidas en reportes)",
  historial_estados: "Historial de estados (embebido en reportes)",
  comentarios: "Comentarios",
  confirmaciones: "Confirmaciones",
  seguimientos: "Votos «¿ya lo arreglaron?»",
  auditoria: "Auditoría",
}

function fecha(valor: string | null) {
  return valor ? new Date(valor).toLocaleString("es-CO") : "—"
}

/**
 * Base secundaria: la réplica de MongoDB en PostgreSQL. Compara, tabla por tabla, cuántos registros hay en cada base
 * y permite reconstruir la réplica completa.
 */
export function ReplicaSqlAdmin() {
  const [estado, setEstado] = useState<EstadoReplicaResponse | null>(null)
  const [error, setError] = useState("")
  const [aviso, setAviso] = useState("")

  const consultar = useCallback(() => adminApi.estadoReplica()
    .then((respuesta) => {
      setEstado(respuesta)
      setError("")
    })
    .catch((exception) => setError(mensajeError(exception, "No fue posible consultar la réplica SQL."))), [])

  useEffect(() => {
    consultar()
  }, [consultar])

  useEffect(() => {
    if (!estado?.pendienteReconstruir || estado.ultimoError) return
    const temporizador = window.setTimeout(consultar, INTERVALO_MIENTRAS_RECONSTRUYE_MS)
    return () => window.clearTimeout(temporizador)
  }, [estado, consultar])

  async function reconstruir() {
    setAviso("Reconstruyendo la réplica…")
    try {
      await adminApi.reconstruirReplica()
      setAviso("Reconstrucción en curso: las cifras se actualizan solas.")
      await consultar()
    } catch (exception) {
      setAviso(mensajeError(exception, "No fue posible reconstruir la réplica."))
    }
  }

  if (error) return <p className="alert error" role="alert">{error}</p>
  if (!estado) return <div className="loading">Consultando la réplica SQL…</div>

  const situacion = !estado.habilitada ? "Deshabilitada" : !estado.conectada ? "Sin conexión" : estado.pendienteReconstruir ? "Reconstruyendo" : "Sincronizada"
  const totalMongo = estado.tablas.reduce((suma, tabla) => suma + tabla.mongo, 0)
  const totalSql = estado.tablas.reduce((suma, tabla) => suma + (tabla.sql ?? 0), 0)

  return (
    <section className="admin-section">
      <div className="admin-stats">
        <div className="admin-stat"><strong>{situacion}</strong><span>PostgreSQL (base secundaria)</span></div>
        <div className="admin-stat"><strong>{totalMongo.toLocaleString("es-CO")}</strong><span>Registros en MongoDB</span></div>
        <div className="admin-stat"><strong>{estado.conectada ? totalSql.toLocaleString("es-CO") : "—"}</strong><span>Filas en PostgreSQL</span></div>
        <div className="admin-stat"><strong>{fecha(estado.ultimaSincronizacion)}</strong><span>Última copia</span></div>
      </div>
      {!estado.habilitada && <p className="alert" role="status">La réplica está apagada. Actívala con SQL_REPLICA_HABILITADA=true (o app.sql.habilitada en local.properties).</p>}
      {estado.ultimoError && <p className="alert error" role="alert">Último error ({fecha(estado.ultimoErrorEn)}): {estado.ultimoError}. Se reintenta automáticamente.</p>}
      <div className="admin-utility">
        <button className="button button-ghost button-small" onClick={reconstruir} disabled={!estado.habilitada}>Reconstruir réplica</button>
        <button className="button button-ghost button-small" onClick={consultar}>Actualizar</button>
        {aviso && <span role="status">{aviso}</span>}
      </div>
      <div className="admin-table-wrap">
        <table className="admin-table">
          <thead><tr><th>Tabla en PostgreSQL</th><th>MongoDB</th><th>PostgreSQL</th><th>Estado</th></tr></thead>
          <tbody>
            {estado.tablas.map((tabla) => (
              <tr key={tabla.nombre}>
                <td><code>{tabla.nombre}</code><small>{NOMBRE_TABLA[tabla.nombre] ?? ""}</small></td>
                <td>{tabla.mongo.toLocaleString("es-CO")}</td>
                <td>{tabla.sql === null ? "—" : tabla.sql.toLocaleString("es-CO")}</td>
                <td>{tabla.sql === null ? "Sin datos" : tabla.sql === tabla.mongo ? "Igual" : "Diferente"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="muted">
        MongoDB es la base principal. Cada cambio se copia a PostgreSQL en segundo plano y, al arrancar el servidor, la réplica se
        reconstruye completa. Lo que en Mongo va embebido (calles, fotos, historial) aquí son tablas con llave foránea.
        Última reconstrucción: {fecha(estado.ultimaReconstruccion)}.
      </p>
    </section>
  )
}
