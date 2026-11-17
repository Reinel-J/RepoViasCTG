import { describe, expect, it } from 'vitest'
import { agruparPorCelda } from './agrupacion'

// Proyección de prueba: un grado = 100 píxeles, sin distorsión.
const proyectar = (latitud: number, longitud: number) => ({ x: longitud * 100, y: -latitud * 100 })
const punto = (id: string, latitud: number, longitud: number) => ({ id, latitud, longitud })

describe('agruparPorCelda', () => {
  it('junta los puntos de la misma celda y los centra en su promedio', () => {
    const grupos = agruparPorCelda([punto('a', 0.1, 0.1), punto('b', 0.3, 0.3), punto('c', 5, 5)], proyectar, 60)

    expect(grupos).toHaveLength(2)
    const juntos = grupos.find((grupo) => grupo.puntos.length === 2)!
    expect(juntos.clave).toMatch(/^celda:/)
    expect(juntos.latitud).toBeCloseTo(0.2)
    expect(juntos.longitud).toBeCloseTo(0.2)
  })

  it('un punto solo conserva su id como clave, para que su ventanita no se cierre al mover el mapa', () => {
    const grupos = agruparPorCelda([punto('a', 0.1, 0.1), punto('c', 5, 5)], proyectar, 60)

    expect(grupos.map((grupo) => grupo.clave).sort()).toEqual(['a', 'c'])
  })

  it('une grupos de celdas vecinas cuyos círculos quedarían encimados', () => {
    // 0.59 y 0.61 caen en celdas distintas (borde en 0.6) pero a 2 píxeles uno del otro.
    const grupos = agruparPorCelda([punto('a', 0.1, 0.59), punto('b', 0.1, 0.58), punto('c', 0.1, 0.61), punto('d', 0.1, 0.62)], proyectar, 60)

    expect(grupos).toHaveLength(1)
    expect(grupos[0].puntos).toHaveLength(4)
  })

  it('con celda 0 no agrupa', () => {
    const grupos = agruparPorCelda([punto('a', 0.1, 0.1), punto('b', 0.1, 0.1)], proyectar, 0)

    expect(grupos.map((grupo) => grupo.clave)).toEqual(['a', 'b'])
  })
})
