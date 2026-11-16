# Crea el usuario "viasctg" y las bases de la réplica SQL en el PostgreSQL local (ver crear-base-local.sql).
# Toma la contraseña de la aplicación de local.properties (app.sql.password) y pide la del usuario "postgres".
#
#   powershell -ExecutionPolicy Bypass -File sql\crear-base-local.ps1     (desde la carpeta ViasCTG)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
$propiedades = Join-Path $raiz 'local.properties'
if (-not (Test-Path $propiedades)) {
    Write-Error "No existe $propiedades. Cópialo de local.properties.example y define app.sql.password."
}
$linea = Select-String -Path $propiedades -Pattern '^\s*app\.sql\.password\s*=\s*(.+?)\s*$' | Select-Object -First 1
if (-not $linea) { Write-Error "Falta app.sql.password en $propiedades." }

$psql = Join-Path $env:ProgramFiles 'PostgreSQL\17\bin\psql.exe'
if (-not (Test-Path $psql)) { $psql = 'psql' }

$env:SQL_PASSWORD = $linea.Matches[0].Groups[1].Value
try {
    Write-Host 'Escribe la contraseña del usuario "postgres" (la que definiste al instalar PostgreSQL):'
    & $psql -h localhost -U postgres -d postgres -f (Join-Path $PSScriptRoot 'crear-base-local.sql')
    if ($LASTEXITCODE -ne 0) { Write-Error "psql terminó con código $LASTEXITCODE." }
} finally {
    Remove-Item Env:SQL_PASSWORD -ErrorAction SilentlyContinue
}
