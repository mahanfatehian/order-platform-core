[CmdletBinding()]
param([switch]$Force)

if (-not $Force) {
    throw 'This permanently removes local PostgreSQL, Redis, and Prometheus demo data. Re-run with -Force.'
}

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
Push-Location $root
try {
    docker compose --env-file .env.example down --volumes --remove-orphans
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose could not reset the showcase.' }
    Write-Host 'Local demo data was removed. Run .\scripts\start-demo.ps1 to recreate it.' -ForegroundColor Green
}
finally {
    Pop-Location
}
