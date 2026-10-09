import { useState, type FormEvent } from "react"
import { Link, useNavigate } from "react-router"
import { Icon } from "../app/components/UI"
import { useAuth } from "../context/useAuth"

export function Registro() {
  const { registro } = useAuth()
  const navigate = useNavigate()
  const [nombre, setNombre] = useState("")
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState("")
  const [enviando, setEnviando] = useState(false)

  async function manejarEnvio(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError("")
    setEnviando(true)
    try {
      await registro(nombre, email, password)
      navigate("/")
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear la cuenta.")
    } finally {
      setEnviando(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-panel">
        <span className="eyebrow">Súmate a la comunidad</span>
        <h1>Crea tu cuenta</h1>
        <p>Reporta, confirma y sigue los problemas viales de tu barrio.</p>
        <form className="form-stack" onSubmit={manejarEnvio}>
          <label>Nombre completo<input value={nombre} onChange={(event) => setNombre(event.target.value)} placeholder="¿Cómo te llamas?" required /></label>
          <label>Correo electrónico<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="nombre@correo.com" required /></label>
          <label>Contraseña<input type="password" minLength={8} value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Mínimo 8 caracteres" required /></label>
          {error && <p className="alert error" role="alert">{error}</p>}
          <button className="button button-primary button-wide" type="submit" disabled={enviando}>
            {enviando ? "Creando cuenta…" : "Crear mi cuenta"} <Icon name="arrow" />
          </button>
        </form>
        <p className="auth-switch">¿Ya tienes una cuenta? <Link to="/login">Inicia sesión</Link></p>
      </section>
      <aside className="auth-aside auth-aside-yellow">
        <Icon name="location" className="auth-icon" />
        <h2>Una calle mejor empieza con alguien que decide reportarla.</h2>
        <p>Ese alguien puedes ser tú.</p>
      </aside>
    </main>
  )
}
