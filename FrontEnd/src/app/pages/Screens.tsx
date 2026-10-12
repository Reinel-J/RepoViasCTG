import { useEffect, useMemo, useRef, useState, type ChangeEvent, type FormEvent, type ReactNode } from "react"
import { NavLink, useLocation, useNavigate, useParams, useSearchParams } from "react-router"
import { cargarReporte, cargarReportes, fotoReporte, textoEstado, type Reporte } from "../../api/reporteVista"
import * as authApi from "../../api/auth"
import * as comentariosApi from "../../api/comentarios"
import * as confirmacionesApi from "../../api/confirmaciones"
import * as geocodingApi from "../../api/geocoding"
import * as notificacionesApi from "../../api/notificaciones"
import * as reportesApi from "../../api/reportes"
import * as seguimientoApi from "../../api/seguimiento"
import * as usuariosApi from "../../api/usuarios"
import type { ComentarioResponse, ConfirmacionResponse, NotificacionResponse, PosibleDuplicadoResponse, ReporteResponse, SeguimientoResumenResponse, UsuarioResponse } from "../../api/types"
import { useAuth } from "../../context/useAuth"
import { MapaReportes } from "../../components/MapaReportes"
import { MapaSeleccionPunto } from "../../components/MapaSeleccionPunto"
import { PanelAdmin } from "../../pages/admin/PanelAdmin"

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

function PortalShell({ children, title, eyebrow, action = true }: { children: ReactNode; title?: string; eyebrow?: string; action?: boolean }) {
  const [menu, setMenu] = useState(false)
  const { session, logout } = useAuth()
  return (
    <div className="portal-shell">
      <div className="top-ribbon"><span>Cartagena de Indias</span><span className="ribbon-dot" /><span>La calle también es nuestra</span></div>
      <header className="site-header portal-header">
        <Logo />
        <nav className={menu ? "main-nav open" : "main-nav"}>
          <NavLink to="/">Inicio</NavLink>
          <NavLink to="/reportes">Reportes</NavLink>
          <NavLink to="/mapa">Mapa ciudadano</NavLink>
          {(session?.rol === "ADMIN" || session?.rol === "MODERADOR") && <NavLink to="/admin">Panel de gestión</NavLink>}
        </nav>
        <div className="header-actions">
          {session && <NavLink className="icon-button" to="/notificaciones" aria-label="Notificaciones"><Icon name="bell" /></NavLink>}
          {action && <NavLink className="login-button nav-action" to="/reportes/nuevo"><Icon name="plus" size={16} /> Reportar</NavLink>}
          {session ? <button className="session-button" onClick={logout}>{session.nombre} · Salir</button> : <NavLink className="session-button" to="/login">Iniciar sesión</NavLink>}
          <button className="menu-button" onClick={() => setMenu(!menu)} aria-label="Abrir menú"><Icon name="menu" /></button>
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
      setError(exception instanceof Error ? exception.message : "No fue posible cargar los reportes.")
    }).finally(() => setCargando(false))
  }, [])
  return { datos, conectado, cargando, error }
}

function ReportCard({ reporte }: { reporte: Reporte }) {
  const foto = fotoReporte(reporte)
  return (
    <article className="report-card">
      <div className="card-image-wrap">
        {foto ? <img src={foto} alt={`Evidencia del reporte ${reporte.categoria}`} /> : <div className="no-photo"><Icon name="camera" size={26} /><span>Sin fotografía</span></div>}
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
      </div>
    </article>
  )
}

