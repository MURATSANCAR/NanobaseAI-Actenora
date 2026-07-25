# Windows PowerShell alternative for ./scripts/ci-build
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\bootstrap.ps1" -Command 'ci-build' @args
exit $LASTEXITCODE
