import { useEffect, useState, type ReactNode } from 'react'
import { NavLink, useLocation } from 'react-router'
import { contarSinLeer } from '../api/notificaciones'
import { EVENTO_CAMBIO_NOTIFICACIONES } from './notificacionesEventos'

// Cada cuánto se consulta el contador con la pestaña visible (VITE_NOTIFICACIONES_INTERVALO_MS, por defecto 60 s).
const INTERVALO_MS = Number(import.meta.env.VITE_NOTIFICACIONES_INTERVALO_MS) || 60_000
// Si el backend falla, la espera se duplica hasta este tope en lugar de insistir.
const ESPERA_MAXIMA_MS = 5 * 60_000

// Campana de la cabecera con el número de notificaciones sin leer. Solo se monta con sesión iniciada.
// Consulta al montarse, al cambiar de página, al volver a la pestaña, al leer notificaciones y cada
// INTERVALO_MS mientras la pestaña está visible; con la pestaña oculta no consulta.
export function CampanaNotificaciones({ children }: { children: ReactNode }) {
  const { pathname } = useLocation()
  const [total, setTotal] = useState(0)

  useEffect(() => {
    let activo = true
    let temporizador: number | undefined
    let espera = INTERVALO_MS

    const consultar = () => {
      window.clearTimeout(temporizador)
      if (document.visibilityState !== 'visible') return
      contarSinLeer()
        .then((cantidad) => {
          if (!activo) return
          setTotal(cantidad)
          espera = INTERVALO_MS
        })
        .catch(() => { espera = Math.min(espera * 2, ESPERA_MAXIMA_MS) })
        .finally(() => {
          if (!activo) return
          // Una consulta lanzada por un evento no debe dejar un segundo temporizador corriendo.
          window.clearTimeout(temporizador)
          temporizador = window.setTimeout(consultar, espera)
        })
    }

    consultar()
    document.addEventListener('visibilitychange', consultar)
    window.addEventListener(EVENTO_CAMBIO_NOTIFICACIONES, consultar)
    return () => {
      activo = false
      window.clearTimeout(temporizador)
      document.removeEventListener('visibilitychange', consultar)
      window.removeEventListener(EVENTO_CAMBIO_NOTIFICACIONES, consultar)
    }
  }, [pathname])

  return (
    <NavLink className="icon-button campana" to="/notificaciones" aria-label={total > 0 ? `Notificaciones, ${total} sin leer` : 'Notificaciones'}>
      {children}
      {total > 0 && <span className="campana-contador" aria-hidden="true">{total > 9 ? '9+' : total}</span>}
    </NavLink>
  )
}
