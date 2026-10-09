import { useState, type FormEvent } from "react"
import { Link, useSearchParams } from "react-router"
import * as authApi from "../api/auth"
import { Icon } from "../app/components/UI"

export function RestablecerPassword() {
  const [params] = useSearchParams()
  const token = params.get("token") ?? ""
  const [password, setPassword] = useState("")
  const [confirmacion, setConfirmacion] = useState("")
  const [listo, setListo] = useState(false)
  const [error, setError] = useState("")
  const [enviando, setEnviando] = useState(false)

  async function manejarEnvio(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (password !== confirmacion) {
      setError("Las contraseñas no coinciden.")
      return
    }
    setError("")
    setEnviando(true)
    try {
      await authApi.restablecer(token, password)
      setListo(true)
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible restablecer la contraseña.")
    } finally {
      setEnviando(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-panel">
        <span className="eyebrow">Nueva contraseña</span>
        <h1>Crea una contraseña nueva</h1>
        {!token ? (
          <p className="alert error" role="alert">El enlace está incompleto. <Link to="/recuperar">Solicita uno nuevo</Link>.</p>
        ) : listo ? (
          <>
            <p className="alert success" role="status">Tu contraseña se actualizó y cerramos las sesiones abiertas en otros dispositivos.</p>
            <Link className="button button-primary button-wide" to="/login">Iniciar sesión <Icon name="arrow" /></Link>
          </>
        ) : (
          <form className="form-stack" onSubmit={manejarEnvio}>
            <label>Contraseña nueva<input type="password" minLength={8} maxLength={72} value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Mínimo 8 caracteres" required /></label>
            <label>Repite la contraseña<input type="password" minLength={8} maxLength={72} value={confirmacion} onChange={(event) => setConfirmacion(event.target.value)} required /></label>
            {error && <p className="alert error" role="alert">{error} {error.includes("venció") && <Link to="/recuperar">Solicitar otro enlace</Link>}</p>}
            <button className="button button-primary button-wide" type="submit" disabled={enviando}>
              {enviando ? "Guardando…" : "Guardar contraseña"} <Icon name="arrow" />
            </button>
          </form>
        )}
      </section>
      <aside className="auth-aside">
        <Icon name="location" className="auth-icon" />
        <h2>Usa una contraseña que no uses en otros sitios.</h2>
        <p>Así tu cuenta sigue segura aunque otro servicio sea vulnerado.</p>
      </aside>
    </main>
  )
}