export function Reportes() {
  const { datos, conectado, cargando, error } = useReportes()
  const [estado, setEstado] = useState("TODOS")
  const [busqueda, setBusqueda] = useState("")
  const visibles = useMemo(() => datos.filter((reporte) =>
    (estado === "TODOS" || reporte.estadoRaw === estado) &&
    `${reporte.categoria} ${reporte.lugar}`.toLowerCase().includes(busqueda.toLowerCase())
  ), [datos, estado, busqueda])

  return (
    <PortalShell eyebrow="La voz de los barrios" title="Reportes ciudadanos">
      <section className="listing-section">
        <div className="listing-toolbar">
          <div className="search-field"><Icon name="search" size={18} /><input value={busqueda} onChange={(e) => setBusqueda(e.target.value)} placeholder="Buscar por barrio, calle o categoría" /></div>
          <div className="select-field"><Icon name="filter" size={17} /><select value={estado} onChange={(e) => setEstado(e.target.value)}><option value="TODOS">Todos los estados</option><option value="PENDIENTE">Pendientes</option><option value="EN_REVISION">En revisión</option><option value="EN_PROCESO">En proceso</option><option value="RESUELTO">Resueltos</option><option value="RECHAZADO">Rechazados</option></select></div>
          <NavLink className="primary-button compact" to="/reportes/nuevo"><Icon name="plus" /> Nuevo reporte</NavLink>
        </div>
        <div className="connection-note"><span className={conectado ? "live-dot" : "demo-dot"} />{conectado ? `${visibles.length} reportes obtenidos del servicio ciudadano` : "Esperando conexión con el servicio ciudadano"}</div>
        {cargando && <div className="loading-panel">Cargando reportes…</div>}
        {error && <div className="alert-panel error-panel">{error}</div>}
        <div className="report-grid listing-grid">{visibles.map((reporte) => <ReportCard reporte={reporte} key={reporte.id} />)}</div>
        {!visibles.length && <div className="empty-panel"><Icon name="map" size={30} /><h2>No encontramos reportes</h2><p>Prueba con otra búsqueda o crea el primer reporte de este sector.</p></div>}
      </section>
    </PortalShell>
  )
}

