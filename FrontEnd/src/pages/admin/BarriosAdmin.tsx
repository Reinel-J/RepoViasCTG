import { useEffect, useState, type FormEvent } from "react"
import * as barriosApi from "../../api/barrios"
import type { BarrioRequest, BarrioResponse } from "../../api/types"
import { mensajeError } from "./estados"

const VACIO: BarrioRequest = { nombre: "", localidad: "" }

function ordenar(lista: BarrioResponse[]) {
  return [...lista].sort((a, b) => a.nombre.localeCompare(b.nombre, "es"))
}

export function BarriosAdmin() {
  const [barrios, setBarrios] = useState<BarrioResponse[]>([])
  const [formulario, setFormulario] = useState<BarrioRequest>(VACIO)
  const [editandoId, setEditandoId] = useState<string | null>(null)
  const [abiertoId, setAbiertoId] = useState<string | null>(null)
  const [calle, setCalle] = useState({ nombre: "", codigoPostal: "" })
  const [cargando, setCargando] = useState(true)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState("")
  const [aviso, setAviso] = useState("")

  useEffect(() => {
    barriosApi.listar()
      .then((lista) => setBarrios(ordenar(lista)))
      .catch((exception) => setError(mensajeError(exception, "No fue posible cargar los barrios.")))
      .finally(() => setCargando(false))
  }, [])

  function reemplazar(barrio: BarrioResponse) {
    setBarrios((actuales) => ordenar([...actuales.filter((item) => item.id !== barrio.id), barrio]))
  }

  async function ejecutar(accion: () => Promise<void>, mensaje: string) {
    setGuardando(true)
    setError("")
    setAviso("")
    try {
      await accion()
      setAviso(mensaje)
    } catch (exception) {
      setError(mensajeError(exception, "No fue posible guardar los cambios."))
    } finally {
      setGuardando(false)
    }
  }

  function guardarBarrio(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const datos = { nombre: formulario.nombre.trim(), localidad: formulario.localidad.trim() }
    ejecutar(async () => {
      reemplazar(editandoId ? await barriosApi.actualizar(editandoId, datos) : await barriosApi.crear(datos))
      setFormulario(VACIO)
      setEditandoId(null)
    }, editandoId ? "Barrio actualizado." : "Barrio creado.")
  }

  function editar(barrio: BarrioResponse) {
    setEditandoId(barrio.id)
    setFormulario({ nombre: barrio.nombre, localidad: barrio.localidad ?? "" })
  }

  function eliminar(barrio: BarrioResponse) {
    if (!window.confirm(`¿Eliminar el barrio ${barrio.nombre} y sus ${barrio.calles.length} calles?`)) return
    ejecutar(async () => {
      await barriosApi.eliminar(barrio.id)
      setBarrios((actuales) => actuales.filter((item) => item.id !== barrio.id))
    }, "Barrio eliminado.")
  }

  function agregarCalle(event: FormEvent<HTMLFormElement>, barrioId: string) {
    event.preventDefault()
    ejecutar(async () => {
      reemplazar(await barriosApi.agregarCalle(barrioId, { nombre: calle.nombre.trim(), codigoPostal: calle.codigoPostal.trim() }))
      setCalle({ nombre: "", codigoPostal: "" })
    }, "Calle agregada.")
  }

  function eliminarCalle(barrioId: string, calleId: string, nombre: string) {
    if (!window.confirm(`¿Eliminar la calle ${nombre}?`)) return
    ejecutar(async () => reemplazar(await barriosApi.eliminarCalle(barrioId, calleId)), "Calle eliminada.")
  }

  return (
    <section className="admin-section">
      <form className="admin-card admin-inline-form" onSubmit={guardarBarrio}>
        <h2>{editandoId ? "Editar barrio" : "Nuevo barrio"}</h2>
        <label>Nombre<input value={formulario.nombre} maxLength={100} onChange={(event) => setFormulario({ ...formulario, nombre: event.target.value })} required /></label>
        <label>Localidad<input value={formulario.localidad} maxLength={100} onChange={(event) => setFormulario({ ...formulario, localidad: event.target.value })} placeholder="Ej: Histórica y del Caribe Norte" required /></label>
        <div className="admin-buttons">
          <button className="button button-primary button-small" type="submit" disabled={guardando}>{editandoId ? "Guardar" : "Crear barrio"}</button>
          {editandoId && <button className="button button-ghost button-small" type="button" onClick={() => { setEditandoId(null); setFormulario(VACIO) }}>Cancelar</button>}
        </div>
      </form>
      {cargando && <div className="loading">Cargando barrios…</div>}
      {error && <p className="alert error" role="alert">{error}</p>}
      {aviso && <p className="alert success" role="status">{aviso}</p>}
      {!cargando && barrios.length === 0 && <p className="muted">Todavía no hay barrios registrados.</p>}
      <div className="admin-list">
        {barrios.map((barrio) => (
          <article key={barrio.id} className="admin-catalog">
            <div className="admin-catalog-head">
              <div><h2>{barrio.nombre}</h2><p>{barrio.localidad ?? "Sin localidad"} · {barrio.calles.length} calle(s)</p></div>
              <div className="admin-buttons">
                <button className="button button-ghost button-small" onClick={() => setAbiertoId(abiertoId === barrio.id ? null : barrio.id)}>{abiertoId === barrio.id ? "Ocultar calles" : "Calles"}</button>
                <button className="button button-ghost button-small" onClick={() => editar(barrio)}>Editar</button>
                <button className="button button-ghost button-small" disabled={guardando} onClick={() => eliminar(barrio)}>Eliminar</button>
              </div>
            </div>
            {abiertoId === barrio.id && (
              <div className="admin-streets">
                <ul>
                  {barrio.calles.length === 0 && <li className="muted">Sin calles.</li>}
                  {barrio.calles.map((item) => (
                    <li key={item.id}>
                      <span>{item.nombre}{item.codigoPostal && <small> · CP {item.codigoPostal}</small>}</span>
                      <button className="text-button" disabled={guardando} onClick={() => eliminarCalle(barrio.id, item.id, item.nombre)}>Quitar</button>
                    </li>
                  ))}
                </ul>
                <form className="admin-inline-form" onSubmit={(event) => agregarCalle(event, barrio.id)}>
                  <label>Calle<input value={calle.nombre} maxLength={150} onChange={(event) => setCalle({ ...calle, nombre: event.target.value })} required /></label>
                  <label>Código postal<input value={calle.codigoPostal} maxLength={20} onChange={(event) => setCalle({ ...calle, codigoPostal: event.target.value })} required /></label>
                  <button className="button button-dark button-small" type="submit" disabled={guardando}>Agregar calle</button>
                </form>
              </div>
            )}
          </article>
        ))}
      </div>
    </section>
  )
}
