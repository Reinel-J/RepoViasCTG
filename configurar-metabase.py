#!/usr/bin/env python3
"""Arma el dashboard de ViaCTG en Metabase (los pasos 2 a 6 de docs/metabase.md).

Con docker compose no hace falta ejecutarlo: el servicio metabase-setup lo corre solo en modo --auto en cada arranque.

    python3 configurar-metabase.py                     # Metabase en http://localhost:3000, .env de esta carpeta
    python3 configurar-metabase.py --url http://…:3000 --env-file otro.env

A mano, usa tu cuenta de administrador de Metabase (la contraseña se pide sin mostrarla y no se guarda), crea preguntas
y un dashboard nuevos en cada ejecución y escribe en el .env la clave de embebido y el número del dashboard. Después:
docker compose up -d backend

    python3 configurar-metabase.py --auto --url http://metabase:3000 --config-dir /metabase-config

En modo --auto (el del servicio metabase-setup) no pregunta nada: si Metabase está recién instalado crea la cuenta de
administrador, arma el dashboard una sola vez y deja la clave y el número en --config-dir, de donde los lee el backend.
Si el dashboard ya existe, solo lo comprueba. Las consultas salen de docs/metabase.md.
"""
import argparse
import contextlib
import getpass
import json
import os
import re
import secrets
import sys
import time
import urllib.error
import urllib.request

RAIZ = os.path.dirname(os.path.abspath(__file__))
COLECCIONES_OCULTAS = ["usuarios", "tokens_recuperacion", "tokens_verificacion", "notificaciones"]
NOMBRE_DASHBOARD = "ViaCTG · Reportes ciudadanos"
# En el orden de los bloques ```json de docs/metabase.md: nombre, visualización, ajustes y posición (fila, columna,
# ancho, alto) en la grilla de 24 columnas del dashboard.
PREGUNTAS = [
    ("Reportes por estado", "bar", {"graph.dimensions": ["estado"], "graph.metrics": ["total"]}, (3, 0, 12, 6)),
    ("Porcentaje resuelto (sin rechazados)", "scalar", {"column_settings": {'["name","porcentajeResuelto"]': {"suffix": " %"}}}, (0, 0, 8, 3)),
    ("Reportes creados y resueltos por mes", "line", {"graph.dimensions": ["mes"], "graph.metrics": ["creados", "resueltos"]}, (9, 0, 24, 6)),
    ("Categorías con más reportes", "row", {"graph.dimensions": ["categoria"], "graph.metrics": ["total"]}, (15, 0, 12, 7)),
    ("Días promedio hasta la primera respuesta", "scalar", {}, (0, 8, 8, 3)),
    ("Días promedio hasta resolver", "scalar", {}, (0, 16, 8, 3)),
    ("Reportes activos en el mapa", "map", {"map.type": "pin", "map.latitude_column": "latitud", "map.longitude_column": "longitud"}, (15, 12, 12, 7)),
    ("Reportes por prioridad", "pie", {"pie.dimension": "prioridad", "pie.metric": "total"}, (3, 12, 12, 6)),
]


class Metabase:
    def __init__(self, url):
        self.url = url.rstrip("/")
        # La dirección que abre el navegador (dentro de docker compose, self.url es http://metabase:3000).
        self.url_publica = (os.environ.get("METABASE_SITE_URL") or self.url).rstrip("/")
        self.sesion = None

    def api(self, metodo, ruta, cuerpo=None, permitir_error=False):
        cabeceras = {"Content-Type": "application/json"}
        if self.sesion:
            cabeceras["X-Metabase-Session"] = self.sesion
        datos = None if cuerpo is None else json.dumps(cuerpo).encode()
        peticion = urllib.request.Request(self.url + ruta, data=datos, method=metodo, headers=cabeceras)
        try:
            with urllib.request.urlopen(peticion, timeout=120) as respuesta:
                texto = respuesta.read().decode()
                if not texto.strip():
                    return None
                try:
                    return json.loads(texto)
                except json.JSONDecodeError:
                    # Algunos ajustes (por ejemplo embedding-secret-key) llegan como texto plano, sin comillas.
                    return texto.strip()
        except urllib.error.HTTPError as error:
            if permitir_error:
                return None
            detalle = error.read().decode()[:300]
            if error.code == 401:
                sys.exit("Metabase rechazó el correo o la contraseña.")
            sys.exit(f"Metabase respondió {error.code} a {metodo} {ruta}: {detalle}")
        except urllib.error.URLError as error:
            if permitir_error:
                return None
            sys.exit(f"No se pudo conectar con Metabase en {self.url} ({error.reason}). ¿Está corriendo? docker compose ps metabase")


