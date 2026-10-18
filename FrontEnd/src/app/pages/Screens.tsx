import { useEffect, useMemo, useRef, useState, type ChangeEvent, type FormEvent, type ReactNode } from "react"
import { NavLink, useLocation, useNavigate, useParams, useSearchParams, type NavigateFunction } from "react-router"
import { cargarMisReportes, cargarReporte, cargarReportes, fotoReporte, fotosReporte, textoEstado, type Reporte } from "../../api/reporteVista"
import * as authApi from "../../api/auth"
import * as categoriasApi from "../../api/categorias"
import * as comentariosApi from "../../api/comentarios"
import * as confirmacionesApi from "../../api/confirmaciones"
import * as geocodingApi from "../../api/geocoding"
import * as notificacionesApi from "../../api/notificaciones"
import * as reportesApi from "../../api/reportes"
import * as seguimientoApi from "../../api/seguimiento"
import * as usuariosApi from "../../api/usuarios"
import type { ComentarioResponse, ConfirmacionResponse, EstadoReporte, NotificacionResponse, PosibleDuplicadoResponse, ReporteResponse, SeguimientoResumenResponse, UsuarioResponse } from "../../api/types"
import { useAuth } from "../../context/useAuth"
import { CampanaNotificaciones } from "../../components/CampanaNotificaciones"
import { avisarCambioNotificaciones } from "../../components/notificacionesEventos"
import { COLOR_ESTADO } from "../../components/mapaBase"
import { MapaReportes, MapaSeleccionPunto } from "../../components/MapasDiferidos"
import { PanelAdmin } from "../../pages/admin/PanelAdmin"
import { prepararFoto } from "../../utils/imagenes"
import { rutaSegura } from "../../utils/navegacion"
import { coincideBusqueda, coincideFiltro, valoresUnicos } from "../../utils/texto"

type IconName = "arrow" | "bell" | "camera" | "check" | "clock" | "filter" | "location" | "map" | "menu" | "plus" | "search" | "user"

function Icon({ name, size = 20 }: { name: IconName; size?: number }) {
  const paths: Record<IconName, ReactNode> = {
    arrow: <><path d="M5 12h14" /><path d="m13 6 6 6-6 6" /></>,
    bell: <><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9" /><path d="M10 21h4" /></>,
    camera: <><path d="M14.5 5 13 3h-2L9.5 5H5a2 2 0 0 0-2 2v11h18V7a2 2 0 0 0-2-2Z" /><circle cx="12" cy="12" r="3.5" /></>,
    check: <path d="m5 12 4 4L19 6" />,
    clock: <><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></>,
    filter: <path d="M4 6h16M7 12h10M10 18h4" />,
    location: <><path d="M20 10c0 5-8 12-8 12S4 15 4 10a8 8 0 1 1 16 0Z" /><circle cx="12" cy="10" r="2.5" /></>,
    map: <><path d="m3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3Z" /><path d="M9 3v15M15 6v15" /></>,
    menu: <><path d="M4 7h16M4 12h16M4 17h16" /></>,
    plus: <><path d="M12 5v14M5 12h14" /></>,
    search: <><circle cx="11" cy="11" r="7" /><path d="m20 20-4-4" /></>,
    user: <><circle cx="12" cy="8" r="4" /><path d="M4 21c1-5 4-7 8-7s7 2 8 7" /></>,
  }
  return <svg aria-hidden="true" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">{paths[name]}</svg>
}

function Logo() {
  return (
    <NavLink className="brand" to="/">
      <span className="brand-mark"><span className="brand-road" /></span>
      <span className="brand-copy"><strong>Vía<span>CTG</span></strong><small>Cartagena se mueve</small></span>
    </NavLink>
  )
}

function mensajeDe(exception: unknown, porDefecto: string) {
  return exception instanceof Error ? exception.message : porDefecto
}

// Lleva al login recordando a dónde volver después.
function irALogin(navigate: NavigateFunction, desde: string) {
  navigate("/login", { state: { desde } })
}

function PortalShell({ children, title, eyebrow, action = true }: { children: ReactNode; title?: string; eyebrow?: string; action?: boolean }) {
  const [menu, setMenu] = useState(false)
  const { session, logout } = useAuth()
  return (
    <div className="portal-shell">
      <div className="top-ribbon"><span>Cartagena de Indias</span><span className="ribbon-dot" /><span>La calle también es nuestra</span></div>
      <header className="site-header portal-header">
        <Logo />
        <nav id="menu-principal" className={menu ? "main-nav open" : "main-nav"} aria-label="Navegación principal">
          <NavLink to="/">Inicio</NavLink>
          <NavLink to="/reportes">Reportes</NavLink>
          <NavLink to="/mapa">Mapa ciudadano</NavLink>
          {session && <NavLink to="/mis-reportes">Mis reportes</NavLink>}
          {(session?.rol === "ADMIN" || session?.rol === "MODERADOR") && <NavLink to="/admin">Panel de gestión</NavLink>}
        </nav>
        <div className="header-actions">
          {session && <CampanaNotificaciones><Icon name="bell" /></CampanaNotificaciones>}
          {action && <NavLink className="login-button nav-action" to="/reportes/nuevo"><Icon name="plus" size={16} /> Reportar</NavLink>}
          {session ? <button className="session-button" onClick={logout}>{session.nombre} · Salir</button> : <NavLink className="session-button" to="/login">Iniciar sesión</NavLink>}
          <button className="menu-button" onClick={() => setMenu(!menu)} aria-label={menu ? "Cerrar menú" : "Abrir menú"} aria-expanded={menu} aria-controls="menu-principal"><Icon name="menu" /></button>
        </div>
      </header>
      {(title || eyebrow) && <div className="page-hero"><span>{eyebrow}</span><h1>{title}</h1><div className="page-hero-sun" /></div>}
      <main className="portal-main">{children}</main>
      <footer className="portal-footer"><Logo /><p>Una plataforma ciudadana para cuidar las calles que nos conectan.</p><span>© 2026 VíaCTG</span></footer>
    </div>
  )
}

function useReportes() {
  const [datos, setDatos] = useState<Reporte[]>([])
  const [conectado, setConectado] = useState(false)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState("")
  useEffect(() => {
    cargarReportes().then((respuesta) => {
      setDatos(respuesta)
      setConectado(true)
    }).catch((exception) => {
      setConectado(false)
      setError(mensajeDe(exception, "No fue posible cargar los reportes."))
    }).finally(() => setCargando(false))
  }, [])
  return { datos, conectado, cargando, error }
}

function ReportCard({ reporte, editable = false }: { reporte: Reporte; editable?: boolean }) {
  const foto = fotoReporte(reporte)
  return (
    <article className="report-card">
      <div className="card-image-wrap">
        {foto ? <img src={foto} alt={`Evidencia del reporte ${reporte.categoria}`} loading="lazy" /> : <div className="no-photo"><Icon name="camera" size={26} /><span>Sin fotografía</span></div>}
        <span className={`category-chip ${reporte.color}`}>{reporte.categoria}</span>
      </div>
      <div className="card-content">
        <div className="location-line"><Icon name="location" size={17} /><h3>{reporte.lugar}</h3></div>
        <p className="card-description">{reporte.descripcion}</p>
        <div className="card-meta">
          <span><Icon name="clock" size={15} />{reporte.fecha}</span>
          <span className={`status ${reporte.estadoClase}`}><i />{reporte.estado}</span>
        </div>
        <NavLink to={`/reportes/${reporte.id}`}>Ver reporte <Icon name="arrow" size={17} /></NavLink>
        {editable && reporte.estadoRaw === "PENDIENTE" && <NavLink className="edit-report-link" to={`/reportes/${reporte.id}/editar`}>Editar este reporte</NavLink>}
      </div>
    </article>
  )
}

