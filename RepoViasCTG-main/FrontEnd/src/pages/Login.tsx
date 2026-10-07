import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  async function manejarEnvio(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setEnviando(true)

    try {
      await login(email, password)
      navigate('/')
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'No fue posible iniciar sesión.')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <main>
      <h1>Iniciar sesión</h1>
      <form onSubmit={manejarEnvio}>
        <label htmlFor="email">Correo electrónico</label>
        <input id="email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
        <label htmlFor="password">Contraseña</label>
        <input id="password" type="password" value={password} onChange={(event) => setPassword(event.target.value)} required />
        {error && <p role="alert">{error}</p>}
        <button type="submit" disabled={enviando}>{enviando ? 'Ingresando…' : 'Ingresar'}</button>
      </form>
      <p>¿No tienes cuenta? <Link to="/registro">Regístrate</Link></p>
    </main>
  )
}
