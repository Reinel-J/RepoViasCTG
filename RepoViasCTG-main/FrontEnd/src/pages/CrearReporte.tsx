import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import * as barriosApi from '../api/barrios'
import * as categoriasApi from '../api/categorias'
import * as reportesApi from '../api/reportes'
import type { BarrioResponse, CategoriaResponse, PrioridadReporte } from '../api/types'
import { MapaSeleccionPunto } from '../components/MapaSeleccionPunto'

export function CrearReporte() {
  const navigate = useNavigate()
  const [barrios, setBarrios] = useState<BarrioResponse[]>([])
  const [categorias, setCategorias] = useState<CategoriaResponse[]>([])
  const [calleId, setCalleId] = useState('')
  const [categoriaId, setCategoriaId] = useState('')
  const [descripcion, setDescripcion] = useState('')
  const [fotoUrl, setFotoUrl] = useState('')
  const [prioridad, setPrioridad] = useState<PrioridadReporte>('MEDIA')
  const [coordenadas, setCoordenadas] = useState<[number, number] | undefined>()
  const [error, setError] = useState('')
  const [cargando, setCargando] = useState(true)
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    async function cargarOpciones() {
      try {
        const [barriosResponse, categoriasResponse] = await Promise.all([barriosApi.listar(), categoriasApi.listar()])
        setBarrios(barriosResponse)
        setCategorias(categoriasResponse)
      } catch (exception) {
        setError(exception instanceof Error ? exception.message : 'No fue posible cargar las opciones.')
      } finally {
        setCargando(false)
      }
    }

    void cargarOpciones()
  }, [])

  async function manejarEnvio(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!coordenadas) {
      setError('Selecciona la ubicación del daño en el mapa.')
      return
    }

    setError('')
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
      setError(exception instanceof Error ? exception.message : 'No fue posible crear el reporte.')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <main>
      <h1>Reportar daño vial</h1>
      <form onSubmit={manejarEnvio}>
        <label htmlFor="calle">Calle</label>
        <select id="calle" value={calleId} onChange={(event) => setCalleId(event.target.value)} disabled={cargando} required>
          <option value="">Selecciona una calle</option>
          {barrios.map((barrio) => (
            <optgroup key={barrio.id} label={barrio.nombre}>
              {barrio.calles.map((calle) => <option key={calle.id} value={calle.id}>{calle.nombre}</option>)}
            </optgroup>
          ))}
        </select>
        <label htmlFor="categoria">Categoría</label>
        <select id="categoria" value={categoriaId} onChange={(event) => setCategoriaId(event.target.value)} disabled={cargando} required>
          <option value="">Selecciona una categoría</option>
          {categorias.map((categoria) => <option key={categoria.id} value={categoria.id}>{categoria.nombre}</option>)}
        </select>
        <label htmlFor="descripcion">Descripción</label>
        <textarea id="descripcion" minLength={10} maxLength={2000} value={descripcion} onChange={(event) => setDescripcion(event.target.value)} required />
        <label htmlFor="fotoUrl">URL de foto (opcional)</label>
        <input id="fotoUrl" type="url" value={fotoUrl} onChange={(event) => setFotoUrl(event.target.value)} />
        <label htmlFor="prioridad">Prioridad</label>
        <select id="prioridad" value={prioridad} onChange={(event) => setPrioridad(event.target.value as PrioridadReporte)}>
          <option value="BAJA">Baja</option>
          <option value="MEDIA">Media</option>
          <option value="ALTA">Alta</option>
        </select>
        <p>Marca la ubicación del daño.</p>
        <MapaSeleccionPunto onSeleccionar={(lat, lng) => setCoordenadas([lat, lng])} puntoInicial={coordenadas} />
        {coordenadas && <p>Ubicación: {coordenadas[0].toFixed(6)}, {coordenadas[1].toFixed(6)}</p>}
        {error && <p role="alert">{error}</p>}
        <button type="submit" disabled={cargando || enviando}>{enviando ? 'Enviando…' : 'Crear reporte'}</button>
      </form>
    </main>
  )
}