export function Reportes() {
  const { datos, conectado, cargando, error } = useReportes()
  const [estado, setEstado] = useState("TODOS")
  const [categoria, setCategoria] = useState("")
  const [barrio, setBarrio] = useState("")
  const [busqueda, setBusqueda] = useState("")
  const categorias = useMemo(() => valoresUnicos(datos.map((reporte) => reporte.categoria)), [datos])
  const barrios = useMemo(() => valoresUnicos(datos.map((reporte) => reporte.barrio)), [datos])
  // La búsqueda no distingue mayúsculas ni tildes ("inundacion" encuentra "Inundación").
  const visibles = useMemo(() => datos.filter((reporte) =>
    (estado === "TODOS" || reporte.estadoRaw === estado) &&
    coincideFiltro(categoria, reporte.categoria) &&
    coincideFiltro(barrio, reporte.barrio) &&
    coincideBusqueda(busqueda, reporte.categoria, reporte.lugar, reporte.barrio, reporte.descripcion)
  ), [datos, estado, categoria, barrio, busqueda])

  return (
    <PortalShell eyebrow="La voz de los barrios" title="Reportes ciudadanos">
      <section className="listing-section">
        <div className="listing-toolbar">
          <div className="search-field"><Icon name="search" size={18} /><input type="search" aria-label="Buscar reportes" value={busqueda} onChange={(e) => setBusqueda(e.target.value)} placeholder="Buscar por barrio, calle, categoría o descripción" /></div>
          <div className="select-field"><Icon name="filter" size={17} /><select aria-label="Filtrar por estado" value={estado} onChange={(e) => setEstado(e.target.value)}><option value="TODOS">Todos los estados</option><option value="PENDIENTE">Pendientes</option><option value="EN_REVISION">En revisión</option><option value="EN_PROCESO">En proceso</option><option value="RESUELTO">Resueltos</option><option value="RECHAZADO">Rechazados</option></select></div>
          <NavLink className="primary-button compact" to="/reportes/nuevo"><Icon name="plus" /> Nuevo reporte</NavLink>
          {(categorias.length > 1 || barrios.length > 0) && (
            <div className="listing-filters">
              {categorias.length > 1 && <div className="select-field"><select aria-label="Filtrar por categoría" value={categoria} onChange={(e) => setCategoria(e.target.value)}><option value="">Todas las categorías</option>{categorias.map((opcion) => <option key={opcion} value={opcion}>{opcion}</option>)}</select></div>}
              {barrios.length > 0 && <div className="select-field"><select aria-label="Filtrar por barrio" value={barrio} onChange={(e) => setBarrio(e.target.value)}><option value="">Todos los barrios</option>{barrios.map((opcion) => <option key={opcion} value={opcion}>{opcion}</option>)}</select></div>}
            </div>
          )}
        </div>
        <div className="connection-note"><span className={conectado ? "live-dot" : "demo-dot"} />{conectado ? `${visibles.length} reportes obtenidos del servicio ciudadano` : "Esperando conexión con el servicio ciudadano"}</div>
        {cargando && <div className="loading-panel">Cargando reportes…</div>}
        {error && <div className="alert-panel error-panel">{error}</div>}
        <div className="report-grid listing-grid">{visibles.map((reporte) => <ReportCard reporte={reporte} key={reporte.id} />)}</div>
        {!cargando && !visibles.length && <div className="empty-panel"><Icon name="map" size={30} /><h2>No encontramos reportes</h2><p>Prueba con otra búsqueda o crea el primer reporte de este sector.</p></div>}
      </section>
    </PortalShell>
  )
}

// La clave reinicia el estado al pasar de un reporte a otro sin desmontar la ruta.
export function DetalleReporte() {
  const { id = "" } = useParams()
  return <DetalleReporteVista key={id} id={id} />
}

