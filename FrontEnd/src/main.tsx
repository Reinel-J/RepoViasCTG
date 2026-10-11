import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import './index.css'

// Tras un despliegue, una pestaña abierta con el build anterior pide chunks que ya no existen.
// Se recarga para obtener el index.html nuevo, como máximo una vez cada 10 s: si el chunk
// falta por otro motivo, se muestra el error en vez de recargar en bucle.
const MARCA_RECARGA = 'recargaPorDespliegue'
window.addEventListener('vite:preloadError', (event) => {
  const ultima = Number(sessionStorage.getItem(MARCA_RECARGA) ?? 0)
  if (Date.now() - ultima < 10_000) return
  event.preventDefault()
  sessionStorage.setItem(MARCA_RECARGA, String(Date.now()))
  window.location.reload()
})

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