export function DetalleReporte() {
  const { id = "" } = useParams()
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
  useEffect(() => {
    if (!id) return
    Promise.all([
      cargarReporte(id),
      comentariosApi.listar(id),
      confirmacionesApi.listar(id),
      seguimientoApi.obtener(id),
    ]).then(([report, comments, confirmations, followup]) => {
      setReporte(report)
      setComentarios(comments)
      setConfirmaciones(confirmations)
      setSeguimiento(followup)
    }).catch((exception) => setError(exception instanceof Error ? exception.message : "No fue posible cargar el reporte."))
      .finally(() => setCargando(false))
  }, [id, session])

  async function confirmar() {
    if (!session) {
      navigate("/login")
      return
    }
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
      setError(exception instanceof Error ? exception.message : "No fue posible confirmar el reporte.")
    }
  }

  async function responder(arreglado: boolean) {
    if (!session) {
      navigate("/login")
      return
    }
    try {
      setSeguimiento(await seguimientoApi.responder(id, arreglado))
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible guardar tu respuesta.")
    }
  }

  async function comentar(event: FormEvent) {
    event.preventDefault()
    if (!texto.trim()) return
    try {
      const nuevo = await comentariosApi.crear(id, texto.trim())
      setComentarios((actuales) => [...actuales, nuevo])
      setTexto("")
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible publicar el comentario.")
    }
  }

  async function editarComentario(comentario: ComentarioResponse) {
    const textoNuevo = window.prompt("Edita tu comentario", comentario.texto)?.trim()
    if (!textoNuevo || textoNuevo === comentario.texto) return
    try {
      const actualizado = await comentariosApi.actualizar(comentario.id, textoNuevo)
      setComentarios((actuales) => actuales.map((item) => item.id === actualizado.id ? actualizado : item))
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible editar el comentario.")
    }
  }

  async function eliminarComentario(comentario: ComentarioResponse) {
    if (!window.confirm("¿Eliminar este comentario?")) return
    try {
      await comentariosApi.eliminar(comentario.id)
      setComentarios((actuales) => actuales.filter((item) => item.id !== comentario.id))
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible eliminar el comentario.")
    }
  }

  if (cargando) return <PortalShell title="Cargando reporte"><div className="loading-panel">Consultando la información…</div></PortalShell>
  if (!reporte) return <PortalShell title="Reporte no encontrado"><div className="alert-panel error-panel">{error}</div></PortalShell>
  const foto = fotoReporte(reporte)
  const historial = [...reporte.historialEstados].sort((a, b) => new Date(a.fecha).getTime() - new Date(b.fecha).getTime())
  const cerrado = reporte.estadoRaw === "RESUELTO" || reporte.estadoRaw === "RECHAZADO"

  return (
    <PortalShell eyebrow={`Reporte · ${reporte.id}`} title={reporte.categoria}>
      <section className="detail-layout">
        {aviso && <div className="alert-panel error-panel detail-notice" role="alert">{aviso}</div>}
        <div className="detail-primary">
          <div className="detail-photo">
            {foto ? <img src={foto} alt={`Evidencia de ${reporte.categoria}`} /> : <div className="no-photo"><Icon name="camera" size={34} /><span>Sin fotografía</span></div>}
          </div>
          <div className="detail-copy">
            <div className="detail-title-row"><div><span className="section-kicker">Descripción ciudadana</span><h2>{reporte.descripcion}</h2>{session?.usuarioId === reporte.usuarioId && reporte.estadoRaw === "PENDIENTE" && <NavLink className="edit-report-link" to={`/reportes/${reporte.id}/editar`}>Editar este reporte</NavLink>}</div><span className={`large-status ${reporte.estadoClase}`}>{reporte.estado}</span></div>
            <div className="detail-address"><Icon name="location" /><div><small>Ubicación reportada</small><strong>{reporte.lugar}</strong></div></div>
            <div className="real-map-wrap">{reporte.latitud != null && reporte.longitud != null && <MapaReportes reportes={[{
              id: reporte.id, usuarioId: "", calleId: null, categoriaId: reporte.categoria, direccionOsm: reporte.direccionOsm,
              descripcion: reporte.descripcion, fotos: reporte.fotos, latitud: reporte.latitud, longitud: reporte.longitud,
              estado: reporte.estadoRaw, prioridad: reporte.prioridad, fechaCreacion: reporte.fechaCreacion,
              fechaActualizacion: reporte.fechaCreacion, historialEstados: reporte.historialEstados,
            }]} />}</div>
            <section className="comments-panel">
              <div className="content-heading"><div><h3>Conversación ciudadana</h3><p>{comentarios.length} comentario(s)</p></div></div>
              {comentarios.map((comentario) => <article className="comment" key={comentario.id}><span><Icon name="user" /></span><div><strong>{comentario.usuarioNombre ?? "Miembro de la comunidad"}</strong><p>{comentario.texto}</p><small>{new Date(comentario.fecha).toLocaleString("es-CO")}{comentario.editado ? " · editado" : ""}</small>{session?.usuarioId === comentario.usuarioId && <div className="comment-actions"><button onClick={() => editarComentario(comentario)}>Editar</button><button onClick={() => eliminarComentario(comentario)}>Eliminar</button></div>}</div></article>)}
              {session ? <form className="comment-form" onSubmit={comentar}><textarea maxLength={1000} value={texto} onChange={(event) => setTexto(event.target.value)} placeholder="Comparte información útil…" required /><button className="primary-button compact">Comentar</button></form> : <p className="signin-note"><NavLink to="/login">Inicia sesión</NavLink> para comentar.</p>}
            </section>
          </div>
        </div>
        <aside className="detail-sidebar">
          {error && <div className="alert-panel error-panel">{error}</div>}
          <div className="community-card"><span className="section-kicker">La comunidad dice</span><h3>¿Este problema sigue igual?</h3><div className="vote-count"><strong>{confirmaciones.length}</strong><span>personas lo confirman</span></div><button className="primary-button full" onClick={confirmar}><Icon name="check" /> {session && confirmaciones.some((item) => item.usuarioId === session.usuarioId) ? "Retirar confirmación" : "Confirmar reporte"}</button></div>
          {seguimiento && !cerrado && <div className="community-card"><span className="section-kicker">¿Ya lo arreglaron?</span><h3>{seguimiento.arreglado} dicen que sí · {seguimiento.sigue} que sigue igual</h3><div className="followup-actions"><button className={seguimiento.miRespuesta === true ? "selected" : ""} onClick={() => responder(true)}>Ya está arreglado</button><button className={seguimiento.miRespuesta === false ? "selected" : ""} onClick={() => responder(false)}>Sigue igual</button></div></div>}
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

export function CrearReporte() {
  const navigate = useNavigate()
  const [paso, setPaso] = useState(1)
  const [fotos, setFotos] = useState<{ archivo: File; preview: string }[]>([])
  const [coordenadas, setCoordenadas] = useState<[number, number]>()
  const [direccion, setDireccion] = useState<string | null>(null)
  const [categoria, setCategoria] = useState("")
  const [descripcion, setDescripcion] = useState("")
  const [duplicados, setDuplicados] = useState<PosibleDuplicadoResponse[]>([])
  const [error, setError] = useState("")
  const [enviando, setEnviando] = useState(false)
  const [avisoFotos, setAvisoFotos] = useState("")
  const previews = useRef<string[]>([])

  useEffect(() => () => previews.current.forEach(URL.revokeObjectURL), [])
  useEffect(() => {
    if (!coordenadas) return
    geocodingApi.inverso(coordenadas[0], coordenadas[1]).then((dato) => setDireccion(dato.direccionOsm)).catch(() => setDireccion(null))
    reportesApi.posiblesDuplicados(coordenadas[0], coordenadas[1]).then(setDuplicados).catch(() => setDuplicados([]))
  }, [coordenadas])

  function cargarFotos(event: ChangeEvent<HTMLInputElement>) {
    const archivos = Array.from(event.target.files ?? [])
    // Permite volver a elegir el mismo archivo después de quitarlo.
    event.target.value = ""
    const avisos: string[] = []
    const imagenes = archivos.filter((archivo) => ["image/jpeg", "image/png", "image/webp"].includes(archivo.type))
    if (imagenes.length < archivos.length) avisos.push("Solo se permiten fotografías JPEG, PNG o WEBP.")
    const pesadas = imagenes.filter((archivo) => archivo.size > MAX_BYTES_FOTO)
    if (pesadas.length) avisos.push(`${pesadas.map((archivo) => archivo.name).join(", ")} ${pesadas.length === 1 ? "pesa" : "pesan"} más de 5 MB y no se ${pesadas.length === 1 ? "agregó" : "agregaron"}. Elige una foto más liviana.`)
    const validas = imagenes.filter((archivo) => archivo.size <= MAX_BYTES_FOTO)
    const cupo = MAX_FOTOS - fotos.length
    if (validas.length > cupo) avisos.push(`Solo puedes adjuntar ${MAX_FOTOS} fotografías en total: ${validas.length - cupo === 1 ? "se descartó 1" : `se descartaron ${validas.length - cupo}`}.`)
    const nuevas = validas.slice(0, cupo).map((archivo) => ({ archivo, preview: URL.createObjectURL(archivo) }))
    previews.current.push(...nuevas.map((foto) => foto.preview))
    setFotos((actuales) => [...actuales, ...nuevas])
    setAvisoFotos(avisos.join(" "))
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
    if (paso === 2 && (!categoria.trim() || descripcion.trim().length < 10)) {
      setError("Escribe una categoría y una descripción de al menos 10 caracteres.")
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
          aviso = `El reporte se creó, pero no se pudieron subir las fotos: ${exception instanceof Error ? exception.message : "error desconocido"}`
        }
      }
      navigate(`/reportes/${creado.id}`, { state: aviso ? { aviso } : undefined })
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear el reporte.")
    } finally {
      setEnviando(false)
    }
  }

  async function confirmarDuplicado(reporteId: string) {
    try {
      await confirmacionesApi.crear(reporteId)
      navigate(`/reportes/${reporteId}`)
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible confirmar el reporte existente.")
    }
  }

  return (
    <PortalShell eyebrow="Tu voz mueve la ciudad" title="Crear un reporte" action={false}>
      <section className="report-form-layout">
        <div className="form-progress">{["Ubicación", "Evidencia", "Revisar"].map((item, index) => <div className={paso >= index + 1 ? "active" : ""} key={item}><span>{paso > index + 1 ? <Icon name="check" size={14} /> : index + 1}</span><strong>{item}</strong></div>)}</div>
        <form className="report-form" onSubmit={enviar}>
          {paso === 1 && <><div className="form-heading"><span>01</span><div><h2>¿Dónde está el problema?</h2><p>Haz clic en el mapa o usa la ubicación de tu dispositivo.</p></div></div><div className="picker-map real-picker"><MapaSeleccionPunto onSeleccionar={(lat, lng) => setCoordenadas([lat, lng])} puntoInicial={coordenadas} /></div>{coordenadas && <div className="coords-summary"><Icon name="location" /><div><strong>{direccion ?? "Buscando la dirección…"}</strong><small>{coordenadas[0].toFixed(5)}, {coordenadas[1].toFixed(5)}</small></div></div>}{duplicados.length > 0 && <div className="duplicates-box"><strong>¿Ya lo reportaron?</strong><p>Encontramos {duplicados.length} reporte(s) activo(s) a menos de 100 metros.</p>{duplicados.slice(0, 3).map((item) => <div className="duplicate-action" key={item.reporte.id}><NavLink to={`/reportes/${item.reporte.id}`}>{item.reporte.categoriaId} · {Math.round(item.distanciaMetros)} m · {item.confirmaciones} confirmaciones</NavLink><button type="button" onClick={() => confirmarDuplicado(item.reporte.id)}>Es el mismo: confirmar</button></div>)}</div>}</>}
          {paso === 2 && <><div className="form-heading"><span>02</span><div><h2>Cuéntanos qué está pasando</h2><p>Una descripción clara y fotos ayudan a gestionar mejor el caso.</p></div></div><div className="form-fields"><label>Categoría<input value={categoria} onChange={(event) => setCategoria(event.target.value)} maxLength={100} placeholder="Ejemplo: Hueco, alumbrado, inundación" required /></label><label>Descripción<textarea rows={5} minLength={10} maxLength={2000} value={descripcion} onChange={(event) => setDescripcion(event.target.value)} placeholder="Describe el problema y alguna referencia para encontrarlo..." required /></label><label className="upload-zone"><Icon name="camera" size={30} /><strong>Agrega hasta 3 fotografías</strong><small>JPEG, PNG o WEBP · máximo 5 MB cada una</small><input type="file" accept="image/jpeg,image/png,image/webp" multiple onChange={cargarFotos} disabled={fotos.length === MAX_FOTOS} /></label>{avisoFotos && <div className="alert-panel error-panel" role="alert">{avisoFotos}</div>}{fotos.length > 0 && <div className="upload-previews">{fotos.map((foto) => <div className="upload-preview" key={foto.preview}><img src={foto.preview} alt={`Vista previa de ${foto.archivo.name}`} /><button type="button" onClick={() => quitarFoto(foto.preview)} aria-label={`Quitar ${foto.archivo.name}`}>×</button></div>)}</div>}</div></>}
          {paso === 3 && <><div className="form-heading"><span>03</span><div><h2>Revisa antes de publicar</h2><p>Tu reporte será visible para toda la comunidad.</p></div></div><div className="review-card"><div className="review-image">{fotos[0] ? <img src={fotos[0].preview} alt="Evidencia seleccionada" /> : <div><Icon name="camera" /><span>Sin fotografías</span></div>}</div><div><span className="category-chip coral">{categoria}</span><h3>{direccion ?? "Ubicación seleccionada en Cartagena"}</h3><p>{descripcion}</p></div></div></>}
          {error && <div className="alert-panel error-panel">{error}</div>}
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
    }).catch((exception) => setError(exception instanceof Error ? exception.message : "No fue posible cargar el reporte."))
  }, [id])

  async function guardar(event: FormEvent) {
    event.preventDefault()
    if (!coordenadas) return
    setGuardando(true)
    try {
      await reportesApi.actualizar(id, { descripcion: descripcion.trim(), latitud: coordenadas[0], longitud: coordenadas[1] })
      navigate(`/reportes/${id}`)
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar el reporte.")
    } finally {
      setGuardando(false)
    }
  }

  return <PortalShell eyebrow="Reporte pendiente" title="Editar reporte"><section className="report-form-layout">{error && <div className="alert-panel error-panel">{error}</div>}{reporte && <form className="report-form" onSubmit={guardar}><div className="form-heading"><span>01</span><div><h2>Actualiza la información</h2><p>Solo los reportes pendientes pueden editarse.</p></div></div><div className="form-fields"><label>Categoría<input value={reporte.categoriaId} disabled /></label><label>Descripción<textarea minLength={10} maxLength={2000} rows={6} value={descripcion} onChange={(event) => setDescripcion(event.target.value)} required /></label></div><div className="picker-map real-picker"><MapaSeleccionPunto puntoInicial={coordenadas} onSeleccionar={(lat, lng) => setCoordenadas([lat, lng])} /></div><div className="form-actions"><button type="button" className="ghost-button" onClick={() => navigate(-1)}>Cancelar</button><button className="primary-button" disabled={guardando}>{guardando ? "Guardando…" : "Guardar cambios"}</button></div></form>}</section></PortalShell>
}