function DetalleReporteVista({ id }: { id: string }) {
  const { session } = useAuth()
  const navigate = useNavigate()
  const aviso = (useLocation().state as { aviso?: string } | null)?.aviso
  const [reporte, setReporte] = useState<Reporte | null>(null)
  const [comentarios, setComentarios] = useState<ComentarioResponse[]>([])
  const [confirmaciones, setConfirmaciones] = useState<ConfirmacionResponse[]>([])
  const [seguimiento, setSeguimiento] = useState<SeguimientoResumenResponse | null>(null)
  const [texto, setTexto] = useState("")
  const [error, setError] = useState("")
  const [cargando, setCargando] = useState(true)
  const [sinCargar, setSinCargar] = useState<string[]>([])
  const [fotoActiva, setFotoActiva] = useState(0)
  const [publicando, setPublicando] = useState(false)
  const usuarioId = session?.usuarioId
  useEffect(() => {
    if (!id) return
    // Se ignoran las respuestas que lleguen después de salir de la página.
    let vigente = true
    cargarReporte(id).then((dato) => { if (vigente) setReporte(dato) })
      .catch((exception) => { if (vigente) setError(mensajeDe(exception, "No fue posible cargar el reporte.")) })
      .finally(() => { if (vigente) setCargando(false) })
    // Lo demás es complementario: si falla, el reporte se muestra igual y se avisa qué faltó.
    Promise.allSettled([comentariosApi.listar(id), confirmacionesApi.listar(id), seguimientoApi.obtener(id)]).then(([comments, confirmations, followup]) => {
      if (!vigente) return
      if (comments.status === "fulfilled") setComentarios(comments.value)
      if (confirmations.status === "fulfilled") setConfirmaciones(confirmations.value)
      if (followup.status === "fulfilled") setSeguimiento(followup.value)
      setSinCargar([
        comments.status === "rejected" && "los comentarios",
        confirmations.status === "rejected" && "las confirmaciones",
        followup.status === "rejected" && "el seguimiento de la comunidad",
      ].filter((parte): parte is string => Boolean(parte)))
    })
    return () => { vigente = false }
  }, [id, usuarioId])

  async function confirmar() {
    if (!session) {
      irALogin(navigate, `/reportes/${id}`)
      return
    }
    setError("")
    try {
      const existente = confirmaciones.some((item) => item.usuarioId === session.usuarioId)
      if (existente) {
        await confirmacionesApi.retirar(id)
        setConfirmaciones((actuales) => actuales.filter((item) => item.usuarioId !== session.usuarioId))
      } else {
        const nueva = await confirmacionesApi.crear(id)
        setConfirmaciones((actuales) => [...actuales, nueva])
      }
    } catch (exception) {
      setError(mensajeDe(exception, "No fue posible confirmar el reporte."))
    }
  }

  async function responder(arreglado: boolean) {
    if (!session) {
      irALogin(navigate, `/reportes/${id}`)
      return
    }
    setError("")
    try {
      setSeguimiento(await seguimientoApi.responder(id, arreglado))
    } catch (exception) {
      setError(mensajeDe(exception, "No fue posible guardar tu respuesta."))
    }
  }

  async function comentar(event: FormEvent) {
    event.preventDefault()
    if (!texto.trim() || publicando) return
    setError("")
    setPublicando(true)
    try {
      const nuevo = await comentariosApi.crear(id, texto.trim())
      setComentarios((actuales) => [...actuales, nuevo])
      setTexto("")
    } catch (exception) {
      setError(mensajeDe(exception, "No fue posible publicar el comentario."))
    } finally {
      setPublicando(false)
    }
  }

  async function editarComentario(comentario: ComentarioResponse) {
    const textoNuevo = window.prompt("Edita tu comentario", comentario.texto)?.trim()
    if (!textoNuevo || textoNuevo === comentario.texto) return
    try {
      const actualizado = await comentariosApi.actualizar(comentario.id, textoNuevo)
      setComentarios((actuales) => actuales.map((item) => item.id === actualizado.id ? actualizado : item))
    } catch (exception) {
      setError(mensajeDe(exception, "No fue posible editar el comentario."))
    }
  }

  async function eliminarComentario(comentario: ComentarioResponse) {
    const propio = comentario.usuarioId === session?.usuarioId
    if (!window.confirm(propio ? "¿Eliminar este comentario?" : `¿Eliminar el comentario de ${comentario.usuarioNombre ?? "esta persona"} por inapropiado?`)) return
    try {
      await comentariosApi.eliminar(comentario.id)
      setComentarios((actuales) => actuales.filter((item) => item.id !== comentario.id))
    } catch (exception) {
      setError(mensajeDe(exception, "No fue posible eliminar el comentario."))
    }
  }

  if (cargando) return <PortalShell title="Cargando reporte"><div className="loading-panel">Consultando la información…</div></PortalShell>
  if (!reporte) return <PortalShell title="No pudimos mostrar el reporte"><div className="alert-panel error-panel detail-load-error">{error}</div></PortalShell>
  const fotos = fotosReporte(reporte)
  const foto = fotos[Math.min(fotoActiva, fotos.length - 1)]
  const historial = [...reporte.historialEstados].sort((a, b) => new Date(a.fecha).getTime() - new Date(b.fecha).getTime())
  const cerrado = reporte.estadoRaw === "RESUELTO" || reporte.estadoRaw === "RECHAZADO"
  const esAutor = session?.usuarioId === reporte.usuarioId
  const moderador = session?.rol === "ADMIN" || session?.rol === "MODERADOR"
  const yaConfirme = Boolean(session && confirmaciones.some((item) => item.usuarioId === session.usuarioId))

  return (
    <PortalShell eyebrow={`Reporte · ${reporte.id}`} title={reporte.categoria}>
      <section className="detail-layout">
        {aviso && <div className="alert-panel error-panel detail-notice" role="alert">{aviso}</div>}
        {sinCargar.length > 0 && <div className="alert-panel error-panel detail-notice" role="alert">No pudimos cargar {new Intl.ListFormat("es", { type: "conjunction" }).format(sinCargar)}. Recarga la página para intentarlo de nuevo.</div>}
        <div className="detail-primary">
          <div className="detail-photo">
            {foto ? <a href={foto} target="_blank" rel="noopener noreferrer" title="Abrir la foto en tamaño completo"><img src={foto} alt={`Evidencia de ${reporte.categoria}${fotos.length > 1 ? ` (foto ${fotoActiva + 1} de ${fotos.length})` : ""}`} /></a> : <div className="no-photo"><Icon name="camera" size={34} /><span>Sin fotografía</span></div>}
            {fotos.length > 1 && (
              <div className="photo-thumbs" role="group" aria-label="Fotos del reporte">
                {fotos.map((url, indice) => <button key={url} type="button" className={indice === fotoActiva ? "is-active" : ""} aria-pressed={indice === fotoActiva} aria-label={`Ver foto ${indice + 1}`} onClick={() => setFotoActiva(indice)}><img src={url} alt="" /></button>)}
              </div>
            )}
          </div>
          <div className="detail-copy">
            <div className="detail-title-row"><div><span className="section-kicker">Descripción ciudadana</span><h2>{reporte.descripcion}</h2>{esAutor && reporte.estadoRaw === "PENDIENTE" && <NavLink className="edit-report-link" to={`/reportes/${reporte.id}/editar`}>Editar este reporte</NavLink>}</div><span className={`large-status ${reporte.estadoClase}`}>{reporte.estado}</span></div>
            <div className="detail-address"><Icon name="location" /><div><small>Ubicación reportada</small><strong>{reporte.lugar}</strong></div></div>
            <div className="real-map-wrap">{reporte.latitud != null && reporte.longitud != null && <MapaReportes enlaces={false} reportes={[{
              id: reporte.id, usuarioId: reporte.usuarioId, calleId: null, categoriaId: reporte.categoria, direccionOsm: reporte.direccionOsm,
              barrioOsm: reporte.barrio, descripcion: reporte.descripcion, fotos: reporte.fotos, latitud: reporte.latitud, longitud: reporte.longitud,
              estado: reporte.estadoRaw, prioridad: reporte.prioridad, fechaCreacion: reporte.fechaCreacion,
              fechaActualizacion: reporte.fechaCreacion, historialEstados: reporte.historialEstados,
            }]} />}</div>
            <section className="comments-panel">
              <div className="content-heading"><div><h3>Conversación ciudadana</h3><p>{comentarios.length} comentario(s)</p></div></div>
              {comentarios.map((comentario) => {
                const propio = session?.usuarioId === comentario.usuarioId
                return <article className="comment" key={comentario.id}><span><Icon name="user" /></span><div><strong>{comentario.usuarioNombre ?? "Miembro de la comunidad"}</strong><p>{comentario.texto}</p><small>{new Date(comentario.fecha).toLocaleString("es-CO")}{comentario.editado ? " · editado" : ""}</small>{(propio || moderador) && <div className="comment-actions">{propio && <button onClick={() => editarComentario(comentario)}>Editar</button>}<button onClick={() => eliminarComentario(comentario)}>Eliminar</button></div>}</div></article>
              })}
              {session ? <form className="comment-form" onSubmit={comentar}><textarea aria-label="Escribe un comentario" maxLength={1000} value={texto} onChange={(event) => setTexto(event.target.value)} placeholder="Comparte información útil…" required /><button className="primary-button compact" disabled={publicando}>{publicando ? "Publicando…" : "Comentar"}</button></form> : <p className="signin-note"><NavLink to="/login" state={{ desde: `/reportes/${id}` }}>Inicia sesión</NavLink> para comentar.</p>}
            </section>
          </div>
        </div>
        <aside className="detail-sidebar">
          {error && <div className="alert-panel error-panel" role="alert">{error}</div>}
          <div className="community-card">
            <span className="section-kicker">La comunidad dice</span>
            <h3>{cerrado ? "Personas que confirmaron este daño" : "¿Este problema sigue igual?"}</h3>
            <div className="vote-count"><strong>{confirmaciones.length}</strong><span>{confirmaciones.length === 1 ? "persona lo confirma" : "personas lo confirman"}</span></div>
            {/* El autor no confirma su propio reporte y un caso cerrado ya no admite confirmaciones. */}
            {esAutor ? <p className="community-note">Es tu reporte: otras personas pueden confirmarlo para darle más fuerza.</p>
              : !cerrado && <button className="primary-button full" onClick={confirmar}><Icon name="check" /> {yaConfirme ? "Retirar confirmación" : "Confirmar reporte"}</button>}
          </div>
          {seguimiento && !cerrado && <div className="community-card"><span className="section-kicker">¿Ya lo arreglaron?</span><h3>{seguimiento.arreglado} dicen que sí · {seguimiento.sigue} que sigue igual</h3><div className="followup-actions"><button className={seguimiento.miRespuesta === true ? "selected" : ""} aria-pressed={seguimiento.miRespuesta === true} onClick={() => responder(true)}>Ya está arreglado</button><button className={seguimiento.miRespuesta === false ? "selected" : ""} aria-pressed={seguimiento.miRespuesta === false} onClick={() => responder(false)}>Sigue igual</button></div></div>}
          <div className="timeline-card">
            <span className="section-kicker">Seguimiento</span>
            <div className={historial.length ? "timeline-item done" : "timeline-item active"}><i /><div><strong>Reporte recibido</strong><small>{reporte.fechaCreacion ? new Date(reporte.fechaCreacion).toLocaleString("es-CO") : reporte.fecha}</small></div></div>
            {historial.map((cambio, index) => (
              <div className={index === historial.length - 1 ? "timeline-item active" : "timeline-item done"} key={cambio.id ?? `${cambio.fecha}-${index}`}>
                <i />
                <div><strong>{textoEstado(cambio.estadoNuevo)}</strong>{cambio.comentarioAdmin && <span>{cambio.comentarioAdmin}</span>}<small>{new Date(cambio.fecha).toLocaleString("es-CO")}</small></div>
              </div>
            ))}
            {historial.length === 0 && <p className="timeline-empty">Aún no hay cambios de estado.</p>}
          </div>
        </aside>
      </section>
    </PortalShell>
  )
}

