import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import type { DialogoAbierto } from './useDialogo'

// Ventana de confirmación o de texto sobre un <dialog> nativo: showModal() bloquea el resto de la página (foco,
// teclado y lector de pantalla) sin detener JavaScript como window.confirm. Esc y el clic fuera cancelan.
export function Dialogo({ dialogo }: { dialogo: DialogoAbierto | null }) {
  return dialogo ? <VentanaDialogo dialogo={dialogo} /> : null
}

function VentanaDialogo({ dialogo }: { dialogo: DialogoAbierto }) {
  const ref = useRef<HTMLDialogElement>(null)
  const id = useId()
  const [valor, setValor] = useState(dialogo.campo?.valorInicial ?? '')
  const { campo } = dialogo

  useEffect(() => {
    const ventana = ref.current
    if (ventana && !ventana.open) ventana.showModal()
  }, [])

  function aceptar(event: FormEvent) {
    event.preventDefault()
    dialogo.responder(campo ? valor.trim() : '')
  }

  return (
    <dialog
      ref={ref}
      className="dialogo"
      aria-labelledby={`${id}-titulo`}
      onCancel={(event) => { event.preventDefault(); dialogo.responder(null) }}
      // Un clic en el fondo oscuro llega con el propio <dialog> como destino.
      onClick={(event) => { if (event.target === event.currentTarget) dialogo.responder(null) }}
    >
      <form onSubmit={aceptar}>
        <h2 id={`${id}-titulo`}>{dialogo.titulo}</h2>
        {dialogo.mensaje && <p>{dialogo.mensaje}</p>}
        {campo && (
          <label>
            {campo.etiqueta}
            <textarea rows={5} maxLength={campo.maximo} value={valor} onChange={(event) => setValor(event.target.value)} required autoFocus />
            <small>{valor.length} / {campo.maximo}</small>
          </label>
        )}
        <div className="dialogo-acciones">
          <button type="button" className="dialogo-boton" onClick={() => dialogo.responder(null)}>Cancelar</button>
          <button type="submit" className={dialogo.peligro ? 'dialogo-boton peligro' : 'dialogo-boton principal'} disabled={Boolean(campo) && !valor.trim()}>{dialogo.aceptar}</button>
        </div>
      </form>
    </dialog>
  )
}
