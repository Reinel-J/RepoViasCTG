import { useEffect, useState } from "react"
import * as adminApi from "../../api/admin"
import { fechaLocalAISO } from "../../api/admin"
import type { RegistroAuditoriaResponse } from "../../api/types"
import { mensajeError } from "./estados"

const ETIQUETA: Record<string, string> = {
  USUARIO_ROL_CAMBIADO: "Rol cambiado",
  USUARIO_ESTADO_CAMBIADO: "Estado de cuenta cambiado",
  REPORTE_ESTADO_CAMBIADO: "Estado de reporte cambiado",
  REPORTE_MANTENIDO_ABIERTO: "Reporte mantenido abierto",
  REPORTE_CERRADO_POR_COMUNIDAD: "Reporte cerrado por la comunidad",
  CATEGORIA_CREADA: "Categoría creada",
  CATEGORIA_EDITADA: "Categoría editada",
  CATEGORIA_ACTIVADA: "Categoría activada",
  CATEGORIA_DESACTIVADA: "Categoría desactivada",
  BARRIO_CREADO: "Barrio creado",
  BARRIO_EDITADO: "Barrio editado",
  BARRIO_ELIMINADO: "Barrio eliminado",
  BARRIO_CALLE_AGREGADA: "Calle agregada",
  BARRIO_CALLE_ELIMINADA: "Calle eliminada",
  SEGUIMIENTO_TAREA_EJECUTADA: "Tarea diaria ejecutada",
}

const ACCIONES = Object.entries(ETIQUETA)

function TablaAuditoria({ registros }: { registros: RegistroAuditoriaResponse[] }) {
  return (
    <div className="admin-table-wrap">
      <table className="admin-table">
        <thead>
          <tr>
            <th>Fecha</th>
            <th>Quién</th>
            <th>Acción</th>
            <th>Sobre qué</th>
            <th>Detalle</th>
          </tr>
        </thead>
        <tbody>
          {registros.map((registro) => (
            <tr key={registro.id}>
              <td>
                {new Date(registro.fecha).toLocaleString("es-CO", {
                  timeZone: "America/Bogota",
                })}
              </td>
              <td>{registro.actorNombre}</td>
              <td>{ETIQUETA[registro.accion] ?? registro.accion}</td>
              <td>
                {registro.entidad} · {registro.entidadId}
              </td>
              <td>{registro.detalle}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function AuditoriaAdmin() {
  const [entidad, setEntidad] = useState("")
  const [accion, setAccion] = useState("")
  const [desde, setDesde] = useState("")
  const [hasta, setHasta] = useState("")
  const [pagina, setPagina] = useState(0)
  const [datos, setDatos] = useState<{
    contenido: RegistroAuditoriaResponse[]
    totalPaginas: number
  } | null>(null)
  const [error, setError] = useState("")

  useEffect(() => {
    let vigente = true

    adminApi
      .auditoria({
        entidad: entidad || undefined,
        accion: accion || undefined,
        desde: fechaLocalAISO(desde),
        hasta: fechaLocalAISO(hasta),
        pagina,
        tamano: 50,
      })
      .then((respuesta) => {
        if (vigente) {
          setDatos(respuesta)
          setError("")
        }
      })
      .catch((exception) => {
        if (vigente) {
          setError(mensajeError(exception, "No fue posible cargar la auditoría."))
        }
      })

    return () => {
      vigente = false
    }
  }, [entidad, accion, desde, hasta, pagina])

  function filtro(cambio: () => void) {
    cambio()
    setPagina(0)
  }

  return (
    <section className="admin-section">
      <div className="toolbar">
        <label>
          Entidad
          <select
            value={entidad}
            onChange={(event) => filtro(() => setEntidad(event.target.value))}
          >
            <option value="">Todas</option>
            <option>USUARIO</option>
            <option>REPORTE</option>
            <option>CATEGORIA</option>
            <option>BARRIO</option>
            <option>SISTEMA</option>
          </select>
        </label>
        <label>
          Acción
          <select
            value={accion}
            onChange={(event) => filtro(() => setAccion(event.target.value))}
          >
            <option value="">Todas</option>
            {ACCIONES.map(([valor, etiqueta]) => (
              <option key={valor} value={valor}>
                {etiqueta}
              </option>
            ))}
          </select>
        </label>
        <label>
          Desde
          <input
            type="datetime-local"
            value={desde}
            onChange={(event) => filtro(() => setDesde(event.target.value))}
          />
        </label>
        <label>
          Hasta
          <input
            type="datetime-local"
            value={hasta}
            onChange={(event) => filtro(() => setHasta(event.target.value))}
          />
        </label>
      </div>

      {error && <p className="alert error" role="alert">{error}</p>}
      {!datos && !error && <div className="loading">Cargando auditoría…</div>}

      {datos && (
        <>
          {datos.contenido.length === 0 ? (
            <div className="empty-state">
              <h2>Sin registros</h2>
              <p>No hay acciones que coincidan con los filtros.</p>
            </div>
          ) : (
            <TablaAuditoria registros={datos.contenido} />
          )}

          {datos.totalPaginas > 1 && (
            <nav className="pagination">
              <button
                className="button button-ghost button-small"
                disabled={pagina === 0}
                onClick={() => setPagina(pagina - 1)}
              >
                ← Anterior
              </button>
              <span>
                Página {pagina + 1} de {datos.totalPaginas}
              </span>
              <button
                className="button button-ghost button-small"
                disabled={pagina + 1 >= datos.totalPaginas}
                onClick={() => setPagina(pagina + 1)}
              >
                Siguiente →
              </button>
            </nav>
          )}
        </>
      )}
    </section>
  )
}