const MAX_FOTOS = 3
const MAX_BYTES_FOTO = 5 * 1024 * 1024
// Fotos más pesadas se reducen en el navegador antes de subirlas; por encima de esto ni se intenta.
const MAX_BYTES_ORIGINAL = 30 * 1024 * 1024
const TIPOS_FOTO = ["image/jpeg", "image/png", "image/webp"]

type EstadoUbicacion =
  | { fase: "buscando" }
  | { fase: "lista"; direccion: string | null; dentroDelArea: boolean | null }

const UBICACION_SIN_DATOS: EstadoUbicacion = { fase: "lista", direccion: null, dentroDelArea: null }

export function CrearReporte() {
  const navigate = useNavigate()
  const { session } = useAuth()
  const [paso, setPaso] = useState(1)
  const [fotos, setFotos] = useState<{ archivo: File; preview: string }[]>([])
  const [coordenadas, setCoordenadas] = useState<[number, number]>()
  const [ubicacion, setUbicacion] = useState<EstadoUbicacion>(UBICACION_SIN_DATOS)
  const [categoria, setCategoria] = useState("")
  const [sugerencias, setSugerencias] = useState<string[]>([])
  const [descripcion, setDescripcion] = useState("")
  const [duplicados, setDuplicados] = useState<PosibleDuplicadoResponse[]>([])
  const [error, setError] = useState("")
  const [enviando, setEnviando] = useState(false)
  const [procesandoFotos, setProcesandoFotos] = useState(false)
  const [avisoFotos, setAvisoFotos] = useState("")
  const previews = useRef<string[]>([])

  useEffect(() => () => previews.current.forEach(URL.revokeObjectURL), [])
  // El catálogo de categorías se ofrece como sugerencia; la persona puede escribir otra.
  useEffect(() => {
    categoriasApi.listar(true).then((lista) => setSugerencias(lista.map((item) => item.nombre))).catch(() => setSugerencias([]))
  }, [])
  useEffect(() => {
    if (!coordenadas) return
    // Si se marca otro punto antes de que llegue la respuesta, la anterior se descarta.
    let vigente = true
    geocodingApi.inverso(coordenadas[0], coordenadas[1])
      .then((dato) => { if (vigente) setUbicacion({ fase: "lista", direccion: dato.direccionOsm, dentroDelArea: dato.dentroDelArea }) })
      .catch(() => { if (vigente) setUbicacion(UBICACION_SIN_DATOS) })
    reportesApi.posiblesDuplicados(coordenadas[0], coordenadas[1])
      .then((lista) => { if (vigente) setDuplicados(lista) })
      .catch(() => { if (vigente) setDuplicados([]) })
    return () => { vigente = false }
  }, [coordenadas])

  function seleccionarUbicacion(lat: number, lng: number) {
    setCoordenadas([lat, lng])
    setUbicacion({ fase: "buscando" })
    setDuplicados([])
    setError("")
  }

  async function cargarFotos(event: ChangeEvent<HTMLInputElement>) {
    const archivos = Array.from(event.target.files ?? [])
    // Permite volver a elegir el mismo archivo después de quitarlo.
    event.target.value = ""
    const avisos: string[] = []
    const imagenes = archivos.filter((archivo) => TIPOS_FOTO.includes(archivo.type))
    if (imagenes.length < archivos.length) avisos.push("Solo se permiten fotografías JPEG, PNG o WEBP.")
    const enormes = imagenes.filter((archivo) => archivo.size > MAX_BYTES_ORIGINAL)
    if (enormes.length) avisos.push(`${enormes.map((archivo) => archivo.name).join(", ")} ${enormes.length === 1 ? "es demasiado pesada" : "son demasiado pesadas"}.`)
    const candidatas = imagenes.filter((archivo) => archivo.size <= MAX_BYTES_ORIGINAL)
    const cupo = MAX_FOTOS - fotos.length
    if (candidatas.length > cupo) avisos.push(`Solo puedes adjuntar ${MAX_FOTOS} fotografías en total: ${candidatas.length - cupo === 1 ? "se descartó 1" : `se descartaron ${candidatas.length - cupo}`}.`)
    setProcesandoFotos(true)
    try {
      // Reduce el tamaño y quita los metadatos (como la ubicación GPS de la cámara) antes de subirlas.
      const preparadas = await Promise.all(candidatas.slice(0, cupo).map(prepararFoto))
      const pesadas = preparadas.filter((archivo) => archivo.size > MAX_BYTES_FOTO)
      if (pesadas.length) avisos.push(`${pesadas.map((archivo) => archivo.name).join(", ")} ${pesadas.length === 1 ? "sigue pesando" : "siguen pesando"} más de 5 MB. Elige una foto más liviana.`)
      const nuevas = preparadas.filter((archivo) => archivo.size <= MAX_BYTES_FOTO).map((archivo) => ({ archivo, preview: URL.createObjectURL(archivo) }))
      previews.current.push(...nuevas.map((foto) => foto.preview))
      setFotos((actuales) => [...actuales, ...nuevas])
    } finally {
      setProcesandoFotos(false)
      setAvisoFotos(avisos.join(" "))
    }
  }

  function quitarFoto(preview: string) {
    URL.revokeObjectURL(preview)
    previews.current = previews.current.filter((url) => url !== preview)
    setFotos((actuales) => actuales.filter((foto) => foto.preview !== preview))
    setAvisoFotos("")
  }

  async function enviar(event: FormEvent) {
    event.preventDefault()
    setError("")
    if (paso === 1 && !coordenadas) {
      setError("Selecciona la ubicación exacta en el mapa.")
      return
    }
    if (paso === 1 && ubicacion.fase === "lista" && ubicacion.dentroDelArea === false) {
      setError("Ese punto está fuera de Cartagena. Marca en el mapa la ubicación del daño dentro de la ciudad.")
      return
    }
    if (paso === 2 && (!categoria.trim() || descripcion.trim().length < 10)) {
      setError("Escribe una categoría y una descripción de al menos 10 caracteres.")
      return
    }
    if (paso === 2 && procesandoFotos) {
      setError("Espera a que terminen de prepararse las fotos.")
      return
    }
    if (paso < 3) {
      setPaso(paso + 1)
      return
    }
    if (!coordenadas) return
    setEnviando(true)
    try {
      const creado = await reportesApi.crear({ categoriaId: categoria.trim(), descripcion: descripcion.trim(), latitud: coordenadas[0], longitud: coordenadas[1] })
      // El reporte ya existe: si las fotos fallan se avisa en el detalle, sin volver al formulario (reenviar lo duplicaría).
      let aviso: string | undefined
      if (fotos.length) {
        try {
          await reportesApi.subirFotos(creado.id, fotos.map((foto) => foto.archivo))
        } catch (exception) {
          aviso = `El reporte se creó, pero no se pudieron subir las fotos: ${mensajeDe(exception, "error desconocido")}`
        }
      }
      navigate(`/reportes/${creado.id}`, { state: aviso ? { aviso } : undefined })
    } catch (exception) {
      setError(mensajeDe(exception, "No fue posible crear el reporte."))
    } finally {
      setEnviando(false)
    }
  }

  async function confirmarDuplicado(reporteId: string) {
    try {
      await confirmacionesApi.crear(reporteId)
      navigate(`/reportes/${reporteId}`)
    } catch (exception) {
      setError(mensajeDe(exception, "No fue posible confirmar el reporte existente."))
    }
  }

  const textoUbicacion = ubicacion.fase === "buscando"
    ? "Buscando la dirección…"
    : ubicacion.direccion ?? "No pudimos identificar la dirección; el punto en el mapa es suficiente."

  return (
    <PortalShell eyebrow="Tu voz mueve la ciudad" title="Crear un reporte" action={false}>
      <section className="report-form-layout">
        <div className="form-progress">{["Ubicación", "Evidencia", "Revisar"].map((item, index) => <div className={paso >= index + 1 ? "active" : ""} key={item}><span>{paso > index + 1 ? <Icon name="check" size={14} /> : index + 1}</span><strong>{item}</strong></div>)}</div>
        <form className="report-form" onSubmit={enviar}>
          {paso === 1 && <>
            <div className="form-heading"><span>01</span><div><h2>¿Dónde está el problema?</h2><p>Toca el mapa (puedes acercarlo para precisar) o usa la ubicación de tu dispositivo.</p></div></div>
            <div className="picker-map real-picker"><MapaSeleccionPunto onSeleccionar={seleccionarUbicacion} puntoInicial={coordenadas} /></div>
            {coordenadas && <div className="coords-summary" aria-live="polite"><Icon name="location" /><div><strong>{textoUbicacion}</strong><small>{coordenadas[0].toFixed(5)}, {coordenadas[1].toFixed(5)}</small></div></div>}
            {ubicacion.fase === "lista" && ubicacion.dentroDelArea === false && <div className="alert-panel error-panel" role="alert">Este punto está fuera de Cartagena. ViaCTG solo recibe reportes de la ciudad.</div>}
            {duplicados.length > 0 && <div className="duplicates-box"><strong>¿Ya lo reportaron?</strong><p>Encontramos {duplicados.length} reporte(s) activo(s) a menos de 100 metros. Si es el mismo daño, confírmalo en lugar de crear otro.</p>{duplicados.slice(0, 3).map((item) => <div className="duplicate-action" key={item.reporte.id}><NavLink to={`/reportes/${item.reporte.id}`}>{item.reporte.categoriaId} · {Math.round(item.distanciaMetros)} m · {item.confirmaciones} confirmaciones</NavLink>{item.reporte.usuarioId === session?.usuarioId ? <button type="button" onClick={() => navigate(`/reportes/${item.reporte.id}`)}>Es mi reporte: verlo</button> : <button type="button" onClick={() => confirmarDuplicado(item.reporte.id)}>Es el mismo: confirmar</button>}</div>)}</div>}
          </>}
          {paso === 2 && <>
            <div className="form-heading"><span>02</span><div><h2>Cuéntanos qué está pasando</h2><p>Una descripción clara y fotos ayudan a gestionar mejor el caso.</p></div></div>
            <div className="form-fields">
              <label>Categoría<input value={categoria} onChange={(event) => setCategoria(event.target.value)} maxLength={100} list="categorias-sugeridas" placeholder="Ejemplo: Hueco, inundación, vía sin pavimentar" required /></label>
              <datalist id="categorias-sugeridas">{sugerencias.map((nombre) => <option key={nombre} value={nombre} />)}</datalist>
              {sugerencias.length > 0 && <div className="category-suggestions" role="group" aria-label="Categorías frecuentes">{sugerencias.slice(0, 8).map((nombre) => <button type="button" key={nombre} className={categoria === nombre ? "is-selected" : ""} aria-pressed={categoria === nombre} onClick={() => setCategoria(nombre)}>{nombre}</button>)}</div>}
              <label>Descripción<textarea rows={5} minLength={10} maxLength={2000} value={descripcion} onChange={(event) => setDescripcion(event.target.value)} placeholder="Describe el problema y alguna referencia para encontrarlo..." required /></label>
              <label className="upload-zone"><Icon name="camera" size={30} /><strong>{procesandoFotos ? "Preparando fotos…" : "Agrega hasta 3 fotografías"}</strong><small>JPEG, PNG o WEBP · se reducen y se les quita la ubicación GPS antes de subirlas</small><input type="file" accept="image/jpeg,image/png,image/webp" multiple onChange={cargarFotos} disabled={procesandoFotos || fotos.length === MAX_FOTOS} /></label>
              {avisoFotos && <div className="alert-panel error-panel" role="alert">{avisoFotos}</div>}
              {fotos.length > 0 && <div className="upload-previews">{fotos.map((foto) => <div className="upload-preview" key={foto.preview}><img src={foto.preview} alt={`Vista previa de ${foto.archivo.name}`} /><button type="button" onClick={() => quitarFoto(foto.preview)} aria-label={`Quitar ${foto.archivo.name}`}>×</button></div>)}</div>}
            </div>
          </>}
          {paso === 3 && <>
            <div className="form-heading"><span>03</span><div><h2>Revisa antes de publicar</h2><p>Tu reporte será visible para toda la comunidad.</p></div></div>
            <div className="review-card"><div className="review-image">{fotos[0] ? <img src={fotos[0].preview} alt="Evidencia seleccionada" /> : <div><Icon name="camera" /><span>Sin fotografías</span></div>}</div><div><span className="category-chip coral">{categoria}</span><h3>{ubicacion.fase === "lista" && ubicacion.direccion ? ubicacion.direccion : "Ubicación seleccionada en Cartagena"}</h3><p>{descripcion}</p></div></div>
          </>}
          {error && <div className="alert-panel error-panel" role="alert">{error}</div>}
          <div className="form-actions">{paso > 1 && <button type="button" className="ghost-button" onClick={() => setPaso(paso - 1)}>Volver</button>}<button className="primary-button" type="submit" disabled={enviando}>{enviando ? "Publicando…" : paso === 3 ? "Publicar reporte" : "Continuar"} <Icon name="arrow" /></button></div>
        </form>
      </section>
    </PortalShell>
  )
}

