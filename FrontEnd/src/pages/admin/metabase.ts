/** Inicio de Metabase a partir de la URL firmada: lo que va antes de /embed/ (relativa, p. ej. /metabase, o absoluta). */
export function inicioMetabase(urlFirmada: string) {
  const fin = urlFirmada.indexOf("/embed/")
  return (fin >= 0 ? urlFirmada.slice(0, fin) : new URL(urlFirmada, window.location.href).origin) + "/"
}
