import { useEffect } from 'react'
import { useMap } from 'react-leaflet'

/**
 * La rueda del mouse desplaza la página hasta que se hace clic en el mapa (o se le da el foco con el teclado), y
 * vuelve a desplazarla al sacar el cursor. Con el zoom por rueda siempre activo, quien solo quería bajar por la página
 * quedaba atrapado acercando y alejando el mapa. Los botones + y − funcionan siempre.
 * Requiere `scrollWheelZoom={false}` en el MapContainer.
 */
export function ZoomConRueda() {
  const map = useMap()

  useEffect(() => {
    const activar = () => map.scrollWheelZoom.enable()
    const desactivar = () => map.scrollWheelZoom.disable()
    map.on('click focus', activar)
    map.on('mouseout blur', desactivar)
    return () => {
      map.off('click focus', activar)
      map.off('mouseout blur', desactivar)
    }
  }, [map])

  return null
}
