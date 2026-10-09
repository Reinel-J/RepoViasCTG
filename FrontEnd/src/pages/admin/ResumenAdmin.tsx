import { useEffect, useState } from "react"
import * as reportesApi from "../../api/reportes"
import type { EstadoReporte, ReporteResponse } from "../../api/types"
import { ESTADOS, ETIQUETA_ESTADO, PRIORIDADES, mensajeError } from "./estados"

export function ResumenAdmin({ onVerReportes }: { onVerReportes: (estado?: EstadoReporte) => void }) {
  const [reportes, setReportes] = useState<ReporteResponse[]>([])
  const [consultadoEn, setConsultadoEn] = useState(0)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState("")

  useEffect(() => {
    reportesApi.listar()
      .then((lista) => {
        setReportes(lista)
        setConsultadoEn(Date.now())
      })
      .catch((exception) => setError(mensajeError(exception, "No fue posible cargar el resumen.")))
      .finally(() => setCargando(false))
  }, [])

  if (cargando) return <div className="loading">Calculando resumen…</div>
  if (error) return <p className="alert error" role="alert">{error}</p>

  const porEstado = (estado: EstadoReporte) => reportes.filter((reporte) => reporte.estado === estado).length
  const activos = reportes.filter((reporte) => reporte.estado !== "RESUELTO" && reporte.estado !== "RECHAZADO")
  const altaActivos = activos.filter((reporte) => reporte.prioridad === "ALTA").length
  const atendidos = reportes.length ? Math.round(((reportes.length - porEstado("PENDIENTE")) / reportes.length) * 100) : 0
  const masAntiguo = reportes
    .filter((reporte) => reporte.estado === "PENDIENTE")
    .sort((a, b) => a.fechaCreacion.localeCompare(b.fechaCreacion))[0]
  const diasEspera = masAntiguo ? Math.floor((consultadoEn - new Date(masAntiguo.fechaCreacion).getTime()) / 86_400_000) : 0

  return (
    <section className="admin-section">
      <div className="admin-stats">
        <button className="admin-stat" onClick={() => onVerReportes()}><strong>{reportes.length}</strong><span>Reportes totales</span></button>
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
              <span className="admin-bar-track"><span className={`admin-bar-fill status-${estado.toLowerCase()}`} style={{ width: `${reportes.length ? (porEstado(estado) / reportes.length) * 100 : 0}%` }} /></span>
              <strong>{porEstado(estado)}</strong>
            </button>
          ))}
        </div>
        <div className="admin-card">
          <h2>Prioridad de los activos</h2>
          {PRIORIDADES.map((prioridad) => {
            const total = activos.filter((reporte) => reporte.prioridad === prioridad).length
            return (
              <div key={prioridad} className="admin-bar">
                <span>{prioridad.charAt(0) + prioridad.slice(1).toLowerCase()}</span>
                <span className="admin-bar-track"><span className={`admin-bar-fill priority-${prioridad.toLowerCase()}`} style={{ width: `${activos.length ? (total / activos.length) * 100 : 0}%` }} /></span>
                <strong>{total}</strong>
              </div>
            )
          })}
        </div>
      </div>
    </section>
  )
}
