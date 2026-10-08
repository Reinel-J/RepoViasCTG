import { useEffect, useState, type FormEvent } from "react"
import { useNavigate } from "react-router"
import * as geocodingApi from "../api/geocoding"
import * as reportesApi from "../api/reportes"
import { Icon } from "../app/components/UI"
import { MapaSeleccionPunto } from "../components/MapaSeleccionPunto"

export function CrearReporte() {
  const navigate = useNavigate()
  const [categoriaId, setCategoriaId] = useState("")
  const [descripcion, setDescripcion] = useState("")
  const [fotoUrl, setFotoUrl] = useState("")
  const [coordenadas, setCoordenadas] = useState<[number, number]>()
  const [direccionOsm, setDireccionOsm] = useState<string | null>(null)
  const [error, setError] = useState("")
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    if (!coordenadas) {
      return
    }
    let vigente = true
    setDireccionOsm(null)
    geocodingApi.inverso(coordenadas[0], coordenadas[1])
      .then((response) => vigente && setDireccionOsm(response.direccionOsm))
      .catch(() => vigente && setDireccionOsm(null))
    return () => { vigente = false }
  }, [coordenadas])

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
        ...(fotoUrl.trim() ? { fotoUrl: fotoUrl.trim() } : {}),
        latitud: coordenadas[0],
        longitud: coordenadas[1],
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
            <label>Categoría
              <input type="text" value={categoriaId} onChange={(event) => setCategoriaId(event.target.value)} placeholder="Ejemplo: Hueco, alumbrado" required />
              <small>Ejemplo: Hueco, alumbrado</small>
            </label>
            <label className="field-wide">Descripción
              <textarea minLength={10} maxLength={2000} value={descripcion} onChange={(event) => setDescripcion(event.target.value)} placeholder="Describe el daño, referencias cercanas y cualquier detalle útil…" required />
              <small>{descripcion.length}/2000 caracteres</small>
            </label>
            <label>URL de la foto <small>(opcional)</small>
              <input type="url" value={fotoUrl} onChange={(event) => setFotoUrl(event.target.value)} placeholder="https://…" />
            </label>
          </div>
        </section>
        <section className="form-card">
          <div className="form-card-heading"><span>2</span><div><h2>Ubica el daño</h2><p>Haz clic sobre el mapa o usa la ubicación de tu dispositivo.</p></div></div>
          <MapaSeleccionPunto onSeleccionar={(lat, lng) => setCoordenadas([lat, lng])} puntoInicial={coordenadas} />
          {coordenadas && <p className="coordinate"><Icon name="location" /> Punto seleccionado: {coordenadas[0].toFixed(5)}, {coordenadas[1].toFixed(5)}</p>}
          {direccionOsm && <p className="coordinate"><Icon name="road" /> Calle detectada: {direccionOsm}</p>}
        </section>
        {error && <p className="alert error" role="alert">{error}</p>}
        <div className="form-actions">
          <button className="button button-ghost" type="button" onClick={() => navigate(-1)}>Cancelar</button>
          <button className="button button-primary" type="submit" disabled={enviando}>{enviando ? "Enviando…" : "Publicar reporte"} <Icon name="arrow" /></button>
        </div>
      </form>
    </main>
  )
}
