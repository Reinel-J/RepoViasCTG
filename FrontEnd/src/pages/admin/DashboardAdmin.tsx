import { useEffect, useState } from "react"
import * as adminApi from "../../api/admin"
import type { DashboardResponse } from "../../api/types"
import { descargarCsv } from "../../utils/csv"
import { estadisticasACsv, nombreArchivoEstadisticas } from "./estadisticasCsv"
import { mensajeError } from "./estados"
import { inicioMetabase } from "./metabase"

// Se pide una URL nueva un minuto antes de que venza la firmada: vencida, el dashboard deja de cargar sus datos.
const MARGEN_RENOVACION_MS = 60_000
const ESPERA_MINIMA_MS = 15_000

/** Dashboard de Metabase embebido con una URL firmada por el backend (GET /api/admin/dashboard). */
export function DashboardAdmin({ esAdmin }: { esAdmin: boolean }) {
  const [dashboard, setDashboard] = useState<DashboardResponse | null>(null)
  const [error, setError] = useState("")
  const [cargando, setCargando] = useState(true)
  const [descarga, setDescarga] = useState("")

  function pedirUrl() {
    return adminApi.obtenerDashboard()
      .then((respuesta) => {
        setDashboard(respuesta)
        setError("")
      })
      .catch((exception) => setError(mensajeError(exception, "No fue posible cargar el dashboard.")))
      .finally(() => setCargando(false))
  }

  useEffect(() => {
    pedirUrl()
  }, [])

  // Renovación automática antes del vencimiento; se reprograma con cada URL nueva.
  useEffect(() => {
    if (!dashboard) return
    const espera = Math.max(ESPERA_MINIMA_MS, new Date(dashboard.expiraEn).getTime() - Date.now() - MARGEN_RENOVACION_MS)
    const temporizador = window.setTimeout(pedirUrl, espera)
    return () => window.clearTimeout(temporizador)
  }, [dashboard])

  function actualizar() {
    setCargando(true)
    pedirUrl()
  }

  // Las mismas cifras de la pestaña Estadísticas (y del dashboard), en un solo archivo.
  async function descargarEstadisticas() {
    setDescarga("Preparando el archivo…")
    try {
      descargarCsv(nombreArchivoEstadisticas(), estadisticasACsv(await adminApi.estadisticas()))
      setDescarga("")
    } catch (exception) {
      setDescarga(mensajeError(exception, "No fue posible descargar las estadísticas."))
    }
  }

  return (
    <section className="admin-section">
      <div className="dashboard-toolbar">
        <button className="button button-ghost button-small" onClick={actualizar} disabled={cargando}>{cargando ? "Actualizando…" : "Actualizar"}</button>
        <button className="button button-ghost button-small" onClick={descargarEstadisticas}>Descargar estadísticas (.csv)</button>
        {esAdmin && dashboard && <a href={inicioMetabase(dashboard.url)} target="_blank" rel="noopener noreferrer">Abrir en Metabase</a>}
        {descarga && <span role="status">{descarga}</span>}
      </div>
      {error && <p className="alert error" role="alert">{error}</p>}
      {!error && !dashboard && <div className="loading">Cargando el dashboard…</div>}
      {!error && dashboard && <iframe key={dashboard.url} src={dashboard.url} title="Dashboard de reportes de ViaCTG (Metabase)" className="dashboard-frame" />}
    </section>
  )
}