export function EditarReporte() {
  const { id = "" } = useParams()
  const navigate = useNavigate()
  const [reporte, setReporte] = useState<ReporteResponse | null>(null)
  const [descripcion, setDescripcion] = useState("")
  const [coordenadas, setCoordenadas] = useState<[number, number]>()
  const [error, setError] = useState("")
  const [guardando, setGuardando] = useState(false)

  useEffect(() => {
    reportesApi.obtener(id).then((dato) => {
      setReporte(dato)
      setDescripcion(dato.descripcion)
      setCoordenadas([dato.latitud, dato.longitud])
    }).catch((exception) => setError(mensajeDe(exception, "No fue posible cargar el reporte.")))
  }, [id])

  async function guardar(event: FormEvent) {
    event.preventDefault()
    if (!coordenadas) return
    setGuardando(true)
    setError("")
    try {
      await reportesApi.actualizar(id, { descripcion: descripcion.trim(), latitud: coordenadas[0], longitud: coordenadas[1] })
      navigate(`/reportes/${id}`)
    } catch (exception) {
      setError(mensajeDe(exception, "No fue posible actualizar el reporte."))
    } finally {
      setGuardando(false)
    }
  }

  return <PortalShell eyebrow="Reporte pendiente" title="Editar reporte"><section className="report-form-layout">{error && <div className="alert-panel error-panel" role="alert">{error}</div>}{reporte && <form className="report-form" onSubmit={guardar}><div className="form-heading"><span>01</span><div><h2>Actualiza la información</h2><p>Solo los reportes pendientes pueden editarse.</p></div></div><div className="form-fields"><label>Categoría<input value={reporte.categoriaId} disabled /></label><label>Descripción<textarea minLength={10} maxLength={2000} rows={6} value={descripcion} onChange={(event) => setDescripcion(event.target.value)} required /></label></div><div className="picker-map real-picker"><MapaSeleccionPunto puntoInicial={coordenadas} onSeleccionar={(lat, lng) => setCoordenadas([lat, lng])} /></div><div className="form-actions"><button type="button" className="ghost-button" onClick={() => navigate(-1)}>Cancelar</button><button className="primary-button" disabled={guardando}>{guardando ? "Guardando…" : "Guardar cambios"}</button></div></form>}</section></PortalShell>
}

