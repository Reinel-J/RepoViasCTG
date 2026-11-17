import { useCallback, useState } from 'react'

export interface CampoDialogo {
  etiqueta: string
  valorInicial: string
  maximo: number
}

export interface PedidoDialogo {
  titulo: string
  mensaje?: string
  // Texto del botón que acepta, con el verbo de la acción ("Eliminar", "Rechazar"), no un "Aceptar" genérico.
  aceptar: string
  // Botón rojo para acciones destructivas o que no se pueden deshacer.
  peligro?: boolean
  campo?: CampoDialogo
}

export interface DialogoAbierto extends PedidoDialogo {
  // El texto escrito ("" si no hay campo) o null si se canceló.
  responder: (respuesta: string | null) => void
}

// Reemplaza window.confirm y window.prompt, que bloquean toda la página (y a las herramientas que la controlan),
// no admiten estilos y en el caso de prompt solo dan una línea. La pantalla renderiza <Dialogo dialogo={dialogo} />.
export function useDialogo() {
  const [dialogo, setDialogo] = useState<DialogoAbierto | null>(null)

  const pedirTexto = useCallback((pedido: PedidoDialogo) => new Promise<string | null>((resolver) => {
    setDialogo({
      ...pedido,
      responder: (respuesta) => {
        setDialogo(null)
        resolver(respuesta)
      },
    })
  }), [])

  const confirmar = useCallback(async (pedido: PedidoDialogo) => (await pedirTexto(pedido)) !== null, [pedirTexto])

  return { dialogo, confirmar, pedirTexto }
}
