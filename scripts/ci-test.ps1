# Windows PowerShell alternative for ./scripts/ci-test
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\bootstrap.ps1" -Command 'ci-test' @args
exit $LASTEXITCODE