export function Mapa() {
  const [carga, setCarga] = useState<{ fase: "cargando" } | { fase: "error"; mensaje: string } | { fase: "cargado"; reportes: ReporteResponse[] }>({ fase: "cargando" })
  useEffect(() => {
    reportesApi.listar()
      .then((reportes) => setCarga({ fase: "cargado", reportes }))
      .catch((exception) => setCarga({ fase: "error", mensaje: exception instanceof Error ? exception.message : "No fue posible cargar el mapa." }))
  }, [])
  const reportes = carga.fase === "cargado" ? carga.reportes : []
  return (
    <PortalShell eyebrow="Cartagena en tiempo real" title="Mapa ciudadano">
      <section className="full-map-layout">
        <aside className="map-sidebar">
          <div className="map-filter-title"><strong>Reportes visibles</strong><span>{reportes.length}</span></div>
          {carga.fase === "cargando" && <p className="map-sidebar-note">Cargando reportes…</p>}
          {carga.fase === "error" && <div className="alert-panel error-panel">{carga.mensaje}</div>}
          {carga.fase === "cargado" && reportes.length === 0 && <p className="map-sidebar-note">Aún no hay reportes en el mapa.</p>}
          {reportes.map((reporte) => <NavLink to={`/reportes/${reporte.id}`} className="map-list-item" key={reporte.id}><span className={`map-list-dot ${reporte.estado === "RESUELTO" ? "done" : reporte.estado === "EN_PROCESO" ? "process" : ""}`} /><div><strong>{reporte.categoriaId}</strong><small>{reporte.direccionOsm ?? `${reporte.latitud.toFixed(4)}, ${reporte.longitud.toFixed(4)}`}</small></div><Icon name="arrow" size={16} /></NavLink>)}
        </aside>
        <div className="interactive-map real-full-map">
          {carga.fase === "cargando" && <div className="loading-panel">Cargando mapa…</div>}
          {carga.fase === "error" && <div className="loading-panel">No pudimos cargar el mapa. {carga.mensaje}</div>}
          {carga.fase === "cargado" && <MapaReportes reportes={reportes} />}
          {carga.fase === "cargado" && reportes.length === 0 && <div className="map-empty-note"><Icon name="map" size={15} /> Aún no hay reportes en el mapa</div>}
        </div>
      </section>
    </PortalShell>
  )
}

