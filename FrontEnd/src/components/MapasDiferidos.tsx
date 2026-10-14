import { lazy, Suspense, type ComponentProps } from 'react'

// Leaflet pesa más que el resto de la aplicación junta: los mapas se descargan solo en las pantallas que
// los muestran (detalle, crear/editar, mapa y estadísticas), no en la portada, el listado ni el login.
const MapaReportesDiferido = lazy(() => import('./MapaReportes').then((modulo) => ({ default: modulo.MapaReportes })))
const MapaSeleccionPuntoDiferido = lazy(() => import('./MapaSeleccionPunto').then((modulo) => ({ default: modulo.MapaSeleccionPunto })))
const MapaDensidadDiferido = lazy(() => import('./MapaDensidad').then((modulo) => ({ default: modulo.MapaDensidad })))

function CargandoMapa() {
  return <div className="loading-panel" role="status">Cargando mapa…</div>
}

export function MapaReportes(props: ComponentProps<typeof MapaReportesDiferido>) {
  return <Suspense fallback={<CargandoMapa />}><MapaReportesDiferido {...props} /></Suspense>
}

export function MapaSeleccionPunto(props: ComponentProps<typeof MapaSeleccionPuntoDiferido>) {
  return <Suspense fallback={<CargandoMapa />}><MapaSeleccionPuntoDiferido {...props} /></Suspense>
}

export function MapaDensidad(props: ComponentProps<typeof MapaDensidadDiferido>) {
  return <Suspense fallback={<CargandoMapa />}><MapaDensidadDiferido {...props} /></Suspense>
}