type FiltroMapa = "ACTIVOS" | "TODOS" | EstadoReporte
const ESTADOS_ACTIVOS: EstadoReporte[] = ["PENDIENTE", "EN_REVISION", "EN_PROCESO"]
const LEYENDA_MAPA: EstadoReporte[] = ["PENDIENTE", "EN_REVISION", "EN_PROCESO", "RESUELTO", "RECHAZADO"]

export function Mapa() {
  const [carga, setCarga] = useState<{ fase: "cargando" } | { fase: "error"; mensaje: string } | { fase: "cargado"; reportes: ReporteResponse[] }>({ fase: "cargando" })
  // Por defecto, solo los daños abiertos: es lo que le sirve a quien planea su recorrido.
  const [filtro, setFiltro] = useState<FiltroMapa>("ACTIVOS")
  const [categoria, setCategoria] = useState("")
  const [barrio, setBarrio] = useState("")
  useEffect(() => {
    reportesApi.listar()
      .then((reportes) => setCarga({ fase: "cargado", reportes }))
      .catch((exception) => setCarga({ fase: "error", mensaje: mensajeDe(exception, "No fue posible cargar el mapa.") }))
  }, [])
  const todos = useMemo(() => carga.fase === "cargado" ? carga.reportes : [], [carga])
  // El documento de visión pide listado y mapa filtrables por barrio y categoría.
  const categorias = useMemo(() => valoresUnicos(todos.map((reporte) => reporte.categoriaId)), [todos])
  const barrios = useMemo(() => valoresUnicos(todos.map((reporte) => reporte.barrioOsm)), [todos])
  const reportes = useMemo(() => todos.filter((reporte) =>
    (filtro === "TODOS" || (filtro === "ACTIVOS" ? ESTADOS_ACTIVOS.includes(reporte.estado) : reporte.estado === filtro)) &&
    coincideFiltro(categoria, reporte.categoriaId) &&
    coincideFiltro(barrio, reporte.barrioOsm)), [todos, filtro, categoria, barrio])
  return (
    <PortalShell eyebrow="Cartagena en tiempo real" title="Mapa ciudadano">
      <section className="full-map-layout">
        <aside className="map-sidebar">
          <div className="select-field map-filter"><Icon name="filter" size={17} /><select aria-label="Qué reportes mostrar" value={filtro} onChange={(event) => setFiltro(event.target.value as FiltroMapa)}><option value="ACTIVOS">Daños activos</option><option value="TODOS">Todos los reportes</option>{LEYENDA_MAPA.map((estado) => <option key={estado} value={estado}>{textoEstado(estado)}</option>)}</select></div>
          {categorias.length > 1 && <div className="select-field map-filter"><select aria-label="Filtrar el mapa por categoría" value={categoria} onChange={(event) => setCategoria(event.target.value)}><option value="">Todas las categorías</option>{categorias.map((opcion) => <option key={opcion} value={opcion}>{opcion}</option>)}</select></div>}
          {barrios.length > 0 && <div className="select-field map-filter"><select aria-label="Filtrar el mapa por barrio" value={barrio} onChange={(event) => setBarrio(event.target.value)}><option value="">Todos los barrios</option>{barrios.map((opcion) => <option key={opcion} value={opcion}>{opcion}</option>)}</select></div>}
          <ul className="map-legend" aria-label="Colores del mapa">{LEYENDA_MAPA.map((estado) => <li key={estado}><i style={{ background: COLOR_ESTADO[estado] }} />{textoEstado(estado)}</li>)}</ul>
          <div className="map-filter-title"><strong>Reportes visibles</strong><span>{reportes.length}</span></div>
          {carga.fase === "cargando" && <p className="map-sidebar-note">Cargando reportes…</p>}
          {carga.fase === "error" && <div className="alert-panel error-panel">{carga.mensaje}</div>}
          {carga.fase === "cargado" && reportes.length === 0 && <p className="map-sidebar-note">No hay reportes con este filtro.</p>}
          {reportes.map((reporte) => <NavLink to={`/reportes/${reporte.id}`} className="map-list-item" key={reporte.id}><span className="map-list-dot" style={{ background: COLOR_ESTADO[reporte.estado] }} /><div><strong>{reporte.categoriaId}</strong><small>{reporte.direccionOsm ?? `${reporte.latitud.toFixed(4)}, ${reporte.longitud.toFixed(4)}`}</small></div><Icon name="arrow" size={16} /></NavLink>)}
        </aside>
        <div className="interactive-map real-full-map">
          {carga.fase === "cargando" && <div className="loading-panel">Cargando mapa…</div>}
          {carga.fase === "error" && <div className="loading-panel">No pudimos cargar el mapa. {carga.mensaje}</div>}
          {carga.fase === "cargado" && <MapaReportes reportes={reportes} />}
          {carga.fase === "cargado" && reportes.length === 0 && <div className="map-empty-note"><Icon name="map" size={15} /> No hay reportes con este filtro</div>}
        </div>
      </section>
    </PortalShell>
  )
}

export function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const desde = (useLocation().state as { desde?: string } | null)?.desde
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState("")
  const [enviando, setEnviando] = useState(false)
  async function enviar(event: FormEvent) {
    event.preventDefault()
    setEnviando(true)
    setError("")
    try {
      await login(email, password)
      navigate(rutaSegura(desde), { replace: true })
    } catch (exception) {
      setError(mensajeDe(exception, "No fue posible iniciar sesión."))
    } finally {
      setEnviando(false)
    }
  }
  return (
    <div className="auth-page">
      <div className="auth-image"><NavLink to="/" className="auth-back">← Volver al inicio</NavLink><div><span>Cartagena se mueve</span><h1>Tu voz también<br />abre caminos.</h1><p>Únete a una comunidad que cuida cada calle, cada barrio y cada trayecto.</p></div></div>
      <div className="auth-panel"><Logo /><form className="auth-form" onSubmit={enviar}><span className="section-kicker">Bienvenido de vuelta</span><h2>Inicia sesión</h2><p>Consulta tus reportes y sigue sus avances.</p><label>Correo electrónico<input type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="tu@correo.com" required /></label><label>Contraseña<input type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="••••••••" required /></label><div className="form-inline"><span /><NavLink to="/recuperar">¿Olvidaste tu contraseña?</NavLink></div>{error && <div className="alert-panel error-panel" role="alert">{error}</div>}<button className="primary-button full" disabled={enviando}>{enviando ? "Entrando…" : "Entrar a VíaCTG"} <Icon name="arrow" /></button><span className="auth-switch">¿Aún no tienes cuenta? <NavLink to="/registro" state={{ desde }}>Crear cuenta</NavLink></span></form></div>
    </div>
  )
}

