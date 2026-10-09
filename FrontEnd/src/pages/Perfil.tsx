import { useEffect, useState, type FormEvent } from "react"
import * as usuariosApi from "../api/usuarios"
import type { UsuarioResponse } from "../api/types"
import { Icon } from "../app/components/UI"
import { useAuth } from "../context/useAuth"

export function Perfil() {
  const { actualizarNombre } = useAuth()
  const [usuario, setUsuario] = useState<UsuarioResponse | null>(null)
  const [nombre, setNombre] = useState("")
  const [telefono, setTelefono] = useState("")
  const [error, setError] = useState("")
  const [mensaje, setMensaje] = useState("")
  const [cargando, setCargando] = useState(true)
  const [guardando, setGuardando] = useState(false)

  useEffect(() => {
    usuariosApi.perfil()
      .then((perfil) => {
        setUsuario(perfil)
        setNombre(perfil.nombre)
        setTelefono(perfil.telefono ?? "")
      })
      .catch((exception) => setError(exception instanceof Error ? exception.message : "No fue posible cargar tu perfil."))
      .finally(() => setCargando(false))
  }, [])

  async function guardar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError("")
    setMensaje("")
    setGuardando(true)
    try {
      const actualizado = await usuariosApi.actualizarPerfil({
        nombre: nombre.trim(),
        ...(telefono.trim() ? { telefono: telefono.trim() } : {}),
      })
      setUsuario(actualizado)
      actualizarNombre(actualizado.nombre)
      setMensaje("Perfil actualizado.")
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible guardar los cambios.")
    } finally {
      setGuardando(false)
    }
  }

  if (cargando) return <main className="page-shell section"><div className="loading">Cargando perfil…</div></main>

  return (
    <main className="page-shell section internal-page">
      <div className="page-heading narrow-heading"><div><span className="eyebrow">Tu cuenta</span><h1>Mi perfil</h1>{usuario && <p>{usuario.email} · {usuario.rol} · miembro desde {new Date(usuario.fechaRegistro).toLocaleDateString("es-CO")}</p>}</div></div>
      <form className="report-form" onSubmit={guardar}>
        <section className="form-card">
          <div className="form-grid">
            <label>Nombre
              <input value={nombre} maxLength={100} onChange={(event) => setNombre(event.target.value)} required />
            </label>
            <label>Teléfono
              <input value={telefono} onChange={(event) => setTelefono(event.target.value)} placeholder="Ej: 300 123 4567" pattern="[0-9+\(\) \-]{7,25}" title="Entre 7 y 25 caracteres: números, +, paréntesis, espacios o guiones" />
            </label>
          </div>
        </section>
        {error && <p className="alert error" role="alert">{error}</p>}
        {mensaje && <p className="alert success" role="status">{mensaje}</p>}
        <div className="form-actions">
          <button className="button button-primary" type="submit" disabled={guardando}>{guardando ? "Guardando…" : "Guardar cambios"} <Icon name="arrow" /></button>
        </div>
      </form>
    </main>
  )
}
