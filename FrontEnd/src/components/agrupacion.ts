// Agrupa los puntos que caen en la misma celda de la pantalla. Con miles de reportes el mapa no puede dibujar un
// marcador por cada uno (se traba y los pines se tapan entre sí): se dibuja un círculo con la cantidad por celda y,
// al acercarse, las celdas se achican hasta que cada reporte queda con su propio pin. No importa Leaflet: recibe la
// proyección a píxeles, así se puede probar sin mapa.

export interface PuntoAgrupable {
  id: string
  latitud: number
  longitud: number
}

export interface Grupo<T extends PuntoAgrupable> {
  // El id del reporte si está solo; si no, la celda (cambia al hacer zoom, y con ella el marcador).
  clave: string
  puntos: T[]
  // Centro del grupo: el promedio de sus puntos, para que el círculo quede donde están los daños.
  latitud: number
  longitud: number
}

export function agruparPorCelda<T extends PuntoAgrupable>(
  puntos: T[],
  proyectar: (latitud: number, longitud: number) => { x: number; y: number },
  tamanoCelda: number,
): Grupo<T>[] {
  if (tamanoCelda <= 0) return puntos.map((punto) => solo(punto))
  const celdas = new Map<string, T[]>()
  for (const punto of puntos) {
    const { x, y } = proyectar(punto.latitud, punto.longitud)
    const clave = `${Math.floor(x / tamanoCelda)}:${Math.floor(y / tamanoCelda)}`
    const celda = celdas.get(clave)
    if (celda) celda.push(punto)
    else celdas.set(clave, [punto])
  }
  // Dos celdas vecinas pueden dejar sus círculos casi encima (los daños de un barrio caen a ambos lados del borde):
  // el grupo más grande absorbe a los que tengan el centro a menos de una celda del suyo.
  const pendientes = [...celdas].map(([clave, contenido]) => ({ clave, contenido, centro: centroEnPantalla(contenido, proyectar) }))
    .sort((a, b) => b.contenido.length - a.contenido.length)
  const fusionados: { clave: string; contenido: T[] }[] = []
  const absorbido = new Set<number>()
  pendientes.forEach((grupo, i) => {
    if (absorbido.has(i)) return
    const contenido = [...grupo.contenido]
    for (let j = i + 1; j < pendientes.length; j++) {
      if (absorbido.has(j)) continue
      const otro = pendientes[j].centro
      if (Math.hypot(otro.x - grupo.centro.x, otro.y - grupo.centro.y) < tamanoCelda) {
        contenido.push(...pendientes[j].contenido)
        absorbido.add(j)
      }
    }
    fusionados.push({ clave: grupo.clave, contenido })
  })
  return fusionados.map(({ clave, contenido }) => {
    if (contenido.length === 1) return solo(contenido[0])
    const latitud = contenido.reduce((suma, punto) => suma + punto.latitud, 0) / contenido.length
    const longitud = contenido.reduce((suma, punto) => suma + punto.longitud, 0) / contenido.length
    return { clave: `celda:${clave}`, puntos: contenido, latitud, longitud }
  })
}

function centroEnPantalla<T extends PuntoAgrupable>(puntos: T[], proyectar: (latitud: number, longitud: number) => { x: number; y: number }) {
  const posiciones = puntos.map((punto) => proyectar(punto.latitud, punto.longitud))
  return {
    x: posiciones.reduce((suma, posicion) => suma + posicion.x, 0) / posiciones.length,
    y: posiciones.reduce((suma, posicion) => suma + posicion.y, 0) / posiciones.length,
  }
}

function solo<T extends PuntoAgrupable>(punto: T): Grupo<T> {
  return { clave: punto.id, puntos: [punto], latitud: punto.latitud, longitud: punto.longitud }
}
