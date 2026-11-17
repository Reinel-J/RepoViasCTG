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

// Respaldo cuando la respuesta no trae el `message` del backend (por ejemplo, un error del proxy):
// nunca se muestra el texto genérico de Axios con el código HTTP.
const MENSAJES_POR_ESTADO: Record<number, string> = {
  400: 'La solicitud no es válida. Revisa los datos e inténtalo de nuevo.',
  401: 'Debes iniciar sesión para realizar esta acción.',
  403: 'No tienes permiso para realizar esta acción.',
  404: 'No encontramos lo que buscabas.',
  409: 'La operación no se pudo completar porque entra en conflicto con el estado actual.',
  413: 'El archivo supera el tamaño máximo permitido.',
  415: 'El tipo de archivo o de contenido no es compatible.',
  429: 'Demasiados intentos. Espera unos minutos e inténtalo de nuevo.',
}

function mensajePorEstado(status: number) {
  if (MENSAJES_POR_ESTADO[status]) return MENSAJES_POR_ESTADO[status]
  return status >= 500
    ? 'El servidor tuvo un problema. Inténtalo de nuevo en unos minutos.'
    : 'No fue posible completar la operación.'
}

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
    } else if (error.response) {
      error.message = mensajePorEstado(error.response.status)
    } else {
      error.message = 'No fue posible conectar con el servidor. Revisa tu conexión.'
    }
    return Promise.reject(error)
  },
)

export default client
