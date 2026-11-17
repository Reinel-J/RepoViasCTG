#!/usr/bin/env bash
# Demo pública de ViaCTG con un túnel rápido de Cloudflare (servicio "tunel" de docker-compose.yml).
#
#   ./demo-tunel.sh iniciar [--build]   levanta todo, enciende el túnel y deja su dirección en FRONTEND_URL
#   ./demo-tunel.sh url                 muestra la dirección pública actual
#   ./demo-tunel.sh detener             apaga el túnel y devuelve FRONTEND_URL a http://localhost:5173
#
# La dirección (https://….trycloudflare.com) cambia cada vez que el túnel arranca, y los enlaces de los correos
# (confirmar cuenta, recuperar contraseña) se arman con FRONTEND_URL: por eso el script la copia al .env y reinicia
# el backend. Respeta COMPOSE_PROJECT_NAME y COMPOSE_FILE si están definidas.
set -euo pipefail

cd "$(dirname "$0")"
ENV_FILE=.env
LOCAL_URL=http://localhost:5173
compose() { docker compose --profile tunel "$@"; }

die() { echo "Error: $*" >&2; exit 1; }

# Reemplaza (o agrega) FRONTEND_URL en el .env sin tocar las demás líneas.
fijar_frontend_url() {
  local url=$1
  [ -f "$ENV_FILE" ] || die "no existe $ENV_FILE (cópialo de .env.example)"
  if grep -q '^FRONTEND_URL=' "$ENV_FILE"; then
    sed -i "s|^FRONTEND_URL=.*|FRONTEND_URL=$url|" "$ENV_FILE"
  else
    printf '\nFRONTEND_URL=%s\n' "$url" >> "$ENV_FILE"
  fi
}

# "|| true": mientras la dirección no aparece, grep falla y set -e cortaría el script sin avisar.
url_del_tunel() {
  { compose logs tunel 2>/dev/null | grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' | tail -1; } || true
}

# Recrea el backend para que lea el FRONTEND_URL nuevo y espera a que responda.
reiniciar_backend() {
  compose up -d backend >/dev/null
  echo -n "Esperando al backend"
  for _ in $(seq 1 90); do
    if compose exec -T backend wget -qO- http://localhost:8080/actuator/health 2>/dev/null | grep -q '"UP"'; then
      echo " listo."; return 0
    fi
    echo -n "."; sleep 2
  done
  echo; die "el backend no respondió en 3 minutos: revisa 'docker compose logs backend'"
}

iniciar() {
  local build=()
  [ "${1:-}" = "--build" ] && build=(--build)
  [ -f "$ENV_FILE" ] || die "no existe $ENV_FILE (cópialo de .env.example y define JWT_SECRET)"

  echo "Levantando Mongo, backend y frontend…"
  docker compose up -d "${build[@]}"

  echo "Encendiendo el túnel…"
  # --force-recreate: un contenedor nuevo, para leer solo la dirección de este arranque en sus logs.
  compose up -d --force-recreate tunel >/dev/null
  local url=""
  for _ in $(seq 1 30); do
    url=$(url_del_tunel)
    [ -n "$url" ] && break
    sleep 2
  done
  [ -n "$url" ] || die "el túnel no entregó una dirección: revisa 'docker compose --profile tunel logs tunel'"

  fijar_frontend_url "$url"
  echo "FRONTEND_URL=$url (guardado en $ENV_FILE)"
  reiniciar_backend

  # El nombre recién creado tarda unos segundos en resolverse en internet.
  echo -n "Esperando a que la dirección pública responda"
  for _ in $(seq 1 30); do
    if [ "$(curl -s -o /dev/null -w '%{http_code}' "$url/" || true)" = 200 ]; then
      echo " listo."
      echo
      echo "ViaCTG está en línea:  $url"
      echo "Funciona mientras este PC esté encendido. Para apagarlo: ./demo-tunel.sh detener"
      return 0
    fi
    echo -n "."; sleep 3
  done
  echo
  echo "El túnel está encendido en $url, pero todavía no responde; prueba abrirla en un minuto."
}

detener() {
  echo "Apagando el túnel…"
  compose stop tunel >/dev/null 2>&1 || true
  fijar_frontend_url "$LOCAL_URL"
  echo "FRONTEND_URL=$LOCAL_URL (guardado en $ENV_FILE)"
  if [ -n "$(docker compose ps -q backend 2>/dev/null)" ]; then
    reiniciar_backend
  fi
  echo "Listo: la app sigue disponible en $LOCAL_URL. Para apagar todo: docker compose down"
}

case "${1:-}" in
  iniciar) shift; iniciar "$@" ;;
  url)
    url=$(url_del_tunel)
    if [ -n "$url" ] && [ -n "$(compose ps -q --status running tunel 2>/dev/null)" ]; then echo "$url"; else echo "El túnel no está encendido."; fi ;;
  detener) detener ;;
  *) sed -n '2,7p' "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
