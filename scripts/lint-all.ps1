# Windows PowerShell alternative for ./scripts/lint-all
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\bootstrap.ps1" -Command 'lint-all' @args
exit $LASTEXITCODE
