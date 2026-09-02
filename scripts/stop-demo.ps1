[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
Push-Location $root
try {
    . (Join-Path $PSScriptRoot 'DemoEnvironment.ps1')
    Write-Host "Using environment file: $DemoEnvironmentFile"
    docker compose --env-file $DemoEnvironmentFile down
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose could not stop the showcase.' }
}
finally {
    Pop-Location
}
