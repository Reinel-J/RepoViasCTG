#!/usr/bin/env python3
"""Exporta los reportes de ViaCTG a CSV para Power BI (guía: docs/powerbi.md).

    python3 exportar-powerbi.py                          # base viasctg_db → powerbi-datos/ (ignorada por git)
    python3 exportar-powerbi.py --db otra --salida carpeta

Lee MongoDB a través del contenedor "mongo" de docker compose. Escribe reportes.csv, historial_estados.csv y
categorias.csv en UTF-8 con BOM (Power BI y Excel leen bien las tildes), con punto decimal y las fechas en hora de
Colombia (UTC−5, sin horario de verano) como AAAA-MM-DD HH:MM:SS. No exporta datos de usuarios: ni el autor de cada
reporte, ni el moderador de cada cambio, ni las descripciones escritas por los ciudadanos.
"""
import argparse
import csv
import json
import os
import subprocess
import sys
from datetime import datetime, timedelta, timezone

RAIZ = os.path.dirname(os.path.abspath(__file__))
COLOMBIA = timezone(timedelta(hours=-5))
ACTIVOS = {"PENDIENTE", "EN_REVISION", "EN_PROCESO"}

# Solo los campos que usa el reporte; EJSON relajado: fechas como {"$date": "…Z"} y ObjectId como {"$oid": …}.
CONSULTA = """
const proyeccion = {categoria: 1, estado: 1, prioridad: 1, barrioOsm: 1, direccionOsm: 1, latitud: 1, longitud: 1,
  fechaCreacion: 1, fechaActualizacion: 1, "historialEstados.estadoAnterior": 1, "historialEstados.estadoNuevo": 1,
  "historialEstados.fecha": 1, "historialEstados.comentarioAdmin": 1};
print(EJSON.stringify({
  reportes: db.reportes.find({}, proyeccion).sort({fechaCreacion: 1}).toArray(),
  categorias: db.categorias.find({}, {nombre: 1, descripcion: 1, activa: 1}).sort({nombre: 1}).toArray()
}, {relaxed: true}));
"""


def leer_base(nombre_base):
    comando = ["docker", "compose", "exec", "-T", "mongo", "mongosh", "--quiet", nombre_base, "--eval", CONSULTA]
    try:
        salida = subprocess.run(comando, cwd=RAIZ, check=True, capture_output=True, text=True).stdout
    except FileNotFoundError:
        sys.exit("No se encontró docker. Ejecuta el script en el equipo donde corre docker compose.")
    except subprocess.CalledProcessError as error:
        sys.exit(f"No se pudo leer MongoDB ({error.stderr.strip()[:200]}). ¿Está corriendo? docker compose ps mongo")
    return json.loads(salida)


def identificador(valor):
    return valor["$oid"] if isinstance(valor, dict) else valor


def fecha(valor):
    """Fecha y hora de Colombia, o vacío si no hay fecha."""
    if not valor:
        return ""
    texto = valor["$date"] if isinstance(valor, dict) else valor
    instante = datetime.fromisoformat(texto.replace("Z", "+00:00"))
    return instante.astimezone(COLOMBIA).strftime("%Y-%m-%d %H:%M:%S")


def escribir(ruta, columnas, filas):
    with open(ruta, "w", encoding="utf-8-sig", newline="") as archivo:
        escritor = csv.writer(archivo)
        escritor.writerow(columnas)
        escritor.writerows(filas)
    print(f"· {os.path.relpath(ruta, RAIZ)}: {len(filas)} filas")


def main():
    argumentos = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    argumentos.add_argument("--db", default="viasctg_db", help="base de MongoDB (por defecto viasctg_db)")
    argumentos.add_argument("--salida", default=os.path.join(RAIZ, "powerbi-datos"), help="carpeta de los CSV (por defecto powerbi-datos/, fuera de git)")
    opciones = argumentos.parse_args()

    datos = leer_base(opciones.db)
    os.makedirs(opciones.salida, exist_ok=True)

    reportes, historial = [], []
    for reporte in datos["reportes"]:
        cambios = reporte.get("historialEstados") or []
        resuelto = next((c for c in cambios if c.get("estadoNuevo") == "RESUELTO"), None)
        primera = cambios[0] if cambios else None
        reporte_id = identificador(reporte["_id"])
        creado = fecha(reporte.get("fechaCreacion"))
        resuelto_en = fecha(resuelto["fecha"]) if resuelto else ""
        reportes.append([
            reporte_id, (reporte.get("categoria") or {}).get("nombre", ""), (reporte.get("categoria") or {}).get("id", ""),
            reporte.get("estado", ""), reporte.get("prioridad", ""),
            reporte.get("barrioOsm") or "", reporte.get("direccionOsm") or "", reporte.get("latitud", ""), reporte.get("longitud", ""),
            "SI" if reporte.get("estado") in ACTIVOS else "NO",
            creado, creado[:10], fecha(reporte.get("fechaActualizacion")),
            fecha(primera["fecha"]) if primera else "", resuelto_en, resuelto_en[:10]])
        for orden, cambio in enumerate(cambios, start=1):
            historial.append([reporte_id, orden, cambio.get("estadoAnterior", ""), cambio.get("estadoNuevo", ""),
                              fecha(cambio.get("fecha")), cambio.get("comentarioAdmin") or ""])

    escribir(os.path.join(opciones.salida, "reportes.csv"),
             ["id", "categoria", "categoria_id", "estado", "prioridad", "barrio", "direccion", "latitud", "longitud", "activo",
              "fecha_creacion", "dia_creacion", "fecha_actualizacion", "fecha_primera_respuesta", "fecha_resuelto", "dia_resuelto"],
             reportes)
    escribir(os.path.join(opciones.salida, "historial_estados.csv"),
             ["reporte_id", "orden", "estado_anterior", "estado_nuevo", "fecha", "comentario"], historial)
    escribir(os.path.join(opciones.salida, "categorias.csv"), ["id", "nombre", "descripcion", "activa"],
             [[identificador(c["_id"]), c.get("nombre", ""), c.get("descripcion") or "", "SI" if c.get("activa") else "NO"]
              for c in datos["categorias"]])


if __name__ == "__main__":
    main()
