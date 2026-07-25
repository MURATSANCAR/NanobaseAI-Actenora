# Windows PowerShell alternative for ./scripts/run-local
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\bootstrap.ps1" -Command 'run-local' @args
exit $LASTEXITCODE
