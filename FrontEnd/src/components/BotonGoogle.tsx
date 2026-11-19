import { useEffect, useRef, useState } from "react"
import * as authApi from "../api/auth"

// Lo mínimo de Google Identity Services (https://accounts.google.com/gsi/client) que usa este botón.
interface GoogleIdentity {
  initialize(opciones: { client_id: string; callback: (respuesta: { credential?: string }) => void; ux_mode?: "popup"; use_fedcm_for_button?: boolean }): void
  renderButton(contenedor: HTMLElement, opciones: { type?: "standard"; theme?: "outline"; size?: "large"; text?: TextoBoton; shape?: "rectangular" | "pill"; width?: number; locale?: string }): void
}

declare global {
  interface Window {
    google?: { accounts: { id: GoogleIdentity } }
  }
}

export type TextoBoton = "signin_with" | "signup_with" | "continue_with"

const SCRIPT_GIS = "https://accounts.google.com/gsi/client"
// El botón de Google admite entre 200 y 400 píxeles de ancho.
const ANCHO_MINIMO = 200
const ANCHO_MAXIMO = 400

let cargaGis: Promise<GoogleIdentity> | null = null

/** Carga el script de Google una sola vez por página (lo comparten el login y el registro). */
function cargarGis(): Promise<GoogleIdentity> {
  if (window.google?.accounts?.id) return Promise.resolve(window.google.accounts.id)
  cargaGis ??= new Promise<GoogleIdentity>((resolver, rechazar) => {
    const script = document.createElement("script")
    script.src = SCRIPT_GIS
    script.async = true
    script.onload = () => window.google?.accounts?.id ? resolver(window.google.accounts.id) : rechazar(new Error("GIS sin cargar"))
    script.onerror = () => {
      cargaGis = null // para reintentar al volver a la página (por ejemplo, tras recuperar la conexión)
      script.remove()
      rechazar(new Error("No se pudo cargar Google Identity Services"))
    }
    document.head.appendChild(script)
  })
  return cargaGis
}

/**
 * «Continuar con Google». Solo aparece si el backend tiene un Client ID configurado. Google entrega al navegador un
 * ID token firmado que se pasa a {@code onCredencial}; el backend lo verifica antes de abrir la sesión.
 */
export function BotonGoogle({ texto, onCredencial }: { texto: TextoBoton; onCredencial: (credencial: string) => void }) {
  const contenedor = useRef<HTMLDivElement>(null)
  // El callback de Google se registra una vez: con la referencia siempre llama a la versión más reciente.
  const alRecibir = useRef(onCredencial)
  const [clientId, setClientId] = useState<string | null>(null)
  const [error, setError] = useState("")

  useEffect(() => {
    alRecibir.current = onCredencial
  }, [onCredencial])

  useEffect(() => {
    let vigente = true
    // Si el backend no responde, simplemente no se ofrece Google: el formulario con contraseña sigue funcionando.
    authApi.configuracionGoogle().then((config) => { if (vigente) setClientId(config.clientId) }).catch(() => {})
    return () => { vigente = false }
  }, [])

  useEffect(() => {
    if (!clientId) return
    let vigente = true
    cargarGis().then((gis) => {
      const elemento = contenedor.current
      if (!vigente || !elemento) return
      gis.initialize({
        client_id: clientId,
        ux_mode: "popup",
        use_fedcm_for_button: true,
        callback: ({ credential }) => { if (credential) alRecibir.current(credential) },
      })
      const ancho = Math.round(Math.min(ANCHO_MAXIMO, Math.max(ANCHO_MINIMO, elemento.offsetWidth || ANCHO_MAXIMO)))
      gis.renderButton(elemento, { type: "standard", theme: "outline", size: "large", text: texto, shape: "rectangular", width: ancho, locale: "es" })
    }).catch(() => { if (vigente) setError("No se pudo cargar el botón de Google. Revisa tu conexión o entra con tu correo y contraseña.") })
    return () => { vigente = false }
  }, [clientId, texto])

  if (!clientId) return null
  return (
    <div className="google-access">
      <span className="auth-divider">o</span>
      <div ref={contenedor} className="google-button" data-testid="boton-google" />
      {error && <div className="alert-panel error-panel" role="alert">{error}</div>}
    </div>
  )
}
