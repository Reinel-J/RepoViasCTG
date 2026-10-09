import { useState, type FormEvent } from "react"
import { Link } from "react-router"
import * as authApi from "../api/auth"
import { Icon } from "../app/components/UI"

export function RecuperarPassword() {
  const [email, setEmail] = useState("")
  const [enviado, setEnviado] = useState(false)
  const [error, setError] = useState("")
  const [enviando, setEnviando] = useState(false)

  async function manejarEnvio(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError("")
    setEnviando(true)
    try {
      await authApi.recuperar(email)
      setEnviado(true)
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible procesar la solicitud.")
    } finally {
      setEnviando(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-panel">
        <span className="eyebrow">Recupera tu acceso</span>
        <h1>¿Olvidaste tu contraseña?</h1>
        {enviado ? (
          <>
            <p className="alert success" role="status">Si existe una cuenta con <strong>{email}</strong>, te enviamos un enlace para crear una contraseña nueva. Vence en 30 minutos.</p>
            <p className="auth-switch">¿No llegó? Revisa el correo no deseado o <button className="text-button" onClick={() => setEnviado(false)}>inténtalo de nuevo</button>.</p>
          </>
        ) : (
          <>
            <p>Escribe el correo con el que te registraste y te enviaremos un enlace para restablecerla.</p>
            <form className="form-stack" onSubmit={manejarEnvio}>
              <label>Correo electrónico<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="nombre@correo.com" required /></label>
              {error && <p className="alert error" role="alert">{error}</p>}
              <button className="button button-primary button-wide" type="submit" disabled={enviando}>
                {enviando ? "Enviando…" : "Enviar enlace"} <Icon name="arrow" />
              </button>
            </form>
          </>
        )}
        <p className="auth-switch"><Link to="/login">Volver a iniciar sesión</Link></p>
      </section>
      <aside className="auth-aside auth-aside-yellow">
        <Icon name="user" className="auth-icon" />
        <h2>Nadie del equipo te pedirá tu contraseña.</h2>
        <p>El enlace es personal y solo funciona una vez.</p>
      </aside>
    </main>
  )
}
