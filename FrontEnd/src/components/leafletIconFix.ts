import L from 'leaflet'
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png'
import markerIcon from 'leaflet/dist/images/marker-icon.png'
import markerShadow from 'leaflet/dist/images/marker-shadow.png'

// Leaflet antepone a estas URL la carpeta que deduce de su CSS (_getIconUrl), y como Vite ya las entrega
// completas la ruta quedaba duplicada (".../images//node_modules/.../marker-icon.png") y el pin salía roto.
delete (L.Icon.Default.prototype as { _getIconUrl?: unknown })._getIconUrl

L.Icon.Default.mergeOptions({
  iconRetinaUrl: markerIcon2x,
  iconUrl: markerIcon,
  shadowUrl: markerShadow,
})
