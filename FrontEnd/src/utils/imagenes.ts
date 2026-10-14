// Prepara una foto antes de subirla: la vuelve a dibujar en un canvas, lo que descarta los metadatos EXIF
// (incluida la ubicación GPS del teléfono y el modelo del dispositivo), y la reduce a un tamaño razonable
// para que pese poco en datos móviles. Si el navegador no puede procesarla, se usa el archivo original.

const LADO_MAXIMO = 1600
const CALIDAD_JPEG = 0.82

export async function prepararFoto(archivo: File): Promise<File> {
  if (typeof document === "undefined" || !archivo.type.startsWith("image/")) return archivo
  const url = URL.createObjectURL(archivo)
  try {
    const imagen = new Image()
    imagen.src = url
    await imagen.decode()
    // Los navegadores actuales ya aplican la orientación EXIF al dibujar, así que la foto no queda girada.
    const escala = Math.min(1, LADO_MAXIMO / Math.max(imagen.naturalWidth, imagen.naturalHeight))
    const ancho = Math.max(1, Math.round(imagen.naturalWidth * escala))
    const alto = Math.max(1, Math.round(imagen.naturalHeight * escala))
    const canvas = document.createElement("canvas")
    canvas.width = ancho
    canvas.height = alto
    const contexto = canvas.getContext("2d")
    if (!contexto) return archivo
    // Fondo blanco: un PNG con transparencia quedaría negro al pasarlo a JPEG.
    contexto.fillStyle = "#ffffff"
    contexto.fillRect(0, 0, ancho, alto)
    contexto.drawImage(imagen, 0, 0, ancho, alto)
    const blob = await new Promise<Blob | null>((resolver) => canvas.toBlob(resolver, "image/jpeg", CALIDAD_JPEG))
    if (!blob) return archivo
    const nombre = archivo.name.replace(/\.[^.]+$/, "") || "foto"
    return new File([blob], `${nombre}.jpg`, { type: "image/jpeg", lastModified: archivo.lastModified })
  } catch {
    return archivo
  } finally {
    URL.revokeObjectURL(url)
  }
}
