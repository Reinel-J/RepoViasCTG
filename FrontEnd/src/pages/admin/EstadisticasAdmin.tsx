import { useEffect, useState } from "react"
import * as adminApi from "../../api/admin"
import type { EstadisticasResponse } from "../../api/types"
import { RAMPA_DENSIDAD } from "../../components/densidad"
import { MapaDensidad } from "../../components/MapasDiferidos"
import { mensajeError } from "./estados"

type Mes = EstadisticasResponse["meses"][number]

// Paleta categórica validada (contraste y daltonismo) sobre fondo blanco: azul = creados, naranja = resueltos.
const SERIES = [
  { clave: "creados", etiqueta: "Creados", color: "#2a78d6" },
  { clave: "resueltos", etiqueta: "Resueltos", color: "#eb6834" },
] as const

function nombreMes(mes: string, conAnio = false) {
  const [anio, numero] = mes.split("-").map(Number)
  const texto = new Date(anio, numero - 1, 1).toLocaleDateString("es-CO", conAnio ? { month: "long", year: "numeric" } : { month: "short" }).replace(".", "")
  return conAnio ? texto.charAt(0).toUpperCase() + texto.slice(1) : texto
}

function dias(valor: number | null) {
  return valor === null ? "—" : valor.toLocaleString("es-CO", { maximumFractionDigits: 1 })
}

export function EstadisticasAdmin() {
  const [datos, setDatos] = useState<EstadisticasResponse | null>(null)
  const [error, setError] = useState("")

  useEffect(() => {
    adminApi.estadisticas()
      .then(setDatos)
      .catch((exception) => setError(mensajeError(exception, "No fue posible cargar las estadísticas.")))
  }, [])

  if (error) return <p className="alert error" role="alert">{error}</p>
  if (!datos) return <div className="loading">Calculando estadísticas…</div>

  const maximoCategoria = Math.max(1, ...datos.categorias.map((categoria) => categoria.total))

  return (
    <section className="admin-section">
      <div className="admin-stats">
        <div className="admin-stat"><strong>{datos.total}</strong><span>Reportes en total</span></div>
        <div className="admin-stat"><strong>{datos.porcentajeResuelto === null ? "—" : `${datos.porcentajeResuelto}%`}</strong><span>Resueltos (sin contar rechazados)</span></div>
        <div className="admin-stat"><strong>{dias(datos.diasPrimeraRespuesta)}</strong><span>Días promedio hasta la primera respuesta</span></div>
        <div className="admin-stat"><strong>{dias(datos.diasResolucion)}</strong><span>Días promedio hasta resolver</span></div>
      </div>

      <div className="admin-card">
        <h2>Reportes creados y resueltos por mes</h2>
        <GraficoMeses meses={datos.meses} />
      </div>

      <div className="admin-grid-2">
        <div className="admin-card stats-categories">
          <h2>Categorías con más reportes</h2>
          {datos.categorias.length === 0 && <p className="muted">Todavía no hay reportes.</p>}
          {datos.categorias.map((categoria) => (
            <div key={categoria.nombre} className="stats-hbar">
              <span className="stats-hbar-label">{categoria.nombre}</span>
              <span className="stats-hbar-track"><span className="stats-hbar-fill" style={{ width: `${(categoria.total / maximoCategoria) * 100}%` }} /></span>
              <span className="stats-hbar-value"><strong>{categoria.total}</strong> · {categoria.resueltos} resueltos</span>
            </div>
          ))}
        </div>
        <div className="admin-card">
          <h2>Dónde se concentran los daños activos</h2>
          <p className="muted">Cada círculo agrupa los reportes pendientes, en revisión o en proceso de una zona de unos 110 m. Pasa el cursor para ver cuántos hay.</p>
          <div className="density-legend" aria-hidden="true">
            <span>Menos</span>
            {RAMPA_DENSIDAD.map((color) => <i key={color} style={{ background: color }} />)}
            <span>Más</span>
          </div>
          {datos.densidad.length === 0 ? <p className="muted">No hay reportes activos.</p> : <MapaDensidad celdas={datos.densidad} />}
        </div>
      </div>
    </section>
  )
}

