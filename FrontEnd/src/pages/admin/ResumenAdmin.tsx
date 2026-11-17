import { useEffect, useState } from "react"
import * as adminApi from "../../api/admin"
import type { EstadisticasResponse, EstadoReporte, PrioridadReporte } from "../../api/types"
import { ESTADOS, ETIQUETA_ESTADO, PRIORIDADES, mensajeError } from "./estados"

// Las cifras salen de GET /admin/estadisticas (conteos en la base de datos): el listado de reportes es paginado.
export function ResumenAdmin({ onVerReportes }: { onVerReportes: (estado?: EstadoReporte) => void }) {
  const [datos, setDatos] = useState<{ estadisticas: EstadisticasResponse; consultadoEn: number } | null>(null)
  const [error, setError] = useState("")

  useEffect(() => {
    adminApi.estadisticas()
      .then((estadisticas) => setDatos({ estadisticas, consultadoEn: Date.now() }))
      .catch((exception) => setError(mensajeError(exception, "No fue posible cargar el resumen.")))
  }, [])

  if (error) return <p className="alert error" role="alert">{error}</p>
  if (!datos) return <div className="loading">Calculando resumen…</div>

  const { estadisticas, consultadoEn } = datos
  const total = estadisticas.total
  const porEstado = (estado: EstadoReporte) => estadisticas.porEstado[estado] ?? 0
  const porPrioridad = (prioridad: PrioridadReporte) => estadisticas.prioridadActivos[prioridad] ?? 0
  const totalActivos = PRIORIDADES.reduce((suma, prioridad) => suma + porPrioridad(prioridad), 0)
  const altaActivos = porPrioridad("ALTA")
  const atendidos = total ? Math.round(((total - porEstado("PENDIENTE")) / total) * 100) : 0
  const diasEspera = estadisticas.pendienteMasAntiguo
    ? Math.floor((consultadoEn - new Date(estadisticas.pendienteMasAntiguo).getTime()) / 86_400_000)
    : 0

  return (
    <section className="admin-section">
      <div className="admin-stats">
        <button className="admin-stat" onClick={() => onVerReportes()}><strong>{total}</strong><span>Reportes totales</span></button>
        <button className="admin-stat" onClick={() => onVerReportes("PENDIENTE")}><strong>{porEstado("PENDIENTE")}</strong><span>Por moderar</span></button>
        <div className="admin-stat"><strong>{altaActivos}</strong><span>Activos con prioridad alta</span></div>
        <div className="admin-stat"><strong>{atendidos}%</strong><span>Ya recibieron respuesta</span></div>
        <div className="admin-stat"><strong>{diasEspera}</strong><span>Días del pendiente más antiguo</span></div>
      </div>
      <div className="admin-grid-2">
        <div className="admin-card">
          <h2>Por estado</h2>
          {ESTADOS.map((estado) => (
            <button key={estado} className="admin-bar" onClick={() => onVerReportes(estado)}>
              <span>{ETIQUETA_ESTADO[estado]}</span>
              <span className="admin-bar-track"><span className={`admin-bar-fill status-${estado.toLowerCase()}`} style={{ width: `${total ? (porEstado(estado) / total) * 100 : 0}%` }} /></span>
              <strong>{porEstado(estado)}</strong>
            </button>
          ))}
        </div>
        <div className="admin-card">
          <h2>Prioridad de los activos</h2>
          {PRIORIDADES.map((prioridad) => {
            const cantidad = porPrioridad(prioridad)
            return (
              <div key={prioridad} className="admin-bar">
                <span>{prioridad.charAt(0) + prioridad.slice(1).toLowerCase()}</span>
                <span className="admin-bar-track"><span className={`admin-bar-fill priority-${prioridad.toLowerCase()}`} style={{ width: `${totalActivos ? (cantidad / totalActivos) * 100 : 0}%` }} /></span>
                <strong>{cantidad}</strong>
              </div>
            )
          })}
        </div>
      </div>
    </section>
  )
}
