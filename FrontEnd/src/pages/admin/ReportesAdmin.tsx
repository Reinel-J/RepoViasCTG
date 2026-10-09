import { useEffect, useState } from "react"
import { Link } from "react-router"
import * as adminApi from "../../api/admin"
import * as reportesApi from "../../api/reportes"
import type { EstadoReporte, HistorialEstadoResponse, PrioridadReporte, ReporteResponse } from "../../api/types"
import { Icon, StatusBadge } from "../../app/components/UI"
import { ACCION_ESTADO, ESTADOS, ETIQUETA_ESTADO, PRIORIDADES, SIGUIENTES, mensajeError } from "./estados"

const POR_PAGINA = 10

function sinClave<T>(registro: Record<string, T>, clave: string) {
  return Object.fromEntries(Object.entries(registro).filter(([actual]) => actual !== clave))
}

interface Borrador {
  prioridad?: PrioridadReporte
  comentario: string
}

export function ReportesAdmin({ estadoInicial }: { estadoInicial: string | null }) {
  const [reportes, setReportes] = useState<ReporteResponse[]>([])
  const [estado, setEstado] = useState<EstadoReporte | "">(ESTADOS.includes(estadoInicial as EstadoReporte) ? estadoInicial as EstadoReporte : "")
  const [busqueda, setBusqueda] = useState("")
  const [pagina, setPagina] = useState(1)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState("")
  const [aviso, setAviso] = useState("")
  const [procesandoId, setProcesandoId] = useState<string | null>(null)
  const [borradores, setBorradores] = useState<Record<string, Borrador>>({})
  const [historiales, setHistoriales] = useState<Record<string, HistorialEstadoResponse[]>>({})

  useEffect(() => {
    reportesApi.listar(estado || undefined)
      .then((lista) => setReportes([...lista].sort((a, b) => b.fechaCreacion.localeCompare(a.fechaCreacion))))
      .catch((exception) => setError(mensajeError(exception, "No fue posible cargar los reportes.")))
      .finally(() => setCargando(false))
  }, [estado])

  function cambiarFiltro(nuevo: EstadoReporte | "") {
    setCargando(true)
    setError("")
    setPagina(1)
    setEstado(nuevo)
  }

  function borrador(id: string): Borrador {
    return borradores[id] ?? { comentario: "" }
  }

  function editarBorrador(id: string, cambios: Partial<Borrador>) {
    setBorradores((actuales) => ({ ...actuales, [id]: { ...borrador(id), ...cambios } }))
  }

  async function aplicar(reporte: ReporteResponse, nuevoEstado: EstadoReporte) {
    if (nuevoEstado === "RECHAZADO" && !window.confirm("¿Rechazar este reporte? Esta acción no se puede deshacer.")) return
    const { prioridad, comentario } = borrador(reporte.id)
    setProcesandoId(reporte.id)
    setError("")
    setAviso("")
    try {
      const actualizado = await adminApi.cambiarEstado(reporte.id, nuevoEstado, comentario.trim() || undefined, prioridad)
      setReportes((actuales) => estado && actualizado.estado !== estado
        ? actuales.filter((item) => item.id !== reporte.id)
        : actuales.map((item) => (item.id === reporte.id ? actualizado : item)))
      setBorradores((actuales) => ({ ...actuales, [reporte.id]: { comentario: "" } }))
      setHistoriales((actuales) => sinClave(actuales, reporte.id))
      setAviso(`Reporte movido a «${ETIQUETA_ESTADO[nuevoEstado]}». Se notificó a su autor.`)
    } catch (exception) {
      setError(mensajeError(exception, "No fue posible actualizar el estado."))
    } finally {
      setProcesandoId(null)
    }
  }

  async function alternarHistorial(id: string) {
    if (historiales[id]) {
      setHistoriales((actuales) => sinClave(actuales, id))
      return
    }
    try {
      const lista = await adminApi.historial(id)
      setHistoriales((actuales) => ({ ...actuales, [id]: lista }))
    } catch (exception) {
      setError(mensajeError(exception, "No fue posible cargar el historial."))
    }
  }

  const texto = busqueda.trim().toLowerCase()
  const filtrados = texto
    ? reportes.filter((reporte) => [reporte.descripcion, reporte.categoriaId, reporte.direccionOsm ?? ""].some((campo) => campo.toLowerCase().includes(texto)))
    : reportes
  const totalPaginas = Math.max(1, Math.ceil(filtrados.length / POR_PAGINA))
  const paginaActual = Math.min(pagina, totalPaginas)
  const visibles = filtrados.slice((paginaActual - 1) * POR_PAGINA, paginaActual * POR_PAGINA)

  return (
    <section className="admin-section">
      <div className="toolbar">
        <label>Estado
          <select value={estado} onChange={(event) => cambiarFiltro(event.target.value as EstadoReporte | "")}>
            <option value="">Todos</option>
            {ESTADOS.map((opcion) => <option key={opcion} value={opcion}>{ETIQUETA_ESTADO[opcion]}</option>)}
          </select>
        </label>
        <label>Buscar
          <input type="search" value={busqueda} onChange={(event) => { setBusqueda(event.target.value); setPagina(1) }} placeholder="Descripción, categoría o dirección" />
        </label>
        <span className="muted">{filtrados.length} reportes</span>
      </div>
      {cargando && <div className="loading">Cargando reportes…</div>}
      {error && <p className="alert error" role="alert">{error}</p>}
      {aviso && <p className="alert success" role="status">{aviso}</p>}
      {!cargando && filtrados.length === 0 && <div className="empty-state"><Icon name="check" className="empty-icon" /><h2>No hay reportes</h2><p>No hay reportes que coincidan con este filtro.</p></div>}
      <div className="admin-list">
        {visibles.map((reporte) => {
          const siguientes = SIGUIENTES[reporte.estado]
          const ocupado = procesandoId === reporte.id
          return (
            <article key={reporte.id} className="admin-report">
              <div className="admin-report-main">
                <div className="admin-report-tags">
                  <StatusBadge status={reporte.estado} />
                  <span className={`priority priority-${reporte.prioridad.toLowerCase()}`}>{reporte.prioridad}</span>
                  <span className="admin-chip">{reporte.categoriaId}</span>
                </div>
                <h2><Link to={`/reportes/${reporte.id}`}>{reporte.descripcion}</Link></h2>
                <p>{reporte.direccionOsm ?? `${reporte.latitud.toFixed(5)}, ${reporte.longitud.toFixed(5)}`} · {new Date(reporte.fechaCreacion).toLocaleDateString("es-CO")} · {reporte.fotos.length} foto(s)</p>
                <button className="text-button" onClick={() => alternarHistorial(reporte.id)}>{historiales[reporte.id] ? "Ocultar historial" : "Ver historial"}</button>
                {historiales[reporte.id] && (
                  <ol className="admin-history">
                    {historiales[reporte.id].length === 0 && <li>Sin cambios de estado todavía.</li>}
                    {historiales[reporte.id].map((cambio) => (
                      <li key={cambio.id}>
                        <strong>{ETIQUETA_ESTADO[cambio.estadoAnterior]} → {ETIQUETA_ESTADO[cambio.estadoNuevo]}</strong>
                        <span>{new Date(cambio.fecha).toLocaleString("es-CO")}</span>
                        {cambio.comentarioAdmin && <em>«{cambio.comentarioAdmin}»</em>}
                      </li>
                    ))}
                  </ol>
                )}
              </div>
              {siguientes.length > 0 ? (
                <div className="admin-report-actions">
                  <label>Prioridad
                    <select value={borrador(reporte.id).prioridad ?? reporte.prioridad} disabled={ocupado} onChange={(event) => editarBorrador(reporte.id, { prioridad: event.target.value as PrioridadReporte })}>
                      {PRIORIDADES.map((opcion) => <option key={opcion} value={opcion}>{opcion.charAt(0) + opcion.slice(1).toLowerCase()}</option>)}
                    </select>
                  </label>
                  <label>Comentario para el historial
                    <input maxLength={500} value={borrador(reporte.id).comentario} disabled={ocupado} onChange={(event) => editarBorrador(reporte.id, { comentario: event.target.value })} placeholder="Opcional" />
                  </label>
                  <div className="admin-buttons">
                    {siguientes.map((siguiente) => (
                      <button key={siguiente} className={siguiente === "RECHAZADO" ? "button button-ghost button-small" : "button button-dark button-small"} disabled={ocupado} onClick={() => aplicar(reporte, siguiente)}>
                        {ACCION_ESTADO[siguiente]}
                      </button>
                    ))}
                  </div>
                </div>
              ) : <p className="muted admin-final">Estado final</p>}
            </article>
          )
        })}
      </div>
      {totalPaginas > 1 && (
        <nav className="pagination" aria-label="Paginación">
          <button className="button button-ghost button-small" disabled={paginaActual === 1} onClick={() => setPagina(paginaActual - 1)}>← Anterior</button>
          <span>Página {paginaActual} de {totalPaginas}</span>
          <button className="button button-ghost button-small" disabled={paginaActual === totalPaginas} onClick={() => setPagina(paginaActual + 1)}>Siguiente →</button>
        </nav>
      )}
    </section>
  )
}
