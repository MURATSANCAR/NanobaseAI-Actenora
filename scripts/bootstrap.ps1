# Windows PowerShell alternative for Unix scripts/*.
# Usage: .\scripts\bootstrap.ps1 | build-all.ps1 | test-all.ps1 | lint-all.ps1 | run-local.ps1 | stop-local.ps1
param(
  [Parameter(Position = 0)]
  [ValidateSet('bootstrap', 'build-all', 'test-all', 'lint-all', 'run-local', 'stop-local', 'ci-build', 'ci-test')]
  [string]$Command = 'bootstrap'
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$BashScript = Join-Path $PSScriptRoot $Command

function Find-Bash {
  $candidates = @(
    'bash',
    "$env:ProgramFiles\Git\bin\bash.exe",
    "$env:LOCALAPPDATA\Programs\Git\bin\bash.exe"
  )
  foreach ($c in $candidates) {
    if (Get-Command $c -ErrorAction SilentlyContinue) {
      return (Get-Command $c).Source
    }
  }
  throw 'bash not found. Install Git for Windows or WSL, then re-run.'
}

$bash = Find-Bash
& $bash $BashScript @args
exit $LASTEXITCODE
