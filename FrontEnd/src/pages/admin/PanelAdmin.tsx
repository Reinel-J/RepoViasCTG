import { useSearchParams } from "react-router"
import { useState } from "react"
import * as adminApi from "../../api/admin"
import { useAuth } from "../../context/useAuth"
import { plural } from "../../utils/texto"
import { BarriosAdmin } from "./BarriosAdmin"
import { CategoriasAdmin } from "./CategoriasAdmin"
import { EstadisticasAdmin } from "./EstadisticasAdmin"
import { ReportesAdmin } from "./ReportesAdmin"
import { ResumenAdmin } from "./ResumenAdmin"
import { UsuariosAdmin } from "./UsuariosAdmin"

type Seccion = "resumen" | "reportes" | "estadisticas" | "usuarios" | "barrios" | "categorias"

const SECCIONES: { id: Seccion; etiqueta: string; soloAdmin: boolean }[] = [
  { id: "resumen", etiqueta: "Resumen", soloAdmin: false },
  { id: "reportes", etiqueta: "Reportes", soloAdmin: false },
  { id: "estadisticas", etiqueta: "Estadísticas", soloAdmin: false },
  { id: "usuarios", etiqueta: "Usuarios", soloAdmin: true },
  { id: "barrios", etiqueta: "Barrios", soloAdmin: true },
  { id: "categorias", etiqueta: "Categorías", soloAdmin: true },
]

export function PanelAdmin() {
  const { session } = useAuth()
  const [params, setParams] = useSearchParams()
  const esAdmin = session?.rol === "ADMIN"
  const disponibles = SECCIONES.filter((seccion) => esAdmin || !seccion.soloAdmin)
  const pedida = params.get("seccion") as Seccion | null
  const actual = disponibles.find((seccion) => seccion.id === pedida)?.id ?? "resumen"
  const [recordatorio, setRecordatorio] = useState("")

  // El resultado de la tarea diaria es de la pestaña Reportes: se borra al cambiar de pestaña.
  function irA(nuevos: Record<string, string>) {
    setRecordatorio("")
    setParams(nuevos)
  }

  async function enviarRecordatorios() {
    setRecordatorio("Enviando recordatorios…")
    try {
      const { reportesRecordados, reportesCerrados } = await adminApi.enviarRecordatorios()
      setRecordatorio(`${plural(reportesRecordados, "recordatorio enviado", "recordatorios enviados")} · ${plural(reportesCerrados, "reporte cerrado", "reportes cerrados")} por la comunidad.`)
    } catch (exception) {
      setRecordatorio(exception instanceof Error ? exception.message : "No fue posible enviar los recordatorios.")
    }
  }

  return (
    <div className="page-shell section internal-page">
      <div className="page-heading">
        <div>
          <span className="eyebrow">Gestión interna · {esAdmin ? "Administrador" : "Moderador"}</span>
          <h1>Panel de administración</h1>
          <p>Modera los reportes de la comunidad{esAdmin ? ", gestiona usuarios y mantén los catálogos de la ciudad" : ""}.</p>
        </div>
      </div>
      <nav className="admin-tabs" aria-label="Secciones del panel">
        {disponibles.map((seccion) => (
          <button
            key={seccion.id}
            className={seccion.id === actual ? "is-active" : ""}
            aria-current={seccion.id === actual ? "page" : undefined}
            onClick={() => irA({ seccion: seccion.id })}
          >
            {seccion.etiqueta}
          </button>
        ))}
      </nav>
      {/* La tarea diaria (recordatorios y cierres por la comunidad) solo tiene sentido junto a los reportes. */}
      {esAdmin && actual === "reportes" && <div className="admin-utility"><button className="button button-ghost button-small" onClick={enviarRecordatorios}>Ejecutar tarea diaria</button>{recordatorio && <span role="status">{recordatorio}</span>}</div>}
      {actual === "resumen" && <ResumenAdmin onVerReportes={(estado) => irA(estado ? { seccion: "reportes", estado } : { seccion: "reportes" })} />}
      {actual === "reportes" && <ReportesAdmin estadoInicial={params.get("estado")} />}
      {actual === "estadisticas" && <EstadisticasAdmin />}
      {actual === "usuarios" && <UsuariosAdmin usuarioActualId={session?.usuarioId} />}
      {actual === "barrios" && <BarriosAdmin />}
      {actual === "categorias" && <CategoriasAdmin />}
    </div>
  )
}
