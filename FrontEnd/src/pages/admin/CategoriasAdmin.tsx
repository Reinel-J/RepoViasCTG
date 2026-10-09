import { useEffect, useState, type FormEvent } from "react"
import * as categoriasApi from "../../api/categorias"
import type { CategoriaRequest, CategoriaResponse } from "../../api/types"
import { mensajeError } from "./estados"

const VACIO = { nombre: "", descripcion: "" }

function ordenar(lista: CategoriaResponse[]) {
  return [...lista].sort((a, b) => a.nombre.localeCompare(b.nombre, "es"))
}

export function CategoriasAdmin() {
  const [categorias, setCategorias] = useState<CategoriaResponse[]>([])
  const [formulario, setFormulario] = useState(VACIO)
  const [editandoId, setEditandoId] = useState<string | null>(null)
  const [cargando, setCargando] = useState(true)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState("")
  const [aviso, setAviso] = useState("")

  useEffect(() => {
    categoriasApi.listar(false)
      .then((lista) => setCategorias(ordenar(lista)))
      .catch((exception) => setError(mensajeError(exception, "No fue posible cargar las categorías.")))
      .finally(() => setCargando(false))
  }, [])

  async function ejecutar(accion: () => Promise<CategoriaResponse>, mensaje: string) {
    setGuardando(true)
    setError("")
    setAviso("")
    try {
      const categoria = await accion()
      setCategorias((actuales) => ordenar([...actuales.filter((item) => item.id !== categoria.id), categoria]))
      setAviso(mensaje)
      return true
    } catch (exception) {
      setError(mensajeError(exception, "No fue posible guardar la categoría."))
      return false
    } finally {
      setGuardando(false)
    }
  }

  async function guardar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const datos: CategoriaRequest = { nombre: formulario.nombre.trim(), ...(formulario.descripcion.trim() ? { descripcion: formulario.descripcion.trim() } : {}) }
    const ok = await ejecutar(() => (editandoId ? categoriasApi.actualizar(editandoId, datos) : categoriasApi.crear(datos)), editandoId ? "Categoría actualizada." : "Categoría creada.")
    if (ok) {
      setFormulario(VACIO)
      setEditandoId(null)
    }
  }

  return (
    <section className="admin-section">
      <p className="muted admin-note">Las categorías no se eliminan: se desactivan para conservar el historial. El formulario de reportes acepta categoría libre, así que este catálogo sirve como referencia.</p>
      <form className="admin-card admin-inline-form" onSubmit={guardar}>
        <h2>{editandoId ? "Editar categoría" : "Nueva categoría"}</h2>
        <label>Nombre<input value={formulario.nombre} maxLength={100} onChange={(event) => setFormulario({ ...formulario, nombre: event.target.value })} required /></label>
        <label>Descripción<input value={formulario.descripcion} maxLength={500} onChange={(event) => setFormulario({ ...formulario, descripcion: event.target.value })} placeholder="Opcional" /></label>
        <div className="admin-buttons">
          <button className="button button-primary button-small" type="submit" disabled={guardando}>{editandoId ? "Guardar" : "Crear categoría"}</button>
          {editandoId && <button className="button button-ghost button-small" type="button" onClick={() => { setEditandoId(null); setFormulario(VACIO) }}>Cancelar</button>}
        </div>
      </form>
      {cargando && <div className="loading">Cargando categorías…</div>}
      {error && <p className="alert error" role="alert">{error}</p>}
      {aviso && <p className="alert success" role="status">{aviso}</p>}
      {!cargando && categorias.length === 0 && <p className="muted">Todavía no hay categorías.</p>}
      <div className="admin-list">
        {categorias.map((categoria) => (
          <article key={categoria.id} className={`admin-catalog${categoria.activa ? "" : " is-inactive"}`}>
            <div className="admin-catalog-head">
              <div><h2>{categoria.nombre} {!categoria.activa && <span className="admin-chip">Inactiva</span>}</h2><p>{categoria.descripcion ?? "Sin descripción"}</p></div>
              <div className="admin-buttons">
                <button className="button button-ghost button-small" onClick={() => { setEditandoId(categoria.id); setFormulario({ nombre: categoria.nombre, descripcion: categoria.descripcion ?? "" }) }}>Editar</button>
                <button className="button button-ghost button-small" disabled={guardando} onClick={() => ejecutar(() => categoriasApi.cambiarEstado(categoria.id, !categoria.activa), categoria.activa ? "Categoría desactivada." : "Categoría activada.")}>
                  {categoria.activa ? "Desactivar" : "Activar"}
                </button>
              </div>
            </div>
          </article>
        ))}
      </div>
    </section>
  )
}
