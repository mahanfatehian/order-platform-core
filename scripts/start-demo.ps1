[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
Push-Location $root
try {
    . (Join-Path $PSScriptRoot 'DemoEnvironment.ps1')
    Write-Host 'Starting the Order/flow showcase...'
    Write-Host "Using environment file: $DemoEnvironmentFile"
    docker compose --env-file $DemoEnvironmentFile up -d --build --wait --wait-timeout 360
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose did not reach a healthy state.' }
    docker compose --env-file $DemoEnvironmentFile ps
    Write-Host ''
    Write-Host 'Order/flow is ready at http://localhost:8080' -ForegroundColor Green
    Write-Host 'Demo personas are listed on the sign-in page and in README.md.'
}
finally {
    Pop-Location
}