def leer_consultas():
    texto = open(os.path.join(RAIZ, "docs", "metabase.md"), encoding="utf-8").read()
    bloques = [json.loads(b) for b in re.findall(r"```json\n(\[.*?\])\n```", texto, re.S)]
    if len(bloques) != len(PREGUNTAS):
        sys.exit(f"docs/metabase.md tiene {len(bloques)} consultas y se esperaban {len(PREGUNTAS)}.")
    return bloques


def base_de_datos(mb, nombre_base):
    """La conexión a MongoDB con esa base; si no existe, la crea (host mongo, puerto 27017, sin usuario)."""
    respuesta = mb.api("GET", "/api/database")
    bases = respuesta["data"] if isinstance(respuesta, dict) else respuesta
    for base in bases:
        if base.get("engine") == "mongo" and (base.get("details") or {}).get("dbname") == nombre_base:
            print(f"· Base de datos: {base['name']} (ya estaba conectada)")
            return base["id"]
    base = mb.api("POST", "/api/database", {"engine": "mongo", "name": "ViaCTG", "details": {
        "host": "mongo", "port": 27017, "dbname": nombre_base, "use-conn-uri": False, "ssl": False}})
    print("· Base de datos: ViaCTG (conectada ahora)")
    return base["id"]


def colecciones(mb, base_id):
    for intento in range(90):
        tablas = {t["name"]: t["id"] for t in mb.api("GET", f"/api/database/{base_id}/metadata?include_hidden=true")["tables"]}
        if "reportes" in tablas:
            return tablas
        # Si la base se conectó antes de que existieran las colecciones, se pide otra lectura del esquema cada 30 s.
        if intento % 15 == 14:
            mb.api("POST", f"/api/database/{base_id}/sync_schema", permitir_error=True)
        time.sleep(2)
    sys.exit("Metabase no terminó de leer las colecciones; vuelve a intentarlo en un minuto.")


def actualizar_env(ruta, valores):
    lineas = open(ruta, encoding="utf-8").read().splitlines() if os.path.exists(ruta) else []
    for clave, valor in valores.items():
        for i, linea in enumerate(lineas):
            if linea.startswith(clave + "="):
                lineas[i] = f"{clave}={valor}"
                break
        else:
            lineas.append(f"{clave}={valor}")
    with open(ruta, "w", encoding="utf-8") as archivo:
        archivo.write("\n".join(lineas) + "\n")


def armar_dashboard(mb, nombre_base, consultas):
    """Conecta la base, oculta las colecciones sensibles, crea las preguntas y el dashboard y lo publica para incrustar.
    Devuelve (clave de embebido, número del dashboard)."""
    base_id = base_de_datos(mb, nombre_base)
    tablas = colecciones(mb, base_id)
    for nombre in COLECCIONES_OCULTAS:
        if nombre in tablas:
            mb.api("PUT", f"/api/table/{tablas[nombre]}", {"visibility_type": "hidden"})
    print("· Colecciones ocultas: " + ", ".join(n for n in COLECCIONES_OCULTAS if n in tablas))

    tarjetas = []
    for (nombre, visualizacion, ajustes, posicion), consulta in zip(PREGUNTAS, consultas):
        tarjeta = mb.api("POST", "/api/card", {"name": nombre, "display": visualizacion, "visualization_settings": ajustes,
            "dataset_query": {"database": base_id, "type": "native",
                              "native": {"query": json.dumps(consulta, ensure_ascii=False), "collection": "reportes"}}})
        tarjetas.append((tarjeta["id"], posicion))
    print(f"· Preguntas creadas: {len(tarjetas)}")

    dashboard = mb.api("POST", "/api/dashboard", {"name": NOMBRE_DASHBOARD})
    mb.api("PUT", f"/api/dashboard/{dashboard['id']}", {"dashcards": [
        {"id": -(i + 1), "card_id": tarjeta_id, "row": fila, "col": columna, "size_x": ancho, "size_y": alto}
        for i, (tarjeta_id, (fila, columna, ancho, alto)) in enumerate(tarjetas)]})

    clave_embebido = publicar(mb, dashboard["id"])
    print(f"· Dashboard «{NOMBRE_DASHBOARD}» publicado para incrustar: {mb.url_publica}/dashboard/{dashboard['id']}")
    return clave_embebido, dashboard["id"]


