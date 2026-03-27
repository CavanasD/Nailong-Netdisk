param(
    [string]$CfmsRoot = "D:\NetdiskProject\CFMS_WebSocket\cfms_on_websocket",
    [switch]$SkipSync
)

$ErrorActionPreference = "Stop"

$projectFile = Join-Path $CfmsRoot "pyproject.toml"
$mainFile = Join-Path $CfmsRoot "src\main.py"
$runtimeDir = Join-Path $CfmsRoot "src"
$configSample = Join-Path $CfmsRoot "src\config.toml.sample"
$configFile = Join-Path $CfmsRoot "src\config.toml"
$logsDir = Join-Path $runtimeDir "content\logs"
$sslDir = Join-Path $runtimeDir "content\ssl"

if (-not (Test-Path $projectFile)) {
    throw "CFMS project not found: $projectFile"
}
if (-not (Test-Path $mainFile)) {
    throw "CFMS main entry not found: $mainFile"
}

Push-Location $CfmsRoot
try {
    if (-not (Test-Path $configFile)) {
        if (-not (Test-Path $configSample)) {
            throw "Missing config template: $configSample"
        }
        Copy-Item $configSample $configFile -Force
        Write-Host "Created config.toml from sample" -ForegroundColor Yellow
    }

    if (-not $SkipSync) {
        # --link-mode=copy avoids hardlink warning across different filesystems on Windows.
        uv sync --dev --link-mode=copy
    }
}
finally {
    Pop-Location
}

Push-Location $runtimeDir
try {
    New-Item -ItemType Directory -Path $logsDir -Force | Out-Null
    New-Item -ItemType Directory -Path $sslDir -Force | Out-Null

    Write-Host "Starting CFMS server..." -ForegroundColor Cyan
    uv run --project .. python .\main.py
}
finally {
    Pop-Location
}