// Columnas agrupadas en SVG: un solo eje Y, barras finas con extremos redondeados apoyados en la base.
function GraficoMeses({ meses }: { meses: Mes[] }) {
  const [activo, setActivo] = useState<number | null>(null)
  // viewBox cercano al ancho real del panel para que el texto del eje no se escale de más.
  const ancho = 1100
  const alto = 300
  const margen = { arriba: 12, derecha: 8, abajo: 28, izquierda: 32 }
  const anchoPlot = ancho - margen.izquierda - margen.derecha
  const altoPlot = alto - margen.arriba - margen.abajo
  const maximo = Math.max(1, ...meses.flatMap((mes) => [mes.creados, mes.resueltos]))
  const paso = escalaPaso(maximo)
  const tope = Math.ceil(maximo / paso) * paso
  const marcas = Array.from({ length: tope / paso + 1 }, (_, indice) => indice * paso)
  const anchoGrupo = anchoPlot / meses.length
  const anchoBarra = Math.min(18, (anchoGrupo - 14) / 2)
  const y = (valor: number) => margen.arriba + altoPlot - (valor / tope) * altoPlot
  const mesActivo = activo === null ? null : meses[activo]

  return (
    <div className="stats-chart">
      <div className="stats-legend">
        {SERIES.map((serie) => <span key={serie.clave}><i style={{ background: serie.color }} />{serie.etiqueta}</span>)}
      </div>
      <div className="stats-chart-area">
        <svg viewBox={`0 0 ${ancho} ${alto}`} role="img" aria-label="Reportes creados y resueltos en los últimos 12 meses" onMouseLeave={() => setActivo(null)}>
          {marcas.map((marca) => (
            <g key={marca}>
              <line x1={margen.izquierda} x2={ancho - margen.derecha} y1={y(marca)} y2={y(marca)} className={marca === 0 ? "stats-baseline" : "stats-grid"} />
              <text x={margen.izquierda - 8} y={y(marca)} dy="0.32em" textAnchor="end" className="stats-tick">{marca}</text>
            </g>
          ))}
          {meses.map((mes, indice) => {
            const inicioGrupo = margen.izquierda + indice * anchoGrupo
            const centro = inicioGrupo + anchoGrupo / 2
            return (
              <g key={mes.mes}>
                {activo === indice && <rect x={inicioGrupo + 2} y={margen.arriba} width={anchoGrupo - 4} height={altoPlot} className="stats-hover-band" />}
                {SERIES.map((serie, posicion) => {
                  const valor = mes[serie.clave]
                  if (valor === 0) return null
                  // 2 px de separación entre las dos barras del grupo.
                  const x = posicion === 0 ? centro - anchoBarra - 1 : centro + 1
                  return <path key={serie.clave} d={barraRedondeada(x, y(valor), anchoBarra, y(0) - y(valor))} fill={serie.color} />
                })}
                <text x={centro} y={alto - 8} textAnchor="middle" className="stats-tick">{nombreMes(mes.mes)}</text>
                {/* Zona de hover más grande que las barras. */}
                <rect x={inicioGrupo} y={0} width={anchoGrupo} height={alto} fill="transparent" tabIndex={0}
                  aria-label={`${nombreMes(mes.mes, true)}: ${mes.creados} creados, ${mes.resueltos} resueltos`}
                  onMouseEnter={() => setActivo(indice)} onFocus={() => setActivo(indice)} onBlur={() => setActivo(null)} />
              </g>
            )
          })}
        </svg>
        {mesActivo && activo !== null && (
          <div className="stats-tooltip" style={{ left: `${((margen.izquierda + (activo + 0.5) * anchoGrupo) / ancho) * 100}%` }} role="status">
            <strong>{nombreMes(mesActivo.mes, true)}</strong>
            {SERIES.map((serie) => <span key={serie.clave}><i style={{ background: serie.color }} />{serie.etiqueta}: <b>{mesActivo[serie.clave]}</b></span>)}
          </div>
        )}
      </div>
      <details className="stats-table">
        <summary>Ver como tabla</summary>
        <table>
          <thead><tr><th scope="col">Mes</th><th scope="col">Creados</th><th scope="col">Resueltos</th></tr></thead>
          <tbody>{meses.map((mes) => <tr key={mes.mes}><th scope="row">{nombreMes(mes.mes, true)}</th><td>{mes.creados}</td><td>{mes.resueltos}</td></tr>)}</tbody>
        </table>
      </details>
    </div>
  )
}

// Paso "redondo" (1, 2, 5 × 10ⁿ) para que el eje tenga unas 4 marcas.
function escalaPaso(maximo: number) {
  const bruto = maximo / 4
  const magnitud = 10 ** Math.floor(Math.log10(bruto))
  const paso = [1, 2, 5, 10].map((factor) => factor * magnitud).find((candidato) => candidato >= bruto) ?? 10 * magnitud
  return Math.max(1, paso)
}

// Rectángulo con las esquinas superiores redondeadas (4 px) y la base recta sobre el eje.
function barraRedondeada(x: number, y: number, ancho: number, alto: number) {
  const r = Math.min(4, ancho / 2, alto)
  return `M${x},${y + alto} V${y + r} Q${x},${y} ${x + r},${y} H${x + ancho - r} Q${x + ancho},${y} ${x + ancho},${y + r} V${y + alto} Z`
}
