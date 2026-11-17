import { describe, expect, it } from "vitest"
import { campoCsv, textoCsv } from "./csv"

describe("campoCsv", () => {
  it("deja igual el texto simple y los números con punto decimal", () => {
    expect(campoCsv("Hueco")).toBe("Hueco")
    expect(campoCsv(24.8)).toBe("24.8")
  })

  it("pone entre comillas lo que lleva coma, comillas o salto de línea", () => {
    expect(campoCsv("Alcantarilla destapada, rota")).toBe('"Alcantarilla destapada, rota"')
    expect(campoCsv('El "hueco" grande')).toBe('"El ""hueco"" grande"')
    expect(campoCsv("dos\nlíneas")).toBe('"dos\nlíneas"')
  })

  it("deja vacíos los valores ausentes", () => {
    expect(campoCsv(null)).toBe("")
    expect(campoCsv(undefined)).toBe("")
  })
})

describe("textoCsv", () => {
  it("separa columnas con coma y filas con CRLF, terminando en salto de línea", () => {
    expect(textoCsv([["a", "b"], [1, null]])).toBe("a,b\r\n1,\r\n")
  })
})
