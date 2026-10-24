[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
Push-Location $root
try {
    . (Join-Path $PSScriptRoot 'DemoEnvironment.ps1')
    Write-Host "Using environment file: $DemoEnvironmentFile"
    # Compose only acts on the profiles it is given, so name the optional ones or their containers outlive the stop.
    docker compose --env-file $DemoEnvironmentFile --profile tools --profile observability down
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose could not stop the showcase.' }
}
finally {
    Pop-Location
}
