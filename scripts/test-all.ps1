# Windows PowerShell alternative for ./scripts/test-all
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\bootstrap.ps1" -Command 'test-all' @args
exit $LASTEXITCODE
