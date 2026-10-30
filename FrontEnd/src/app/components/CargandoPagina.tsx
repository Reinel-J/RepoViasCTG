// Lo que muestra cada ruta mientras llega su código en la primera carga (HydrateFallback de React Router). Sin él,
// React Router avisaba en la consola («No HydrateFallback element provided») y la página quedaba en blanco.
export function CargandoPagina() {
  return <div className="loading-panel" role="status">Cargando…</div>
}
