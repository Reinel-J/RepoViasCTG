import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export function Registro() {
  const { registro } = useAuth()
  const navigate = useNavigate()
  const [nombre, setNombre] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  async function manejarEnvio(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setEnviando(true)

    try {
      await registro(nombre, email, password)
      navigate('/')
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'No fue posible crear la cuenta.')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <main>
      <h1>Crear cuenta</h1>
      <form onSubmit={manejarEnvio}>
        <label htmlFor="nombre">Nombre</label>
        <input id="nombre" value={nombre} onChange={(event) => setNombre(event.target.value)} required />
        <label htmlFor="email">Correo electrónico</label>
        <input id="email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
        <label htmlFor="password">Contraseña</label>
        <input id="password" type="password" minLength={8} value={password} onChange={(event) => setPassword(event.target.value)} required />
        {error && <p role="alert">{error}</p>}
        <button type="submit" disabled={enviando}>{enviando ? 'Creando…' : 'Registrarme'}</button>
      </form>
      <p>¿Ya tienes cuenta? <Link to="/login">Inicia sesión</Link></p>
    </main>
  )
}
