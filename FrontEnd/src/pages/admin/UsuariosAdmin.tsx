import { useEffect, useState } from "react"
import * as adminApi from "../../api/admin"
import type { Rol, UsuarioResponse } from "../../api/types"
import { ROLES, mensajeError } from "./estados"

const ETIQUETA_ROL: Record<Rol, string> = { CIUDADANO: "Ciudadano", MODERADOR: "Moderador", ADMIN: "Administrador" }

export function UsuariosAdmin({ usuarioActualId }: { usuarioActualId?: string }) {
  const [usuarios, setUsuarios] = useState<UsuarioResponse[]>([])
  const [busqueda, setBusqueda] = useState("")
  const [filtroRol, setFiltroRol] = useState<Rol | "">("")
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState("")
  const [aviso, setAviso] = useState("")
  const [procesandoId, setProcesandoId] = useState<string | null>(null)

  useEffect(() => {
    adminApi.listarUsuarios()
      .then((lista) => setUsuarios([...lista].sort((a, b) => a.nombre.localeCompare(b.nombre, "es"))))
      .catch((exception) => setError(mensajeError(exception, "No fue posible cargar los usuarios.")))
      .finally(() => setCargando(false))
  }, [])

  async function ejecutar(usuario: UsuarioResponse, accion: () => Promise<UsuarioResponse>, mensaje: string) {
    setProcesandoId(usuario.id)
    setError("")
    setAviso("")
    try {
      const actualizado = await accion()
      setUsuarios((actuales) => actuales.map((item) => (item.id === actualizado.id ? actualizado : item)))
      setAviso(mensaje)
    } catch (exception) {
      setError(mensajeError(exception, "No fue posible actualizar el usuario."))
    } finally {
      setProcesandoId(null)
    }
  }

  function cambiarRol(usuario: UsuarioResponse, rol: Rol) {
    if (rol === "ADMIN" && !window.confirm(`¿Dar permisos de administrador a ${usuario.nombre}?`)) return
    ejecutar(usuario, () => adminApi.cambiarRol(usuario.id, rol), `${usuario.nombre} ahora es ${ETIQUETA_ROL[rol].toLowerCase()}.`)
  }

  function alternarActivo(usuario: UsuarioResponse) {
    if (usuario.activo && !window.confirm(`¿Desactivar a ${usuario.nombre}? No podrá iniciar sesión ni usar su sesión actual.`)) return
    ejecutar(usuario, () => adminApi.cambiarActivo(usuario.id, !usuario.activo), `${usuario.nombre} fue ${usuario.activo ? "desactivado" : "activado"}.`)
  }

  const texto = busqueda.trim().toLowerCase()
  const visibles = usuarios.filter((usuario) =>
    (!filtroRol || usuario.rol === filtroRol)
    && (!texto || usuario.nombre.toLowerCase().includes(texto) || usuario.email.includes(texto)))

  return (
    <section className="admin-section">
      <div className="toolbar">
        <label>Rol
          <select value={filtroRol} onChange={(event) => setFiltroRol(event.target.value as Rol | "")}>
            <option value="">Todos</option>
            {ROLES.map((rol) => <option key={rol} value={rol}>{ETIQUETA_ROL[rol]}</option>)}
          </select>
        </label>
        <label>Buscar
          <input type="search" value={busqueda} onChange={(event) => setBusqueda(event.target.value)} placeholder="Nombre o correo" />
        </label>
        <span className="muted">{visibles.length} de {usuarios.length} usuarios</span>
      </div>
      <p className="muted admin-note">Los cambios de rol y de estado se aplican de inmediato en el servidor. La persona afectada verá su nuevo menú al volver a iniciar sesión.</p>
      {cargando && <div className="loading">Cargando usuarios…</div>}
      {error && <p className="alert error" role="alert">{error}</p>}
      {aviso && <p className="alert success" role="status">{aviso}</p>}
      <div className="admin-table-wrap">
        <table className="admin-table">
          <thead>
            <tr><th>Usuario</th><th>Teléfono</th><th>Registro</th><th>Rol</th><th>Estado</th></tr>
          </thead>
          <tbody>
            {visibles.map((usuario) => {
              const esYo = usuario.id === usuarioActualId
              const ocupado = procesandoId === usuario.id
              return (
                <tr key={usuario.id} className={usuario.activo ? "" : "is-inactive"}>
                  <td><strong>{usuario.nombre}{esYo && " (tú)"}</strong><small>{usuario.email}</small></td>
                  <td>{usuario.telefono ?? "—"}</td>
                  <td>{new Date(usuario.fechaRegistro).toLocaleDateString("es-CO")}</td>
                  <td>
                    <select aria-label={`Rol de ${usuario.nombre}`} value={usuario.rol} disabled={esYo || ocupado} onChange={(event) => cambiarRol(usuario, event.target.value as Rol)}>
                      {ROLES.map((rol) => <option key={rol} value={rol}>{ETIQUETA_ROL[rol]}</option>)}
                    </select>
                  </td>
                  <td>
                    <button className={usuario.activo ? "button button-ghost button-small" : "button button-dark button-small"} disabled={esYo || ocupado} onClick={() => alternarActivo(usuario)}>
                      {usuario.activo ? "Desactivar" : "Activar"}
                    </button>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </section>
  )
}