export function Registro() {
  const { registro } = useAuth()
  const navigate = useNavigate()
  const desde = (useLocation().state as { desde?: string } | null)?.desde
  const [nombre, setNombre] = useState("")
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState("")
  const [enviando, setEnviando] = useState(false)
  async function enviar(event: FormEvent) {
    event.preventDefault()
    setEnviando(true)
    setError("")
    try {
      await registro(nombre, email, password)
      navigate(rutaSegura(desde), { replace: true })
    } catch (exception) {
      setError(mensajeDe(exception, "No fue posible crear la cuenta."))
    } finally {
      setEnviando(false)
    }
  }
  return <div className="auth-page"><div className="auth-image"><NavLink to="/" className="auth-back">← Volver al inicio</NavLink><div><span>Súmate a la comunidad</span><h1>Una calle mejor empieza contigo.</h1><p>Reporta, confirma y sigue los problemas de tu barrio.</p></div></div><div className="auth-panel"><Logo /><form className="auth-form" onSubmit={enviar}><span className="section-kicker">Nueva cuenta</span><h2>Únete a VíaCTG</h2><label>Nombre completo<input autoComplete="name" value={nombre} onChange={(event) => setNombre(event.target.value)} maxLength={100} required /></label><label>Correo electrónico<input type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} required /></label><label>Contraseña<input type="password" autoComplete="new-password" minLength={8} maxLength={72} value={password} onChange={(event) => setPassword(event.target.value)} required /></label>{error && <div className="alert-panel error-panel" role="alert">{error}</div>}<button className="primary-button full" disabled={enviando}>{enviando ? "Creando cuenta…" : "Crear mi cuenta"} <Icon name="arrow" /></button><span className="auth-switch">¿Ya tienes cuenta? <NavLink to="/login" state={{ desde }}>Inicia sesión</NavLink></span></form></div></div>
}

export function RecuperarPassword() {
  const [email, setEmail] = useState("")
  const [enviado, setEnviado] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState("")
  async function enviar(event: FormEvent) {
    event.preventDefault()
    setEnviando(true)
    setError("")
    try {
      await authApi.recuperar(email)
      setEnviado(true)
    } catch (exception) {
      setError(mensajeDe(exception, "No fue posible procesar la solicitud."))
    } finally {
      setEnviando(false)
    }
  }
  return <div className="auth-page"><div className="auth-image"><NavLink to="/login" className="auth-back">← Volver</NavLink><div><span>Recupera tu acceso</span><h1>Volvamos a ponerte en camino.</h1></div></div><div className="auth-panel"><Logo /><form className="auth-form" onSubmit={enviar}><span className="section-kicker">Contraseña</span><h2>Recuperar acceso</h2>{enviado ? <div className="success-message" role="status">Si existe una cuenta con {email}, recibirás un enlace válido por 30 minutos.</div> : <><p>Escribe el correo con el que te registraste.</p><label>Correo electrónico<input type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} required /></label>{error && <div className="alert-panel error-panel" role="alert">{error}</div>}<button className="primary-button full" disabled={enviando}>{enviando ? "Enviando…" : "Enviar enlace"} <Icon name="arrow" /></button></>}</form></div></div>
}

export function RestablecerPassword() {
  const [params] = useSearchParams()
  const token = params.get("token") ?? ""
  const [password, setPassword] = useState("")
  const [confirmacion, setConfirmacion] = useState("")
  const [listo, setListo] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState("")
  async function enviar(event: FormEvent) {
    event.preventDefault()
    if (password !== confirmacion) return setError("Las contraseñas no coinciden.")
    setEnviando(true)
    setError("")
    try {
      await authApi.restablecer(token, password)
      setListo(true)
    } catch (exception) {
      setError(mensajeDe(exception, "No fue posible actualizar la contraseña."))
    } finally {
      setEnviando(false)
    }
  }
  return <div className="auth-page"><div className="auth-image"><div><span>Seguridad</span><h1>Crea una nueva contraseña.</h1></div></div><div className="auth-panel"><Logo /><form className="auth-form" onSubmit={enviar}><span className="section-kicker">Nueva contraseña</span><h2>Restablecer acceso</h2>{listo ? <><div className="success-message" role="status">Tu contraseña se actualizó correctamente.</div><NavLink className="primary-button full" to="/login">Iniciar sesión</NavLink></> : !token ? <div className="alert-panel error-panel">El enlace está incompleto.</div> : <><label>Contraseña nueva<input type="password" autoComplete="new-password" minLength={8} maxLength={72} value={password} onChange={(event) => setPassword(event.target.value)} required /></label><label>Confirmar contraseña<input type="password" autoComplete="new-password" minLength={8} maxLength={72} value={confirmacion} onChange={(event) => setConfirmacion(event.target.value)} required /></label>{error && <div className="alert-panel error-panel" role="alert">{error}</div>}<button className="primary-button full" disabled={enviando}>{enviando ? "Guardando…" : "Guardar contraseña"}</button></>}</form></div></div>
}

// Menú lateral del espacio personal; NavLink marca la página actual con la clase "active".
function CuentaNav() {
  return <aside className="account-nav"><NavLink to="/perfil"><Icon name="user" /> Mi perfil</NavLink><NavLink to="/mis-reportes"><Icon name="location" /> Mis reportes</NavLink><NavLink to="/notificaciones"><Icon name="bell" /> Notificaciones</NavLink></aside>
}

const FILTROS_MIS_REPORTES: [string, EstadoReporte | null][] = [["Todos", null], ["Pendientes", "PENDIENTE"], ["En revisión", "EN_REVISION"], ["En proceso", "EN_PROCESO"], ["Resueltos", "RESUELTO"], ["Rechazados", "RECHAZADO"]]