def publicar(mb, dashboard_id):
    """Habilita el embebido estático y el del dashboard; devuelve la clave de embebido (la crea si no hay)."""
    # El embebido estático ya lo habilita MB_ENABLE_EMBEDDING_STATIC; si se fijó por variable, Metabase no deja
    # cambiarlo aquí y se ignora el error.
    mb.api("PUT", "/api/setting/enable-embedding-static", {"value": True}, permitir_error=True)
    clave_embebido = mb.api("GET", "/api/setting/embedding-secret-key")
    if not clave_embebido:
        clave_embebido = secrets.token_hex(32)
        mb.api("PUT", "/api/setting/embedding-secret-key", {"value": clave_embebido})
    mb.api("PUT", f"/api/dashboard/{dashboard_id}", {"enable_embedding": True, "embedding_params": {}})
    return clave_embebido


def manual(opciones):
    consultas = leer_consultas()
    mb = Metabase(opciones.url)
    mb.api("GET", "/api/health")  # antes de pedir la contraseña: si Metabase no responde, se avisa aquí
    correo = os.environ.get("METABASE_ADMIN_EMAIL") or input("Correo de tu cuenta de administrador de Metabase: ").strip()
    clave = os.environ.get("METABASE_ADMIN_PASSWORD") or getpass.getpass("Contraseña (no se muestra): ")
    mb.sesion = mb.api("POST", "/api/session", {"username": correo, "password": clave})["id"]
    del clave
    try:
        clave_embebido, dashboard_id = armar_dashboard(mb, opciones.base, consultas)
        actualizar_env(opciones.env_file, {"METABASE_SECRET_KEY": clave_embebido, "METABASE_DASHBOARD_ID": dashboard_id})
        print(f"· {opciones.env_file}: METABASE_SECRET_KEY y METABASE_DASHBOARD_ID={dashboard_id} actualizados")
    finally:
        mb.api("DELETE", "/api/session", permitir_error=True)
    print("\nListo. Para que el backend lea los valores nuevos:\n  docker compose up -d backend")


# --- Modo automático (servicio metabase-setup de docker compose) ---

ARCHIVO_CLAVE_EMBEBIDO = "secret-key"   # lo lee el backend (METABASE_CONFIG_DIR)
ARCHIVO_DASHBOARD = "dashboard-id"      # lo lee el backend
ARCHIVO_CONTRASENA = "admin-password"   # solo root: el backend corre con otro usuario y no puede leerlo


def leer_archivo(carpeta, nombre):
    with contextlib.suppress(FileNotFoundError):
        return open(os.path.join(carpeta, nombre), encoding="utf-8").read().strip()
    return ""


def escribir_archivo(carpeta, nombre, valor, permisos):
    """Escribe en un temporal y lo renombra: el backend nunca lee un archivo a medio escribir."""
    ruta = os.path.join(carpeta, nombre)
    temporal = ruta + ".tmp"
    with open(os.open(temporal, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, permisos), "w", encoding="utf-8") as archivo:
        archivo.write(f"{valor}\n")
    os.chmod(temporal, permisos)
    os.replace(temporal, ruta)


def esperar_metabase(mb, intentos=60):
    for _ in range(intentos):
        if (mb.api("GET", "/api/health", permitir_error=True) or {}).get("status") == "ok":
            return
        time.sleep(5)
    sys.exit(f"Metabase no respondió en {mb.url}.")


def crear_administrador(mb, correo, clave):
    """Primer arranque de Metabase: crea su cuenta de administrador (lo que hace el asistente de la interfaz)."""
    token = mb.api("GET", "/api/session/properties")["setup-token"]
    sesion = mb.api("POST", "/api/setup", {
        "token": token,
        "user": {"first_name": "Administrador", "last_name": "ViaCTG", "email": correo, "password": clave,
                 "site_name": "ViaCTG"},
        "prefs": {"site_name": "ViaCTG", "site_locale": "es", "allow_tracking": False}})
    return sesion["id"]


