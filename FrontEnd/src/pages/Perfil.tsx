import { useEffect, useState, type FormEvent } from "react"
import { useNavigate } from "react-router"
import * as usuariosApi from "../api/usuarios"
import type { UsuarioResponse } from "../api/types"
import { Icon } from "../app/components/UI"
import { useAuth } from "../context/useAuth"

export function Perfil() {
  const { actualizarNombre, renovarSesion, logout } = useAuth()
  const navigate = useNavigate()
  const [passwordActual, setPasswordActual] = useState("")
  const [passwordNueva, setPasswordNueva] = useState("")
  const [passwordConfirmacion, setPasswordConfirmacion] = useState("")
  const [errorSeguridad, setErrorSeguridad] = useState("")
  const [mensajeSeguridad, setMensajeSeguridad] = useState("")
  const [procesandoSeguridad, setProcesandoSeguridad] = useState(false)
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

  async function cambiarPassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setErrorSeguridad("")
    setMensajeSeguridad("")
    if (passwordNueva !== passwordConfirmacion) {
      setErrorSeguridad("Las contraseñas nuevas no coinciden.")
      return
    }
    setProcesandoSeguridad(true)
    try {
      renovarSesion(await usuariosApi.cambiarPassword(passwordActual, passwordNueva))
      setPasswordActual("")
      setPasswordNueva("")
      setPasswordConfirmacion("")
      setMensajeSeguridad("Contraseña actualizada. Cerramos tus sesiones en otros dispositivos.")
    } catch (exception) {
      setErrorSeguridad(exception instanceof Error ? exception.message : "No fue posible cambiar la contraseña.")
    } finally {
      setProcesandoSeguridad(false)
    }
  }

  async function cerrarSesiones() {
    if (!window.confirm("Se cerrará tu sesión en todos los dispositivos, incluido este. ¿Continuar?")) return
    setProcesandoSeguridad(true)
    setErrorSeguridad("")
    try {
      await usuariosApi.cerrarSesiones()
      logout()
      navigate("/login")
    } catch (exception) {
      setErrorSeguridad(exception instanceof Error ? exception.message : "No fue posible cerrar las sesiones.")
      setProcesandoSeguridad(false)
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
      <form className="report-form" onSubmit={cambiarPassword}>
        <section className="form-card">
          <div className="form-card-heading"><span><Icon name="user" /></span><div><h2>Seguridad</h2><p>Al cambiar la contraseña se cierran tus sesiones en otros dispositivos.</p></div></div>
          <div className="form-grid">
            <label className="field-wide">Contraseña actual
              <input type="password" autoComplete="current-password" value={passwordActual} onChange={(event) => setPasswordActual(event.target.value)} required />
            </label>
            <label>Contraseña nueva
              <input type="password" autoComplete="new-password" minLength={8} maxLength={72} value={passwordNueva} onChange={(event) => setPasswordNueva(event.target.value)} placeholder="Mínimo 8 caracteres" required />
            </label>
            <label>Repite la contraseña nueva
              <input type="password" autoComplete="new-password" minLength={8} maxLength={72} value={passwordConfirmacion} onChange={(event) => setPasswordConfirmacion(event.target.value)} required />
            </label>
          </div>
        </section>
        {errorSeguridad && <p className="alert error" role="alert">{errorSeguridad}</p>}
        {mensajeSeguridad && <p className="alert success" role="status">{mensajeSeguridad}</p>}
        <div className="form-actions">
          <button className="button button-ghost" type="button" disabled={procesandoSeguridad} onClick={cerrarSesiones}>Cerrar sesión en todos los dispositivos</button>
          <button className="button button-primary" type="submit" disabled={procesandoSeguridad}>{procesandoSeguridad ? "Guardando…" : "Cambiar contraseña"} <Icon name="arrow" /></button>
        </div>
      </form>
    </main>
  )
}
