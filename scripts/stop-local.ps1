# Windows PowerShell alternative for ./scripts/stop-local
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\bootstrap.ps1" -Command 'stop-local' @args
exit $LASTEXITCODE