export function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
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
      navigate("/")
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible iniciar sesión.")
    } finally {
      setEnviando(false)
    }
  }
  return (
    <div className="auth-page">
      <div className="auth-image"><NavLink to="/" className="auth-back">← Volver al inicio</NavLink><div><span>Cartagena se mueve</span><h1>Tu voz también<br />abre caminos.</h1><p>Únete a una comunidad que cuida cada calle, cada barrio y cada trayecto.</p></div></div>
      <div className="auth-panel"><Logo /><form className="auth-form" onSubmit={enviar}><span className="section-kicker">Bienvenido de vuelta</span><h2>Inicia sesión</h2><p>Consulta tus reportes y sigue sus avances.</p><label>Correo electrónico<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="tu@correo.com" required /></label><label>Contraseña<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="••••••••" required /></label><div className="form-inline"><span /><NavLink to="/recuperar">¿Olvidaste tu contraseña?</NavLink></div>{error && <div className="alert-panel error-panel">{error}</div>}<button className="primary-button full" disabled={enviando}>{enviando ? "Entrando…" : "Entrar a VíaCTG"} <Icon name="arrow" /></button><span className="auth-switch">¿Aún no tienes cuenta? <NavLink to="/registro">Crear cuenta</NavLink></span></form></div>
    </div>
  )
}

