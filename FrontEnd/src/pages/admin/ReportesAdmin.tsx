import { useEffect, useState } from "react"
import { Link } from "react-router"
import * as adminApi from "../../api/admin"
import * as reportesApi from "../../api/reportes"
import type { EstadoReporte, HistorialEstadoResponse, PaginaResponse, PrioridadReporte, ReporteResponse, SeguimientoResumenResponse } from "../../api/types"
import { Icon, StatusBadge } from "../../app/components/UI"
import { Dialogo } from "../../components/Dialogo"
import { useDialogo } from "../../components/useDialogo"
import { plural } from "../../utils/texto"
import { useValorDiferido } from "../../utils/useValorDiferido"
import { ACCION_ESTADO, ESTADOS, ETIQUETA_ESTADO, PRIORIDADES, SIGUIENTES, mensajeError } from "./estados"
import { ordenar, type OrdenReportes } from "./ordenReportes"

const POR_PAGINA = 10
const CLAVE_ORDEN = "viactg.admin.reportes.orden"

type CargaPagina = { clave: string } & ({ pagina: PaginaResponse<ReporteResponse> } | { error: string })

function sinClave<T>(registro: Record<string, T>, clave: string) {
  return Object.fromEntries(Object.entries(registro).filter(([actual]) => actual !== clave))
}

// Resultado de una acción sobre un reporte, mostrado dentro de su tarjeta: arriba de la lista quedaba lejos del botón.
interface Resultado {
  id: string
  tipo: "error" | "exito"
  texto: string
}

interface Borrador {
  prioridad?: PrioridadReporte
  comentario: string
}

// Resume los votos "¿ya lo arreglaron?" en una etiqueta corta para el moderador.
function SenalComunidad({ seguimiento }: { seguimiento?: SeguimientoResumenResponse }) {
  if (!seguimiento) return null
  const total = seguimiento.arreglado + seguimiento.sigue
  if (total === 0) return null
  const mayoriaArreglado = seguimiento.arreglado > seguimiento.sigue
  const texto = mayoriaArreglado
    ? `Comunidad: ya arreglado (${seguimiento.arreglado} de ${total})`
    : `Comunidad: sigue igual (${seguimiento.sigue} de ${total})`
  return <span className={`admin-chip followup-signal${mayoriaArreglado ? "" : " is-mixed"}`} title={`Última respuesta: ${seguimiento.ultimaRespuesta ? new Date(seguimiento.ultimaRespuesta).toLocaleString("es-CO") : "—"}`}>{texto}</span>
}

