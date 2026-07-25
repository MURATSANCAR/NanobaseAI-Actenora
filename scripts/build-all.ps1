# Windows PowerShell alternative for ./scripts/build-all
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\bootstrap.ps1" -Command 'build-all' @args
exit $LASTEXITCODE
