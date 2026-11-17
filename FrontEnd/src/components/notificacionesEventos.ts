// Evento que emite la pantalla de notificaciones al marcar como leídas, para que la campana baje al instante.
// Va aparte de CampanaNotificaciones por la regla react-refresh/only-export-components.
export const EVENTO_CAMBIO_NOTIFICACIONES = 'notificaciones:cambio'

export function avisarCambioNotificaciones() {
  window.dispatchEvent(new Event(EVENTO_CAMBIO_NOTIFICACIONES))
}