export function ReportesAdmin({ estadoInicial }: { estadoInicial: string | null }) {
  const [estado, setEstado] = useState<EstadoReporte | "">(ESTADOS.includes(estadoInicial as EstadoReporte) ? estadoInicial as EstadoReporte : "")
  const [busqueda, setBusqueda] = useState("")
  const [pagina, setPagina] = useState(1)
  const [orden, setOrden] = useState<OrdenReportes>(() => {
    try {
      const guardado = localStorage.getItem(CLAVE_ORDEN)
      return guardado === "prioridad" || guardado === "antiguos" || guardado === "recientes" ? guardado : "recientes"
    } catch {
      return "recientes"
    }
  })
  // Sube para volver a pedir la misma página (por ejemplo, cuando un reporte sale del filtro y hay que rellenarla).
  const [version, setVersion] = useState(0)
  const [aviso, setAviso] = useState("")
  const [resultado, setResultado] = useState<Resultado | null>(null)
  const { dialogo, confirmar } = useDialogo()
  const [procesandoId, setProcesandoId] = useState<string | null>(null)
  const [borradores, setBorradores] = useState<Record<string, Borrador>>({})
  const [historiales, setHistoriales] = useState<Record<string, HistorialEstadoResponse[]>>({})
  const [seguimientos, setSeguimientos] = useState<Record<string, SeguimientoResumenResponse>>({})

  // Si falla, el panel sigue funcionando: solo no se muestran las señales de la comunidad.
  useEffect(() => {
    adminApi.seguimientos()
      .then((lista) => setSeguimientos(Object.fromEntries(lista.map((seguimiento) => [seguimiento.reporteId, seguimiento]))))
      .catch(() => setSeguimientos({}))
  }, [])

  // La búsqueda y la paginación las resuelve el servidor; al servidor se le pregunta cuando se deja de escribir.
  const textoBuscado = useValorDiferido(busqueda.trim())
  const filtro = JSON.stringify({ estado: estado || undefined, q: textoBuscado, pagina: pagina - 1, tamano: POR_PAGINA })
  const clave = `${filtro}#${version}`
  const [carga, setCarga] = useState<CargaPagina | null>(null)
  useEffect(() => {
    let vigente = true
    reportesApi.listar(JSON.parse(filtro))
      .then((respuesta) => { if (vigente) setCarga({ clave, pagina: respuesta }) })
      .catch((exception) => { if (vigente) setCarga({ clave, error: mensajeError(exception, "No fue posible cargar los reportes.") }) })
    return () => { vigente = false }
  }, [clave, filtro])
  const cargando = carga?.clave !== clave
  const datos = carga && "pagina" in carga ? carga.pagina : null
  const error = !cargando && carga && "error" in carga ? carga.error : ""
  const visibles = ordenar(datos?.contenido ?? [], orden)
  const total = datos?.totalElementos ?? 0
  const totalPaginas = Math.max(1, datos?.totalPaginas ?? 1)

  function reemplazar(actualizado: ReporteResponse) {
    setCarga((actual) => actual && "pagina" in actual
      ? { ...actual, pagina: { ...actual.pagina, contenido: actual.pagina.contenido.map((item) => (item.id === actualizado.id ? actualizado : item)) } }
      : actual)
  }

  function cambiarFiltro(nuevo: EstadoReporte | "") {
    setAviso("")
    setPagina(1)
    setEstado(nuevo)
  }

  function cambiarOrden(nuevo: OrdenReportes) {
    setOrden(nuevo)
    try {
      localStorage.setItem(CLAVE_ORDEN, nuevo)
    } catch {
      // Algunos navegadores bloquean el almacenamiento; el orden sigue funcionando durante esta sesión.
    }
  }

  function borrador(id: string): Borrador {
    return borradores[id] ?? { comentario: "" }
  }

  function editarBorrador(id: string, cambios: Partial<Borrador>) {
    setBorradores((actuales) => ({ ...actuales, [id]: { ...borrador(id), ...cambios } }))
  }

  async function aplicar(reporte: ReporteResponse, nuevoEstado: EstadoReporte) {
    if (nuevoEstado === "RECHAZADO" && !await confirmar({ titulo: "¿Rechazar este reporte?", mensaje: "Es un estado final: el reporte ya no se podrá mover a otro estado. Se notificará a su autor.", aceptar: "Rechazar reporte", peligro: true })) return
    const { prioridad, comentario } = borrador(reporte.id)
    setProcesandoId(reporte.id)
    setAviso("")
    setResultado(null)
    try {
      const actualizado = await adminApi.cambiarEstado(reporte.id, nuevoEstado, comentario.trim() || undefined, prioridad)
      const saleDelFiltro = Boolean(estado) && actualizado.estado !== estado
      // Si sale del filtro se vuelve a pedir la página para rellenarla; si era el último de una página, la anterior.
      if (!saleDelFiltro) reemplazar(actualizado)
      else if (visibles.length === 1 && pagina > 1) setPagina(pagina - 1)
      else setVersion((actual) => actual + 1)
      setBorradores((actuales) => ({ ...actuales, [reporte.id]: { comentario: "" } }))
      setHistoriales((actuales) => sinClave(actuales, reporte.id))
      const texto = `Reporte movido a «${ETIQUETA_ESTADO[nuevoEstado]}». Se notificó a su autor.`
      // Si el reporte ya no cumple el filtro desaparece de la lista: el aviso va arriba porque no hay tarjeta.
      if (saleDelFiltro) setAviso(texto)
      else setResultado({ id: reporte.id, tipo: "exito", texto })
    } catch (exception) {
      setResultado({ id: reporte.id, tipo: "error", texto: mensajeError(exception, "No fue posible actualizar el estado.") })
    } finally {
      setProcesandoId(null)
    }
  }

  async function mantenerAbierto(reporte: ReporteResponse) {
    setProcesandoId(reporte.id)
    setAviso("")
    setResultado(null)
    try {
      const seguimiento = await adminApi.mantenerAbierto(reporte.id)
      setSeguimientos((actuales) => ({ ...actuales, [reporte.id]: seguimiento }))
      setResultado({ id: reporte.id, tipo: "exito", texto: "Propuesta de cierre descartada: desde ahora solo cuentan los votos nuevos." })
    } catch (exception) {
      setResultado({ id: reporte.id, tipo: "error", texto: mensajeError(exception, "No fue posible mantener abierto el reporte.") })
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
      setResultado({ id, tipo: "error", texto: mensajeError(exception, "No fue posible cargar el historial.") })
    }
  }

  return (
    <section className="admin-section">
      <Dialogo dialogo={dialogo} />
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
        <label>Ordenar por
          <select value={orden} onChange={(event) => cambiarOrden(event.target.value as OrdenReportes)}>
            <option value="prioridad">Prioridad</option>
            <option value="antiguos">Más antiguos</option>
            <option value="recientes">Más recientes</option>
          </select>
        </label>
        <span className="muted">{cargando ? "Cargando…" : plural(total, "reporte")}</span>
      </div>
      {cargando && !datos && <div className="loading">Cargando reportes…</div>}
      {error && <p className="alert error" role="alert">{error}</p>}
      {aviso && <p className="alert success" role="status">{aviso}</p>}
      {!cargando && !error && visibles.length === 0 && <div className="empty-state"><Icon name="check" className="empty-icon" /><h2>No hay reportes</h2><p>No hay reportes que coincidan con este filtro.</p></div>}
      <div className={cargando ? "admin-list is-loading" : "admin-list"} aria-busy={cargando}>
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
                  {siguientes.length > 0 && <SenalComunidad seguimiento={seguimientos[reporte.id]} />}
                </div>
                <h2><Link to={`/reportes/${reporte.id}`}>{reporte.descripcion}</Link></h2>
                <p>{reporte.direccionOsm ?? `${reporte.latitud.toFixed(5)}, ${reporte.longitud.toFixed(5)}`} · {new Date(reporte.fechaCreacion).toLocaleDateString("es-CO")} · {plural(reporte.fotos.length, "foto")}</p>
                {reporte.estado === "EN_PROCESO" && seguimientos[reporte.id]?.cierrePrevisto && (
                  <div className="community-closure">
                    <span>La comunidad dice que ya está arreglado: se cerrará solo el <strong>{new Date(seguimientos[reporte.id].cierrePrevisto!).toLocaleString("es-CO", { dateStyle: "long", timeStyle: "short" })}</strong> si nadie lo desmiente.</span>
                    <button className="button button-ghost button-small" disabled={ocupado} onClick={() => mantenerAbierto(reporte)}>Mantener abierto</button>
                  </div>
                )}
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
              {resultado?.id === reporte.id && <p className={`alert ${resultado.tipo === "error" ? "error" : "success"} admin-report-resultado`} role={resultado.tipo === "error" ? "alert" : "status"}>{resultado.texto}</p>}
            </article>
          )
        })}
      </div>
      {totalPaginas > 1 && (
        <nav className="pagination" aria-label="Paginación">
          <button className="button button-ghost button-small" disabled={pagina <= 1} onClick={() => setPagina(pagina - 1)}>← Anterior</button>
          <span>Página {pagina} de {totalPaginas}</span>
          <button className="button button-ghost button-small" disabled={pagina >= totalPaginas} onClick={() => setPagina(pagina + 1)}>Siguiente →</button>
        </nav>
      )}
    </section>
  )
}
