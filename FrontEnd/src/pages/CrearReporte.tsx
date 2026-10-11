import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from "react"
import { Link, useNavigate } from "react-router"
import * as confirmacionesApi from "../api/confirmaciones"
import * as geocodingApi from "../api/geocoding"
import * as reportesApi from "../api/reportes"
import type { PosibleDuplicadoResponse } from "../api/types"
import { Icon, StatusBadge } from "../app/components/UI"
import { MapaSeleccionPunto } from "../components/MapaSeleccionPunto"
import { useAuth } from "../context/useAuth"

interface FotoSeleccionada {
  archivo: File
  previsualizacion: string
}

const TIPOS_IMAGEN_PERMITIDOS = ["image/jpeg", "image/png", "image/webp"]

// La categoría es texto libre: "Hueco", "hueco " y "huéco" deben contar como la misma.
function normalizarCategoria(categoria: string) {
  return categoria.trim().toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "")
}

function resumir(texto: string) {
  return texto.length > 110 ? `${texto.slice(0, 110)}…` : texto
}

export function CrearReporte() {
  const navigate = useNavigate()
  const { session } = useAuth()
  const [categoriaId, setCategoriaId] = useState("")
  const [descripcion, setDescripcion] = useState("")
  const [fotos, setFotos] = useState<FotoSeleccionada[]>([])
  const [coordenadas, setCoordenadas] = useState<[number, number]>()
  const [direccionOsm, setDireccionOsm] = useState<string | null>(null)
  const [duplicados, setDuplicados] = useState<PosibleDuplicadoResponse[]>([])
  const [confirmandoId, setConfirmandoId] = useState<string | null>(null)
  const [error, setError] = useState("")
  const [enviando, setEnviando] = useState(false)
  const previsualizaciones = useRef<string[]>([])

  useEffect(() => () => {
    previsualizaciones.current.forEach((url) => URL.revokeObjectURL(url))
  }, [])

  useEffect(() => {
    if (!coordenadas) {
      return
    }
    let vigente = true
    geocodingApi.inverso(coordenadas[0], coordenadas[1])
      .then((response) => vigente && setDireccionOsm(response.direccionOsm))
      .catch(() => vigente && setDireccionOsm(null))
    // Si falla la búsqueda de duplicados no se bloquea el reporte: simplemente no se muestra el aviso.
    reportesApi.posiblesDuplicados(coordenadas[0], coordenadas[1])
      .then((lista) => vigente && setDuplicados(lista))
      .catch(() => vigente && setDuplicados([]))
    return () => { vigente = false }
  }, [coordenadas])

  function seleccionarUbicacion(lat: number, lng: number) {
    setDireccionOsm(null)
    setDuplicados([])
    setCoordenadas([lat, lng])
  }

  async function confirmarExistente(reporteId: string) {
    setError("")
    setConfirmandoId(reporteId)
    try {
      await confirmacionesApi.crear(reporteId)
      navigate(`/reportes/${reporteId}`)
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible confirmar el reporte.")
      setConfirmandoId(null)
    }
  }

  function seleccionarFotos(event: ChangeEvent<HTMLInputElement>) {
    const archivos = Array.from(event.target.files ?? [])
    const archivosValidos = archivos.filter((archivo) => TIPOS_IMAGEN_PERMITIDOS.includes(archivo.type))
    const disponibles = 3 - fotos.length
    const nuevos = archivosValidos.slice(0, disponibles).map((archivo) => ({
      archivo,
      previsualizacion: URL.createObjectURL(archivo),
    }))
    previsualizaciones.current.push(...nuevos.map((foto) => foto.previsualizacion))
    setFotos((actuales) => [...actuales, ...nuevos])
    event.target.value = ""

    if (archivosValidos.length !== archivos.length) {
      setError("Solo se permiten imágenes JPEG, PNG o WEBP.")
    } else if (archivos.length > disponibles) {
      setError("Puedes seleccionar un máximo de 3 fotos.")
    }
  }

  function quitarFoto(previsualizacion: string) {
    URL.revokeObjectURL(previsualizacion)
    previsualizaciones.current = previsualizaciones.current.filter((url) => url !== previsualizacion)
    setFotos((actuales) => actuales.filter((foto) => foto.previsualizacion !== previsualizacion))
  }

  async function manejarEnvio(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!coordenadas) {
      setError("Selecciona la ubicación del daño en el mapa.")
      return
    }
    setError("")
    setEnviando(true)
    try {
      const reporte = await reportesApi.crear({
        categoriaId,
        descripcion,
        latitud: coordenadas[0],
        longitud: coordenadas[1],
      })
      if (fotos.length) {
        await reportesApi.subirFotos(reporte.id, fotos.map((foto) => foto.archivo))
      }
      navigate(`/reportes/${reporte.id}`)
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear el reporte.")
    } finally {
      setEnviando(false)
    }
  }

  return (
    <main className="page-shell section internal-page">
      <div className="page-heading narrow-heading">
        <div><span className="eyebrow">Nuevo reporte</span><h1>Cuéntanos qué está pasando</h1><p>Completa los datos y marca el punto exacto. Entre más claro sea el reporte, más fácil será atenderlo.</p></div>
      </div>
      <form className="report-form" onSubmit={manejarEnvio}>
        <section className="form-card">
          <div className="form-card-heading"><span>1</span><div><h2>Describe el problema</h2><p>Información básica del daño vial.</p></div></div>
          <div className="form-grid">
            <label>Categoría
              <input type="text" value={categoriaId} onChange={(event) => setCategoriaId(event.target.value)} placeholder="Ejemplo: Hueco, alumbrado" required />
              <small>Ejemplo: Hueco, alumbrado</small>
            </label>
            <label className="field-wide">Descripción
              <textarea minLength={10} maxLength={2000} value={descripcion} onChange={(event) => setDescripcion(event.target.value)} placeholder="Describe el daño, referencias cercanas y cualquier detalle útil…" required />
              <small>{descripcion.length}/2000 caracteres</small>
            </label>
            <label className="field-wide">Fotos <small>(opcional, máximo 3)</small>
              <input type="file" accept="image/jpeg,image/png,image/webp" multiple onChange={seleccionarFotos} disabled={fotos.length === 3} />
              <small>Formatos admitidos: JPEG, PNG y WEBP. Máximo 5 MB por foto.</small>
            </label>
            {fotos.length > 0 && <div className="photo-previews field-wide">
              {fotos.map((foto) => <figure key={foto.previsualizacion}>
                <img src={foto.previsualizacion} alt={`Previsualización de ${foto.archivo.name}`} />
                <button type="button" onClick={() => quitarFoto(foto.previsualizacion)} aria-label={`Quitar ${foto.archivo.name}`}>×</button>
              </figure>)}
            </div>}
          </div>
        </section>
        <section className="form-card">
          <div className="form-card-heading"><span>2</span><div><h2>Ubica el daño</h2><p>Haz clic sobre el mapa o usa la ubicación de tu dispositivo.</p></div></div>
          <MapaSeleccionPunto onSeleccionar={seleccionarUbicacion} puntoInicial={coordenadas} />
          {coordenadas && <p className="coordinate"><Icon name="location" /> Punto seleccionado: {coordenadas[0].toFixed(5)}, {coordenadas[1].toFixed(5)}</p>}
          {direccionOsm && <p className="coordinate"><Icon name="road" /> Calle detectada: {direccionOsm}</p>}
        </section>
        {duplicados.length > 0 && <section className="form-card duplicate-panel" aria-live="polite">
          <div className="form-card-heading"><span>!</span><div><h2>¿Ya lo reportaron?</h2><p>Hay {duplicados.length === 1 ? "un reporte activo" : `${duplicados.length} reportes activos`} a menos de 100 m. Si es el mismo daño, confírmalo: un reporte con más confirmaciones gana visibilidad.</p></div></div>
          <ul className="duplicate-list">
            {[...duplicados]
              .sort((a, b) => Number(normalizarCategoria(b.reporte.categoriaId) === normalizarCategoria(categoriaId))
                - Number(normalizarCategoria(a.reporte.categoriaId) === normalizarCategoria(categoriaId)))
              .map(({ reporte, distanciaMetros, confirmaciones }) => {
                const mismaCategoria = categoriaId.trim() !== "" && normalizarCategoria(reporte.categoriaId) === normalizarCategoria(categoriaId)
                const esPropio = reporte.usuarioId === session?.usuarioId
                return <li className={`duplicate-card${mismaCategoria ? " is-match" : ""}`} key={reporte.id}>
                  <div className="duplicate-info">
                    <div className="duplicate-meta">
                      <StatusBadge status={reporte.estado} />
                      <span>{reporte.categoriaId}</span>
                      {mismaCategoria && <strong>Misma categoría</strong>}
                    </div>
                    <p>{resumir(reporte.descripcion)}</p>
                    <small>A {distanciaMetros} m · {confirmaciones} {confirmaciones === 1 ? "confirmación" : "confirmaciones"}{reporte.direccionOsm ? ` · ${reporte.direccionOsm}` : ""}</small>
                  </div>
                  <div className="duplicate-actions">
                    <Link className="button button-ghost button-small" to={`/reportes/${reporte.id}`} target="_blank" rel="noreferrer">Ver reporte</Link>
                    {esPropio
                      ? <span className="muted">Es un reporte tuyo</span>
                      : <button className="button button-primary button-small" type="button" disabled={confirmandoId !== null} onClick={() => confirmarExistente(reporte.id)}>{confirmandoId === reporte.id ? "Confirmando…" : "Es el mismo: confirmar"}</button>}
                  </div>
                </li>
              })}
          </ul>
          <p className="muted">¿Es un daño distinto? Continúa y publica tu reporte normalmente.</p>
        </section>}
        {error && <p className="alert error" role="alert">{error}</p>}
        <div className="form-actions">
          <button className="button button-ghost" type="button" onClick={() => navigate(-1)}>Cancelar</button>
          <button className="button button-primary" type="submit" disabled={enviando}>{enviando ? "Enviando…" : "Publicar reporte"} <Icon name="arrow" /></button>
        </div>
      </form>
    </main>
  )
}