export function MisReportes() {
  const [datos, setDatos] = useState<Reporte[]>([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState("")
  const [estado, setEstado] = useState<EstadoReporte | null>(null)
  useEffect(() => {
    cargarMisReportes().then(setDatos)
      .catch((exception) => setError(mensajeDe(exception, "No fue posible cargar tus reportes.")))
      .finally(() => setCargando(false))
  }, [])
  // Se descargan una vez y se filtran aquí: así cada pestaña muestra su conteo sin otra petición.
  const conteo = (filtro: EstadoReporte | null) => filtro ? datos.filter((reporte) => reporte.estadoRaw === filtro).length : datos.length
  const visibles = estado ? datos.filter((reporte) => reporte.estadoRaw === estado) : datos
  return <PortalShell eyebrow="Tu espacio ciudadano" title="Mis reportes"><section className="account-layout"><CuentaNav /><div className="my-reports">
    <div className="content-heading"><div><h2>Lo que has reportado</h2><p>{datos.length} {datos.length === 1 ? "reporte" : "reportes"} en total</p></div><NavLink className="primary-button compact" to="/reportes/nuevo"><Icon name="plus" /> Nuevo reporte</NavLink></div>
    {datos.length > 0 && <div className="filters" aria-label="Filtrar mis reportes por estado">{FILTROS_MIS_REPORTES.filter(([, filtro]) => !filtro || conteo(filtro) > 0).map(([texto, filtro]) => <button key={texto} className={estado === filtro ? "selected" : ""} aria-pressed={estado === filtro} onClick={() => setEstado(filtro)}>{texto} ({conteo(filtro)})</button>)}</div>}
    {cargando && <div className="loading-panel">Cargando tus reportes…</div>}
    {error && <div className="alert-panel error-panel" role="alert">{error}</div>}
    <div className="report-grid my-reports-grid">{visibles.map((reporte) => <ReportCard reporte={reporte} editable key={reporte.id} />)}</div>
    {!cargando && !error && !datos.length && <div className="empty-panel"><Icon name="map" size={30} /><h2>Aún no has reportado nada</h2><p>Cuando veas un hueco, una alcantarilla abierta o un poste sin luz, repórtalo y síguelo desde aquí.</p><NavLink className="primary-button compact empty-action" to="/reportes/nuevo"><Icon name="plus" /> Reportar un daño</NavLink></div>}
  </div></section></PortalShell>
}

export function Notificaciones() {
  const [items, setItems] = useState<NotificacionResponse[]>([])
  const [error, setError] = useState("")
  useEffect(() => {
    notificacionesApi.listar().then(setItems).catch((exception) => setError(mensajeDe(exception, "No fue posible cargar las notificaciones.")))
  }, [])
  async function leer(id: string) {
    try {
      const actualizada = await notificacionesApi.marcarLeida(id)
      setItems((actuales) => actuales.map((item) => item.id === id ? actualizada : item))
      avisarCambioNotificaciones()
    } catch (exception) {
      setError(mensajeDe(exception, "No fue posible actualizar la notificación."))
    }
  }
  async function leerTodas() {
    try {
      await notificacionesApi.marcarTodasLeidas()
      setItems((actuales) => actuales.map((item) => ({ ...item, leida: true })))
      avisarCambioNotificaciones()
    } catch (exception) {
      setError(mensajeDe(exception, "No fue posible actualizar las notificaciones."))
    }
  }
  const sinLeer = items.filter((item) => !item.leida).length
  return <PortalShell eyebrow="Mantente al día" title="Notificaciones"><section className="account-layout"><CuentaNav /><div className="notification-list"><div className="content-heading"><div><h2>Novedades de tus reportes</h2><p>{sinLeer} sin leer</p></div>{sinLeer > 1 && <button className="read-button" onClick={leerTodas}>Marcar todas como leídas</button>}</div>{error && <div className="alert-panel error-panel" role="alert">{error}</div>}{items.map((item) => <article className={!item.leida ? "notification unread" : "notification"} key={item.id}><span className="notification-icon"><Icon name="bell" /></span><div><strong>{item.mensaje}</strong>{item.reporteId && <NavLink to={`/reportes/${item.reporteId}`}>Ver reporte</NavLink>}<small>{new Date(item.fecha).toLocaleString("es-CO")}</small></div>{!item.leida && <button className="read-button" onClick={() => leer(item.id)}>Marcar leída</button>}</article>)}{!items.length && !error && <div className="empty-panel"><h2>Estás al día</h2><p>Te avisaremos cuando cambien tus reportes.</p></div>}</div></section></PortalShell>
}

export function Perfil() {
  const { actualizarNombre, renovarSesion, logout } = useAuth()
  const navigate = useNavigate()
  const [usuario, setUsuario] = useState<UsuarioResponse | null>(null)
  const [nombre, setNombre] = useState("")
  const [telefono, setTelefono] = useState("")
  const [actual, setActual] = useState("")
  const [nueva, setNueva] = useState("")
  const [confirmacion, setConfirmacion] = useState("")
  const [mensaje, setMensaje] = useState("")
  const [error, setError] = useState("")
  useEffect(() => {
    usuariosApi.perfil().then((dato) => { setUsuario(dato); setNombre(dato.nombre); setTelefono(dato.telefono ?? "") }).catch((exception) => setError(mensajeDe(exception, "No fue posible cargar el perfil.")))
  }, [])
  function limpiarMensajes() {
    setMensaje("")
    setError("")
  }
  async function guardar(event: FormEvent) {
    event.preventDefault()
    limpiarMensajes()
    try {
      // Un teléfono vacío borra el guardado.
      const actualizado = await usuariosApi.actualizarPerfil({ nombre: nombre.trim(), telefono: telefono.trim() })
      setUsuario(actualizado)
      actualizarNombre(actualizado.nombre)
      setMensaje("Perfil actualizado.")
    } catch (exception) { setError(mensajeDe(exception, "No fue posible actualizar el perfil.")) }
  }
  async function cambiarClave(event: FormEvent) {
    event.preventDefault()
    limpiarMensajes()
    if (nueva !== confirmacion) return setError("Las contraseñas nuevas no coinciden.")
    try {
      renovarSesion(await usuariosApi.cambiarPassword(actual, nueva))
      setActual(""); setNueva(""); setConfirmacion(""); setMensaje("Contraseña actualizada y otras sesiones cerradas.")
    } catch (exception) { setError(mensajeDe(exception, "No fue posible cambiar la contraseña.")) }
  }
  async function cerrarTodo() {
    limpiarMensajes()
    if (!window.confirm("¿Cerrar la sesión en todos tus dispositivos, incluido este?")) return
    try {
      await usuariosApi.cerrarSesiones()
      logout()
      navigate("/login")
    } catch (exception) { setError(mensajeDe(exception, "No fue posible cerrar las sesiones.")) }
  }
  return <PortalShell eyebrow="Tu espacio ciudadano" title="Mi perfil"><section className="account-layout"><CuentaNav /><div className="profile-stack">{error && <div className="alert-panel error-panel" role="alert">{error}</div>}{mensaje && <div className="success-message" role="status">{mensaje}</div>}<div className="profile-card"><div className="profile-intro"><div className="avatar">{nombre.slice(0, 2).toUpperCase()}</div><div><h2>{usuario?.nombre ?? "Cargando…"}</h2><span>{usuario?.rol} · {usuario?.email}</span></div></div><form className="profile-form" onSubmit={guardar}><label>Nombre completo<input autoComplete="name" value={nombre} onChange={(event) => setNombre(event.target.value)} maxLength={100} required /></label><label>Correo electrónico<input value={usuario?.email ?? ""} disabled /></label><label>Teléfono<input type="tel" autoComplete="tel" value={telefono} onChange={(event) => setTelefono(event.target.value)} placeholder="Opcional" /></label><button className="primary-button">Guardar cambios</button></form></div><div className="profile-card"><div className="content-heading"><div><h2>Seguridad</h2><p>Cambiar la contraseña invalida las demás sesiones.</p></div></div><form className="profile-form" onSubmit={cambiarClave}><label>Contraseña actual<input type="password" autoComplete="current-password" value={actual} onChange={(event) => setActual(event.target.value)} required /></label><label>Nueva contraseña<input type="password" autoComplete="new-password" minLength={8} maxLength={72} value={nueva} onChange={(event) => setNueva(event.target.value)} required /></label><label>Confirmar contraseña<input type="password" autoComplete="new-password" minLength={8} maxLength={72} value={confirmacion} onChange={(event) => setConfirmacion(event.target.value)} required /></label><button className="primary-button">Cambiar contraseña</button></form><button className="danger-link" onClick={cerrarTodo}>Cerrar sesiones en todos los dispositivos</button></div></div></section></PortalShell>
}

export function Admin() {
  return <PortalShell eyebrow="Gestión interna" title="Panel de moderación"><div className="adapted-admin"><PanelAdmin /></div></PortalShell>
}
