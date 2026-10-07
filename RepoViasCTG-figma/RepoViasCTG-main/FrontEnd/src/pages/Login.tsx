import { useState, type FormEvent } from "react"
import { Link, useNavigate } from "react-router"
import { Icon } from "../app/components/UI"
import { useAuth } from "../context/AuthContext"

export function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState("")
  const [enviando, setEnviando] = useState(false)

  async function manejarEnvio(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError("")
    setEnviando(true)
    try {
      await login(email, password)
      navigate("/")
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible iniciar sesión.")
    } finally {
      setEnviando(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-panel">
        <span className="eyebrow">Bienvenido de vuelta</span>
        <h1>Ingresa a tu cuenta</h1>
        <p>Sigue tus reportes y participa en el cuidado de las vías de Cartagena.</p>
        <form className="form-stack" onSubmit={manejarEnvio}>
          <label>Correo electrónico<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="nombre@correo.com" required /></label>
          <label>Contraseña<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Tu contraseña" required /></label>
          {error && <p className="alert error" role="alert">{error}</p>}
          <button className="button button-primary button-wide" type="submit" disabled={enviando}>
            {enviando ? "Ingresando…" : "Iniciar sesión"} <Icon name="arrow" />
          </button>
        </form>
        <p className="auth-switch">¿No tienes una cuenta? <Link to="/registro">Regístrate gratis</Link></p>
      </section>
      <aside className="auth-aside">
        <span className="auth-number">42</span>
        <h2>barrios ya están haciendo visible lo que pasa en sus calles.</h2>
        <p>Tu voz también cuenta.</p>
      </aside>
    </main>
  )
}
