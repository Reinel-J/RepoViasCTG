import { useEffect, useState } from "react"
import * as adminApi from "../api/admin"
import type { EstadoReporte, ReporteResponse } from "../api/types"
import { Icon } from "../app/components/UI"

const SIGUIENTES_ESTADOS: EstadoReporte[] = ["EN_REVISION", "RECHAZADO"]

export function PanelAdmin() {
  const [reportes, setReportes] = useState<ReporteResponse[]>([])
  const [error, setError] = useState("")
  const [cargando, setCargando] = useState(true)
  const [procesandoId, setProcesandoId] = useState<string | null>(null)

  useEffect(() => {
    adminApi.listarPendientes().then(setReportes).catch((exception) => setError(exception instanceof Error ? exception.message : "No fue posible cargar los reportes pendientes.")).finally(() => setCargando(false))
  }, [])

  async function actualizarEstado(reporteId: string, nuevoEstado: EstadoReporte) {
    setProcesandoId(reporteId)
    setError("")
    try {
      await adminApi.cambiarEstado(reporteId, nuevoEstado)
      setReportes((current) => current.filter((report) => report.id !== reporteId))
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar el estado.")
    } finally {
      setProcesandoId(null)
    }
  }

  return (
    <main className="page-shell section internal-page">
      <div className="page-heading"><div><span className="eyebrow">Gestión interna</span><h1>Panel de moderación</h1><p>Revisa los reportes pendientes y decide el siguiente paso.</p></div><div className="admin-count"><strong>{reportes.length}</strong><span>Pendientes</span></div></div>
      {cargando && <div className="loading">Cargando reportes pendientes…</div>}
      {error && <p className="alert error" role="alert">{error}</p>}
      {!cargando && reportes.length === 0 && <div className="empty-state"><Icon name="check" className="empty-icon" /><h2>Todo está al día</h2><p>No hay reportes pendientes por moderar.</p></div>}
      <section className="admin-list">
        {reportes.map((reporte) => (
          <article key={reporte.id}>
            <span className={`priority priority-${reporte.prioridad.toLowerCase()}`}>{reporte.prioridad}</span>
            <div><small>Reporte #{reporte.id.slice(0, 7)}</small><h2>{reporte.descripcion}</h2><p>{new Date(reporte.fechaCreacion).toLocaleDateString("es-CO")}</p></div>
            <div className="admin-actions">
              {SIGUIENTES_ESTADOS.map((estado) => <button className={estado === "RECHAZADO" ? "button button-ghost" : "button button-dark"} key={estado} disabled={procesandoId === reporte.id} onClick={() => actualizarEstado(reporte.id, estado)}>{estado === "RECHAZADO" ? "Rechazar" : "Revisar"}</button>)}
            </div>
          </article>
        ))}
      </section>
    </main>
  )
}