export function Registro() {
  const { registro } = useAuth()
  const navigate = useNavigate()
  const [nombre, setNombre] = useState("")
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState("")
  async function enviar(event: FormEvent) {
    event.preventDefault()
    try {
      await registro(nombre, email, password)
      navigate("/")
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear la cuenta.")
    }
  }
  return <div className="auth-page"><div className="auth-image"><NavLink to="/" className="auth-back">← Volver al inicio</NavLink><div><span>Súmate a la comunidad</span><h1>Una calle mejor empieza contigo.</h1><p>Reporta, confirma y sigue los problemas de tu barrio.</p></div></div><div className="auth-panel"><Logo /><form className="auth-form" onSubmit={enviar}><span className="section-kicker">Nueva cuenta</span><h2>Únete a VíaCTG</h2><label>Nombre completo<input value={nombre} onChange={(event) => setNombre(event.target.value)} maxLength={100} required /></label><label>Correo electrónico<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required /></label><label>Contraseña<input type="password" minLength={8} maxLength={72} value={password} onChange={(event) => setPassword(event.target.value)} required /></label>{error && <div className="alert-panel error-panel">{error}</div>}<button className="primary-button full">Crear mi cuenta <Icon name="arrow" /></button><span className="auth-switch">¿Ya tienes cuenta? <NavLink to="/login">Inicia sesión</NavLink></span></form></div></div>
}

