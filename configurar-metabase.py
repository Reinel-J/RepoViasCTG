#!/usr/bin/env python3
"""Arma el dashboard de ViaCTG en Metabase con tu cuenta de administrador de Metabase y escribe en el .env la clave de
embebido y el número del dashboard (los pasos 2 a 6 de docs/metabase.md).

    python3 configurar-metabase.py                     # Metabase en http://localhost:3000, .env de esta carpeta
    python3 configurar-metabase.py --url http://…:3000 --env-file otro.env

La contraseña se pide sin mostrarla y no se guarda. Las consultas salen de docs/metabase.md. Cada ejecución crea preguntas
y un dashboard nuevos (las anteriores quedan en Metabase). Después: docker compose up -d backend
"""
import argparse
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
                return json.loads(texto) if texto.strip() else None
        except urllib.error.HTTPError as error:
            if permitir_error:
                return None
            detalle = error.read().decode()[:300]
            if error.code == 401:
                sys.exit("Metabase rechazó el correo o la contraseña.")
            sys.exit(f"Metabase respondió {error.code} a {metodo} {ruta}: {detalle}")
        except urllib.error.URLError as error:
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
    for _ in range(60):
        tablas = {t["name"]: t["id"] for t in mb.api("GET", f"/api/database/{base_id}/metadata?include_hidden=true")["tables"]}
        if "reportes" in tablas:
            return tablas
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


def main():
    argumentos = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    argumentos.add_argument("--url", default="http://localhost:3000", help="dirección de Metabase (por defecto http://localhost:3000)")
    argumentos.add_argument("--env-file", default=os.path.join(RAIZ, ".env"), help="archivo .env donde escribir la clave y el id")
    argumentos.add_argument("--base", default="viasctg_db", help="base de MongoDB (por defecto viasctg_db)")
    opciones = argumentos.parse_args()

    consultas = leer_consultas()
    mb = Metabase(opciones.url)
    mb.api("GET", "/api/health")  # antes de pedir la contraseña: si Metabase no responde, se avisa aquí
    correo = os.environ.get("METABASE_ADMIN_EMAIL") or input("Correo de tu cuenta de administrador de Metabase: ").strip()
    clave = os.environ.get("METABASE_ADMIN_PASSWORD") or getpass.getpass("Contraseña (no se muestra): ")
    mb.sesion = mb.api("POST", "/api/session", {"username": correo, "password": clave})["id"]
    del clave
    try:
        base_id = base_de_datos(mb, opciones.base)
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

        # El embebido estático ya lo habilita MB_ENABLE_EMBEDDING_STATIC; si se fijó por variable, Metabase no deja
        # cambiarlo aquí y se ignora el error.
        mb.api("PUT", "/api/setting/enable-embedding-static", {"value": True}, permitir_error=True)
        clave_embebido = mb.api("GET", "/api/setting/embedding-secret-key")
        if not clave_embebido:
            clave_embebido = secrets.token_hex(32)
            mb.api("PUT", "/api/setting/embedding-secret-key", {"value": clave_embebido})
        mb.api("PUT", f"/api/dashboard/{dashboard['id']}", {"enable_embedding": True, "embedding_params": {}})
        print(f"· Dashboard «{NOMBRE_DASHBOARD}» publicado para incrustar: {opciones.url.rstrip('/')}/dashboard/{dashboard['id']}")

        actualizar_env(opciones.env_file, {"METABASE_SECRET_KEY": clave_embebido, "METABASE_DASHBOARD_ID": dashboard["id"]})
        print(f"· {opciones.env_file}: METABASE_SECRET_KEY y METABASE_DASHBOARD_ID={dashboard['id']} actualizados")
    finally:
        mb.api("DELETE", "/api/session", permitir_error=True)
    print("\nListo. Para que el backend lea los valores nuevos:\n  docker compose up -d backend")


if __name__ == "__main__":
    main()
