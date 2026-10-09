import axios from 'axios'

const client = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api',
})

client.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')

  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  return config
})

client.interceptors.response.use(
  (response) => response,
  (error) => {
    // Token vencido o inválido: se limpia la sesión y se vuelve al inicio de sesión.
    if (error.response?.status === 401 && localStorage.getItem('token') && !error.config?.url?.startsWith('/auth/')) {
      localStorage.removeItem('token')
      localStorage.removeItem('authSession')
      window.location.assign('/login')
    }
    // Se muestra el mensaje en español que entrega el backend en lugar del genérico de Axios.
    const datos = error.response?.data
    if (datos?.message) {
      const detalles = Object.values(datos.validationErrors ?? {})
      error.message = detalles.length ? `${datos.message}: ${detalles.join('; ')}` : datos.message
    } else if (!error.response) {
      error.message = 'No fue posible conectar con el servidor. Revisa tu conexión.'
    }
    return Promise.reject(error)
  },
)

export default client