def dashboard_vigente(mb, dashboard_id):
    if not dashboard_id.isdigit():
        return False
    dashboard = mb.api("GET", f"/api/dashboard/{dashboard_id}", permitir_error=True)
    return bool(dashboard) and not dashboard.get("archived") and dashboard.get("name") == NOMBRE_DASHBOARD


def automatico(opciones):
    if os.environ.get("METABASE_SECRET_KEY") and os.environ.get("METABASE_DASHBOARD_ID"):
        print("· El dashboard está configurado a mano en el .env (METABASE_SECRET_KEY y METABASE_DASHBOARD_ID): nada que hacer.")
        return
    carpeta = opciones.config_dir
    os.makedirs(carpeta, exist_ok=True)
    consultas = leer_consultas()
    mb = Metabase(opciones.url)
    esperar_metabase(mb)

    correo = os.environ.get("METABASE_ADMIN_EMAIL") or os.environ.get("ADMIN_EMAIL") or "admin@viasctg.local"
    clave = os.environ.get("METABASE_ADMIN_PASSWORD") or leer_archivo(carpeta, ARCHIVO_CONTRASENA)
    if not mb.api("GET", "/api/session/properties")["has-user-setup"]:
        if not clave:
            # Cumple la política de Metabase (al menos un dígito) con 24 caracteres aleatorios.
            clave = secrets.token_urlsafe(18) + str(secrets.randbelow(10))
        if not os.environ.get("METABASE_ADMIN_PASSWORD"):
            escribir_archivo(carpeta, ARCHIVO_CONTRASENA, clave, 0o600)
        mb.sesion = crear_administrador(mb, correo, clave)
        print(f"· Cuenta de administrador de Metabase creada: {correo}")
    else:
        sesion = mb.api("POST", "/api/session", {"username": correo, "password": clave}, permitir_error=True) if clave else None
        if not sesion:
            if leer_archivo(carpeta, ARCHIVO_DASHBOARD):
                print("· No se pudo entrar a Metabase para comprobar el dashboard; se deja la configuración que ya había.")
            else:
                print("· Metabase ya tenía una cuenta de administrador creada a mano y no se pudo entrar con ella.\n"
                      "  Define METABASE_ADMIN_EMAIL y METABASE_ADMIN_PASSWORD en el .env con esa cuenta y vuelve a\n"
                      "  ejecutar: docker compose up -d metabase-setup")
            return
        mb.sesion = sesion["id"]
    del clave

    try:
        dashboard_id = leer_archivo(carpeta, ARCHIVO_DASHBOARD)
        if dashboard_vigente(mb, dashboard_id):
            clave_embebido = publicar(mb, dashboard_id)
            print(f"· El dashboard {mb.url_publica}/dashboard/{dashboard_id} ya estaba listo.")
        else:
            clave_embebido, dashboard_id = armar_dashboard(mb, opciones.base, consultas)
        escribir_archivo(carpeta, ARCHIVO_CLAVE_EMBEBIDO, clave_embebido, 0o644)
        escribir_archivo(carpeta, ARCHIVO_DASHBOARD, dashboard_id, 0o644)
    finally:
        mb.api("DELETE", "/api/session", permitir_error=True)
    print("Listo: el dashboard se ve en Panel › Dashboard para administradores y moderadores.")


def main():
    argumentos = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    argumentos.add_argument("--url", default="http://localhost:3000", help="dirección de Metabase (por defecto http://localhost:3000)")
    argumentos.add_argument("--env-file", default=os.path.join(RAIZ, ".env"), help="archivo .env donde escribir la clave y el id")
    argumentos.add_argument("--base", default="viasctg_db", help="base de MongoDB (por defecto viasctg_db)")
    argumentos.add_argument("--auto", action="store_true", help="sin preguntas, para el servicio metabase-setup de docker compose")
    argumentos.add_argument("--config-dir", default="/metabase-config", help="con --auto: carpeta compartida con el backend")
    opciones = argumentos.parse_args()
    if opciones.auto:
        automatico(opciones)
    else:
        manual(opciones)


if __name__ == "__main__":
    main()
