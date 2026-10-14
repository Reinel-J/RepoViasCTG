import { describe, expect, it } from "vitest"
import { coincideBusqueda, normalizarBusqueda, valoresUnicos } from "./texto"

describe("normalizarBusqueda", () => {
  it("ignora tildes, mayúsculas y espacios repetidos", () => {
    expect(normalizarBusqueda("  Inundación   en  MANGA ")).toBe("inundacion en manga")
    expect(normalizarBusqueda("Daño en alcantarillado")).toBe("dano en alcantarillado")
    expect(normalizarBusqueda(null)).toBe("")
  })
})

describe("coincideBusqueda", () => {
  it("encuentra el término en cualquiera de los campos", () => {
    expect(coincideBusqueda("inundacion", "Inundación", "Calle 30")).toBe(true)
    expect(coincideBusqueda("calle 30", "Hueco", "Calle 30, Manga")).toBe(true)
    expect(coincideBusqueda("bocagrande", "Hueco", "Calle 30, Manga", null)).toBe(false)
  })

  it("una búsqueda vacía coincide con todo", () => {
    expect(coincideBusqueda("   ", "Hueco")).toBe(true)
  })
})

describe("valoresUnicos", () => {
  it("deduplica sin distinguir tildes ni mayúsculas, conserva la primera forma y ordena", () => {
    expect(valoresUnicos(["Manga", "manga", "Bocagrande", null, " ", "El Bosque"])).toEqual(["Bocagrande", "El Bosque", "Manga"])
  })
})
