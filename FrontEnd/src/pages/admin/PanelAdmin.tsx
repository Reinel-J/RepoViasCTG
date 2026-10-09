import { useSearchParams } from "react-router"
import { useAuth } from "../../context/useAuth"
import { BarriosAdmin } from "./BarriosAdmin"
import { CategoriasAdmin } from "./CategoriasAdmin"
import { ReportesAdmin } from "./ReportesAdmin"
import { ResumenAdmin } from "./ResumenAdmin"
import { UsuariosAdmin } from "./UsuariosAdmin"

type Seccion = "resumen" | "reportes" | "usuarios" | "barrios" | "categorias"

const SECCIONES: { id: Seccion; etiqueta: string; soloAdmin: boolean }[] = [
  { id: "resumen", etiqueta: "Resumen", soloAdmin: false },
  { id: "reportes", etiqueta: "Reportes", soloAdmin: false },
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

  return (
    <main className="page-shell section internal-page">
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
            onClick={() => setParams({ seccion: seccion.id })}
          >
            {seccion.etiqueta}
          </button>
        ))}
      </nav>
      {actual === "resumen" && <ResumenAdmin onVerReportes={(estado) => setParams(estado ? { seccion: "reportes", estado } : { seccion: "reportes" })} />}
      {actual === "reportes" && <ReportesAdmin estadoInicial={params.get("estado")} />}
      {actual === "usuarios" && <UsuariosAdmin usuarioActualId={session?.usuarioId} />}
      {actual === "barrios" && <BarriosAdmin />}
      {actual === "categorias" && <CategoriasAdmin />}
    </main>
  )
}
