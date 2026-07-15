[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
Push-Location $root
try {
    docker compose --env-file .env.example down
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose could not stop the showcase.' }
}
finally {
    Pop-Location
}
