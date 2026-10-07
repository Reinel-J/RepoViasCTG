import type { ReactNode } from "react"
import { Link } from "react-router"
import type { EstadoReporte } from "../../api/types"

export type IconName =
  | "arrow"
  | "camera"
  | "check"
  | "location"
  | "map"
  | "menu"
  | "road"
  | "search"
  | "user"

export function Icon({ name, className = "icon" }: { name: IconName; className?: string }) {
  const paths: Record<IconName, ReactNode> = {
    arrow: <path d="M5 12h14m-5-5 5 5-5 5" />,
    camera: (
      <>
        <path d="m14.5 6-1.5-2h-2L9.5 6H5a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-4.5Z" />
        <circle cx="12" cy="12.5" r="3.5" />
      </>
    ),
    check: <path d="m5 12 4 4L19 6" />,
    location: (
      <>
        <path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z" />
        <circle cx="12" cy="10" r="2.5" />
      </>
    ),
    map: <path d="m3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3V6Zm6-3v15m6-12v15" />,
    menu: <path d="M4 7h16M4 12h16M4 17h16" />,
    road: (
      <>
        <path d="m8 3-3 18M16 3l3 18" />
        <path d="M12 4v4m0 4v4m0 4v1" />
      </>
    ),
    search: (
      <>
        <circle cx="11" cy="11" r="7" />
        <path d="m20 20-4-4" />
      </>
    ),
    user: (
      <>
        <circle cx="12" cy="8" r="4" />
        <path d="M4 21a8 8 0 0 1 16 0" />
      </>
    ),
  }

  return (
    <svg
      aria-hidden="true"
      className={className}
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth="1.9"
    >
      {paths[name]}
    </svg>
  )
}

export function BrandMark({ compact = false }: { compact?: boolean }) {
  return (
    <Link className="brand" to="/" aria-label="Vía Cartagena, inicio">
      <svg className={compact ? "brand-mark compact" : "brand-mark"} viewBox="0 0 64 64" aria-hidden="true">
        <rect width="64" height="64" rx="20" fill="#EB654C" />
        <path d="M32 11c-12 0-21 8-21 19 0 10 7 17 17 19l4 8 4-8c10-2 17-9 17-19 0-11-9-19-21-19Z" fill="#FFF4DD" />
        <path d="m24 43 5-24h6l5 24Z" fill="#173B57" />
        <path d="M32 22v7m0 5v4" stroke="#F4BB42" strokeWidth="2.7" strokeLinecap="round" />
      </svg>
      {!compact && (
        <span>
          <strong>Vía Cartagena</strong>
          <small>Tu calle, tu voz</small>
        </span>
      )}
    </Link>
  )
}

const statusLabels: Record<EstadoReporte, string> = {
  PENDIENTE: "Pendiente",
  EN_REVISION: "En revisión",
  EN_PROCESO: "En proceso",
  RESUELTO: "Resuelto",
  RECHAZADO: "Rechazado",
}

export function StatusBadge({ status }: { status: EstadoReporte }) {
  return <span className={`status status-${status.toLowerCase()}`}>{statusLabels[status]}</span>
}
