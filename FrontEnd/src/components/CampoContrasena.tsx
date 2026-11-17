import { useState, type ComponentPropsWithoutRef } from "react"

type CampoContrasenaProps = Omit<ComponentPropsWithoutRef<"input">, "type">

export function CampoContrasena(props: CampoContrasenaProps) {
  const [visible, setVisible] = useState(false)

  return (
    <span className="campo-contrasena">
      <input {...props} type={visible ? "text" : "password"} />
      <button
        type="button"
        aria-label={visible ? "Ocultar contraseña" : "Mostrar contraseña"}
        aria-pressed={visible}
        onClick={() => setVisible(!visible)}
      >
        {visible ? "Ocultar" : "Mostrar"}
      </button>
    </span>
  )
}
