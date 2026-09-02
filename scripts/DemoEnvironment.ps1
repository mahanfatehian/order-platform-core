if (-not [string]::IsNullOrEmpty($env:ORDER_PLATFORM_ENV_FILE)) {
    $DemoEnvironmentFile = $env:ORDER_PLATFORM_ENV_FILE
}
elseif (Test-Path -LiteralPath '.env' -PathType Leaf) {
    $DemoEnvironmentFile = '.env'
}
else {
    $DemoEnvironmentFile = '.env.example'
}

if (-not (Test-Path -LiteralPath $DemoEnvironmentFile -PathType Leaf)) {
    throw "Environment file not found: $DemoEnvironmentFile"
}
