import { useEffect, useState, type FormEvent } from "react"
import { useNavigate } from "react-router"
import * as barriosApi from "../api/barrios"
import * as categoriasApi from "../api/categorias"
import * as reportesApi from "../api/reportes"
import type { BarrioResponse, CategoriaResponse, PrioridadReporte } from "../api/types"
import { Icon } from "../app/components/UI"
import { MapaSeleccionPunto } from "../components/MapaSeleccionPunto"

export function CrearReporte() {
  const navigate = useNavigate()
  const [barrios, setBarrios] = useState<BarrioResponse[]>([])
  const [categorias, setCategorias] = useState<CategoriaResponse[]>([])
  const [calleId, setCalleId] = useState("")
  const [categoriaId, setCategoriaId] = useState("")
  const [descripcion, setDescripcion] = useState("")
  const [fotoUrl, setFotoUrl] = useState("")
  const [prioridad, setPrioridad] = useState<PrioridadReporte>("MEDIA")
  const [coordenadas, setCoordenadas] = useState<[number, number]>()
  const [error, setError] = useState("")
  const [cargando, setCargando] = useState(true)
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    Promise.all([barriosApi.listar(), categoriasApi.listar()])
      .then(([barriosResponse, categoriasResponse]) => {
        setBarrios(barriosResponse)
        setCategorias(categoriasResponse)
      })
      .catch((exception) => setError(exception instanceof Error ? exception.message : "No fue posible cargar las opciones."))
      .finally(() => setCargando(false))
  }, [])

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
        calleId,
        categoriaId,
        descripcion,
        ...(fotoUrl.trim() ? { fotoUrl: fotoUrl.trim() } : {}),
        latitud: coordenadas[0],
        longitud: coordenadas[1],
        prioridad,
      })
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
            <label>Calle
              <select value={calleId} onChange={(event) => setCalleId(event.target.value)} disabled={cargando} required>
                <option value="">Selecciona una calle</option>
                {barrios.map((barrio) => <optgroup key={barrio.id} label={barrio.nombre}>{barrio.calles.map((calle) => <option key={calle.id} value={calle.id}>{calle.nombre}</option>)}</optgroup>)}
              </select>
            </label>
            <label>Categoría
              <select value={categoriaId} onChange={(event) => setCategoriaId(event.target.value)} disabled={cargando} required>
                <option value="">Selecciona una categoría</option>
                {categorias.map((categoria) => <option key={categoria.id} value={categoria.id}>{categoria.nombre}</option>)}
              </select>
            </label>
            <label className="field-wide">Descripción
              <textarea minLength={10} maxLength={2000} value={descripcion} onChange={(event) => setDescripcion(event.target.value)} placeholder="Describe el daño, referencias cercanas y cualquier detalle útil…" required />
              <small>{descripcion.length}/2000 caracteres</small>
            </label>
            <label>URL de la foto <small>(opcional)</small>
              <input type="url" value={fotoUrl} onChange={(event) => setFotoUrl(event.target.value)} placeholder="https://…" />
            </label>
            <label>Prioridad
              <select value={prioridad} onChange={(event) => setPrioridad(event.target.value as PrioridadReporte)}>
                <option value="BAJA">Baja</option><option value="MEDIA">Media</option><option value="ALTA">Alta</option>
              </select>
            </label>
          </div>
        </section>
        <section className="form-card">
          <div className="form-card-heading"><span>2</span><div><h2>Ubica el daño</h2><p>Haz clic sobre el mapa o usa la ubicación de tu dispositivo.</p></div></div>
          <MapaSeleccionPunto onSeleccionar={(lat, lng) => setCoordenadas([lat, lng])} puntoInicial={coordenadas} />
          {coordenadas && <p className="coordinate"><Icon name="location" /> Punto seleccionado: {coordenadas[0].toFixed(5)}, {coordenadas[1].toFixed(5)}</p>}
        </section>
        {error && <p className="alert error" role="alert">{error}</p>}
        <div className="form-actions">
          <button className="button button-ghost" type="button" onClick={() => navigate(-1)}>Cancelar</button>
          <button className="button button-primary" type="submit" disabled={cargando || enviando}>{enviando ? "Enviando…" : "Publicar reporte"} <Icon name="arrow" /></button>
        </div>
      </form>
    </main>
  )
}
