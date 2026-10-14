// Solo rutas internas de la aplicación: evita que un estado de navegación manipulado redirija a otro sitio
// después de iniciar sesión (por ejemplo "//sitio-falso.com" o "https://...").
export function rutaSegura(ruta: string | null | undefined): string {
  return ruta && ruta.startsWith("/") && !ruta.startsWith("//") && !ruta.startsWith("/\\") ? ruta : "/"
}
