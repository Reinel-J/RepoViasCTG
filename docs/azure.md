# Publicar ViaCTG en una VM de Azure (con el dashboard)

Esta guía deja ViaCTG en internet con HTTPS en una máquina virtual de Azure. El dashboard de Metabase se ve en la
misma dirección del sitio, en `https://<tu-dominio>/metabase/`. Todo corre con el mismo `docker-compose.yml` del
repositorio:

- **Caddy** (perfil `https`) recibe el tráfico en los puertos 80 y 443, pide y renueva solo el certificado de
  Let's Encrypt y lo reenvía al nginx del frontend.
- **nginx** sirve el sitio y reenvía `/api` y `/uploads` al backend y `/metabase/` a Metabase.
- **metabase-setup** arma el dashboard en el primer arranque, sin pasos manuales.

```text
navegador ──HTTPS──▶ Caddy :443 ──▶ nginx (frontend) ──┬─▶ backend :8080 ──▶ MongoDB / PostgreSQL
                                                       └─▶ /metabase/ ──▶ Metabase :3000 ──▶ MongoDB
```

A internet solo quedan abiertos los puertos 80 y 443 (y el 22 para SSH, idealmente solo desde tu IP). MongoDB,
PostgreSQL, el backend y Metabase no se publican.

## 1. Crear la VM

Tamaño recomendado: **Standard_B2ms** (2 vCPU, 8 GB de RAM). Metabase, el backend (Java), MongoDB y PostgreSQL juntos
no caben con holgura en 4 GB. Sistema: **Ubuntu Server 24.04 LTS**.

### Desde el portal

1. *Virtual machines › Create › Azure virtual machine*: Ubuntu Server 24.04 LTS, tamaño Standard_B2ms, autenticación
   con clave SSH.
2. *Inbound port rules*: permite **SSH (22)**, **HTTP (80)** y **HTTPS (443)**.
3. Crea la VM. Luego, en su **IP pública › Configuration**: deja la asignación en *Static* y pon un **DNS name label**
   (por ejemplo `viactg-demo`). Tu dominio queda como `viactg-demo.<región>.cloudapp.azure.com`. Si tienes un dominio
   propio, crea en tu proveedor un registro `A` hacia esa IP y usa ese dominio.
4. En *Networking*, edita la regla de SSH para que solo acepte tu IP (*Source: My IP address*).

### O con la CLI de Azure (`az`)

```bash
az group create --name viactg --location eastus
az vm create --resource-group viactg --name viactg-vm --image Ubuntu2404 --size Standard_B2ms \
  --admin-username azureuser --generate-ssh-keys \
  --public-ip-sku Standard --public-ip-address-dns-name viactg-demo
az vm open-port --resource-group viactg --name viactg-vm --port 80,443 --priority 1010
```

`az vm create` muestra la IP pública y el dominio (`fqdn`). Abre SSH a todo internet por defecto: restríngelo a tu IP
en el grupo de seguridad de red (NSG) de la VM.

## 2. Instalar Docker en la VM

```bash
ssh azureuser@viactg-demo.eastus.cloudapp.azure.com
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
exit   # vuelve a entrar para que el grupo docker tenga efecto
```

## 3. Configurar el proyecto

```bash
git clone https://github.com/Reinel-J/RepoViasCTG.git
cd RepoViasCTG
cp .env.example .env
nano .env
```

Valores que hay que poner en el `.env` (los demás pueden quedar como vienen):

| Variable | Valor |
| --- | --- |
| `JWT_SECRET` | `openssl rand -base64 32` |
| `SQL_PASSWORD` | `openssl rand -base64 18` |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Tu cuenta de administrador de ViaCTG (contraseña fuerte). |
| `DOMINIO` | `viactg-demo.eastus.cloudapp.azure.com` (o tu dominio propio). |
| `FRONTEND_URL` | `https://viactg-demo.eastus.cloudapp.azure.com` (el mismo dominio, con `https://`). |
| `METABASE_SITE_URL` | `/metabase` (ya viene así). |
| `SEED_DEMO` | `true` para mostrar la demo con datos; `false` en un despliegue real. |
| `SWAGGER_ENABLED` | `false` para no publicar la documentación de la API. |

`METABASE_SECRET_KEY` y `METABASE_DASHBOARD_ID` se dejan **vacíos**: los pone `metabase-setup`.

## 4. Levantar todo

```bash
docker compose --profile https up -d --build
```

El primer arranque tarda unos minutos: hay que compilar el backend y el frontend, y Metabase crea su base interna.
Para seguirlo:

```bash
docker compose ps                      # todo en running / healthy (metabase-setup termina con "exited (0)")
docker compose logs https              # Caddy: "certificate obtained successfully"
docker compose logs metabase-setup     # "Dashboard «ViaCTG · Reportes ciudadanos» publicado…"
```

Abre `https://<tu-dominio>`, entra con `ADMIN_EMAIL` y `ADMIN_PASSWORD` y ve a **Panel › Dashboard**. Lo ven todos
los administradores y moderadores.

Para editar el dashboard en Metabase (enlace «Abrir en Metabase» o `https://<tu-dominio>/metabase/`), entra con el
correo de `ADMIN_EMAIL` y esta contraseña:

```bash
docker compose run --rm --no-deps --entrypoint cat metabase-setup /metabase-config/admin-password
```

## 5. Actualizar a una versión nueva

```bash
cd RepoViasCTG
git pull
docker compose --profile https up -d --build
```

Los datos quedan en los volúmenes de Docker (`mongo_data`, `postgres_data`, `uploads_data`, `metabase_data`,
`metabase_config` y `caddy_data`, que guarda el certificado). No los borres con `docker compose down -v`.

## Problemas frecuentes

| Síntoma | Causa y solución |
| --- | --- |
| El navegador no abre el sitio o Caddy no obtiene el certificado | Los puertos 80 y 443 no están abiertos en el NSG, o `DOMINIO` no apunta a la IP pública de la VM (`nslookup <dominio>`). Revisa `docker compose logs https`. |
| «El dashboard no está configurado» | `metabase-setup` todavía no termina (tarda unos minutos en el primer arranque) o falló: `docker compose logs metabase-setup`. Para repetirlo: `docker compose up metabase-setup`. |
| El dashboard sale en blanco | `METABASE_SITE_URL` no es `/metabase` o `FRONTEND_URL` no es la dirección `https://` del sitio. Corrige el `.env` y ejecuta `docker compose --profile https up -d`. |
| Los enlaces de los correos apuntan a `localhost` | `FRONTEND_URL` sigue en `http://localhost:5173`. |
| La VM se queda sin memoria | Usa un tamaño de 8 GB (Standard_B2ms) o mayor. |
