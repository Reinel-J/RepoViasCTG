import { describe, expect, it } from "vitest"
import { coincideBusqueda, coincideFiltro, empiezaPalabra, normalizarBusqueda, plural, valoresUnicos } from "./texto"

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

describe("coincideFiltro", () => {
  it("sin filtro deja pasar todo, incluso valores vacíos", () => {
    expect(coincideFiltro("", "Hueco")).toBe(true)
    expect(coincideFiltro("", null)).toBe(true)
  })

  it("compara el valor completo sin distinguir tildes ni mayúsculas", () => {
    expect(coincideFiltro("Manga", "manga")).toBe(true)
    expect(coincideFiltro("Inundación", "inundacion")).toBe(true)
    expect(coincideFiltro("Manga", "Pie de la Popa, Manga")).toBe(false)
    expect(coincideFiltro("Manga", null)).toBe(false)
  })
})

describe("valoresUnicos", () => {
  it("deduplica sin distinguir tildes ni mayúsculas, conserva la primera forma y ordena", () => {
    expect(valoresUnicos(["Manga", "manga", "Bocagrande", null, " ", "El Bosque"])).toEqual(["Bocagrande", "El Bosque", "Manga"])
  })
})

describe("plural", () => {
  it("usa el singular solo para 1", () => {
    expect(plural(1, "reporte")).toBe("1 reporte")
    expect(plural(0, "reporte")).toBe("0 reportes")
    expect(plural(3, "comentario")).toBe("3 comentarios")
  })

  it("acepta un plural irregular", () => {
    expect(plural(2, "reporte activo", "reportes activos")).toBe("2 reportes activos")
    expect(plural(1, "reporte activo", "reportes activos")).toBe("1 reporte activo")
  })
})

describe("empiezaPalabra", () => {
  it("busca la clave al inicio de alguna palabra, sin tildes ni mayúsculas", () => {
    expect(empiezaPalabra("Señal de PARE", "pare")).toBe(true)
    expect(empiezaPalabra("Inundación en Manga", "inunda")).toBe(true)
    expect(empiezaPalabra("Vía sin pavimentar", "sin pavimentar")).toBe(true)
    expect(empiezaPalabra("Por reparar", "pare")).toBe(false)
  })
})