export function RecuperarPassword() {
  const [email, setEmail] = useState("")
  const [enviado, setEnviado] = useState(false)
  const [error, setError] = useState("")
  async function enviar(event: FormEvent) {
    event.preventDefault()
    try {
      await authApi.recuperar(email)
      setEnviado(true)
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible procesar la solicitud.")
    }
  }
  return <div className="auth-page"><div className="auth-image"><NavLink to="/login" className="auth-back">← Volver</NavLink><div><span>Recupera tu acceso</span><h1>Volvamos a ponerte en camino.</h1></div></div><div className="auth-panel"><Logo /><form className="auth-form" onSubmit={enviar}><span className="section-kicker">Contraseña</span><h2>Recuperar acceso</h2>{enviado ? <div className="success-message">Si existe una cuenta con {email}, recibirás un enlace válido por 30 minutos.</div> : <><p>Escribe el correo con el que te registraste.</p><label>Correo electrónico<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required /></label>{error && <div className="alert-panel error-panel">{error}</div>}<button className="primary-button full">Enviar enlace <Icon name="arrow" /></button></>}</form></div></div>
}

export function RestablecerPassword() {
  const [params] = useSearchParams()
  const token = params.get("token") ?? ""
  const [password, setPassword] = useState("")
  const [confirmacion, setConfirmacion] = useState("")
  const [listo, setListo] = useState(false)
  const [error, setError] = useState("")
  async function enviar(event: FormEvent) {
    event.preventDefault()
    if (password !== confirmacion) return setError("Las contraseñas no coinciden.")
    try {
      await authApi.restablecer(token, password)
      setListo(true)
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar la contraseña.")
    }
  }
  return <div className="auth-page"><div className="auth-image"><div><span>Seguridad</span><h1>Crea una nueva contraseña.</h1></div></div><div className="auth-panel"><Logo /><form className="auth-form" onSubmit={enviar}><span className="section-kicker">Nueva contraseña</span><h2>Restablecer acceso</h2>{listo ? <><div className="success-message">Tu contraseña se actualizó correctamente.</div><NavLink className="primary-button full" to="/login">Iniciar sesión</NavLink></> : !token ? <div className="alert-panel error-panel">El enlace está incompleto.</div> : <><label>Contraseña nueva<input type="password" minLength={8} maxLength={72} value={password} onChange={(event) => setPassword(event.target.value)} required /></label><label>Confirmar contraseña<input type="password" minLength={8} maxLength={72} value={confirmacion} onChange={(event) => setConfirmacion(event.target.value)} required /></label>{error && <div className="alert-panel error-panel">{error}</div>}<button className="primary-button full">Guardar contraseña</button></>}</form></div></div>
}

