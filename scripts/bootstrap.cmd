@ECHO OFF
REM Windows alternative for ./scripts/bootstrap
SETLOCAL
powershell -ExecutionPolicy Bypass -File "%~dp0bootstrap.ps1" %*
EXIT /B %ERRORLEVEL%