export function Notificaciones() {
  const [items, setItems] = useState<NotificacionResponse[]>([])
  const [error, setError] = useState("")
  useEffect(() => {
    notificacionesApi.listar().then(setItems).catch((exception) => setError(exception instanceof Error ? exception.message : "No fue posible cargar las notificaciones."))
  }, [])
  async function leer(id: string) {
    try {
      const actualizada = await notificacionesApi.marcarLeida(id)
      setItems((actuales) => actuales.map((item) => item.id === id ? actualizada : item))
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar la notificación.")
    }
  }
  return <PortalShell eyebrow="Mantente al día" title="Notificaciones"><section className="account-layout"><aside className="account-nav"><NavLink to="/perfil"><Icon name="user" /> Mi perfil</NavLink><NavLink className="active" to="/notificaciones"><Icon name="bell" /> Notificaciones</NavLink></aside><div className="notification-list"><div className="content-heading"><div><h2>Novedades de tus reportes</h2><p>{items.filter((item) => !item.leida).length} sin leer</p></div></div>{error && <div className="alert-panel error-panel">{error}</div>}{items.map((item) => <article className={!item.leida ? "notification unread" : "notification"} key={item.id}><span className="notification-icon"><Icon name="bell" /></span><div><strong>{item.mensaje}</strong>{item.reporteId && <NavLink to={`/reportes/${item.reporteId}`}>Ver reporte</NavLink>}<small>{new Date(item.fecha).toLocaleString("es-CO")}</small></div>{!item.leida && <button className="read-button" onClick={() => leer(item.id)}>Marcar leída</button>}</article>)}{!items.length && !error && <div className="empty-panel"><h2>Estás al día</h2><p>Te avisaremos cuando cambien tus reportes.</p></div>}</div></section></PortalShell>
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
    usuariosApi.perfil().then((dato) => { setUsuario(dato); setNombre(dato.nombre); setTelefono(dato.telefono ?? "") }).catch((exception) => setError(exception instanceof Error ? exception.message : "No fue posible cargar el perfil."))
  }, [])
  async function guardar(event: FormEvent) {
    event.preventDefault()
    try {
      const actualizado = await usuariosApi.actualizarPerfil({ nombre: nombre.trim(), telefono: telefono.trim() || undefined })
      setUsuario(actualizado)
      actualizarNombre(actualizado.nombre)
      setMensaje("Perfil actualizado.")
    } catch (exception) { setError(exception instanceof Error ? exception.message : "No fue posible actualizar el perfil.") }
  }
  async function cambiarClave(event: FormEvent) {
    event.preventDefault()
    if (nueva !== confirmacion) return setError("Las contraseñas nuevas no coinciden.")
    try {
      renovarSesion(await usuariosApi.cambiarPassword(actual, nueva))
      setActual(""); setNueva(""); setConfirmacion(""); setMensaje("Contraseña actualizada y otras sesiones cerradas.")
    } catch (exception) { setError(exception instanceof Error ? exception.message : "No fue posible cambiar la contraseña.") }
  }
  async function cerrarTodo() {
    await usuariosApi.cerrarSesiones()
    logout()
    navigate("/login")
  }
  return <PortalShell eyebrow="Tu espacio ciudadano" title="Mi perfil"><section className="account-layout"><aside className="account-nav"><NavLink className="active" to="/perfil"><Icon name="user" /> Mi perfil</NavLink><NavLink to="/notificaciones"><Icon name="bell" /> Notificaciones</NavLink></aside><div className="profile-stack">{error && <div className="alert-panel error-panel">{error}</div>}{mensaje && <div className="success-message">{mensaje}</div>}<div className="profile-card"><div className="profile-intro"><div className="avatar">{nombre.slice(0, 2).toUpperCase()}</div><div><h2>{usuario?.nombre ?? "Cargando…"}</h2><span>{usuario?.rol} · {usuario?.email}</span></div></div><form className="profile-form" onSubmit={guardar}><label>Nombre completo<input value={nombre} onChange={(event) => setNombre(event.target.value)} required /></label><label>Correo electrónico<input value={usuario?.email ?? ""} disabled /></label><label>Teléfono<input value={telefono} onChange={(event) => setTelefono(event.target.value)} /></label><button className="primary-button">Guardar cambios</button></form></div><div className="profile-card"><div className="content-heading"><div><h2>Seguridad</h2><p>Cambiar la contraseña invalida las demás sesiones.</p></div></div><form className="profile-form" onSubmit={cambiarClave}><label>Contraseña actual<input type="password" value={actual} onChange={(event) => setActual(event.target.value)} required /></label><label>Nueva contraseña<input type="password" minLength={8} maxLength={72} value={nueva} onChange={(event) => setNueva(event.target.value)} required /></label><label>Confirmar contraseña<input type="password" minLength={8} maxLength={72} value={confirmacion} onChange={(event) => setConfirmacion(event.target.value)} required /></label><button className="primary-button">Cambiar contraseña</button></form><button className="danger-link" onClick={cerrarTodo}>Cerrar sesiones en todos los dispositivos</button></div></div></section></PortalShell>
}

export function Admin() {
  return <PortalShell eyebrow="Gestión interna" title="Panel de moderación"><div className="adapted-admin"><PanelAdmin /></div></PortalShell>
}
