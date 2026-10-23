@echo off
setlocal
rem Prefer an installed PowerShell, without relying on Codex's runtime.
set "SIGER_PWSH="
if exist "%ProgramFiles%\PowerShell\7\pwsh.exe" set "SIGER_PWSH=%ProgramFiles%\PowerShell\7\pwsh.exe"
if not defined SIGER_PWSH if exist "%LOCALAPPDATA%\Microsoft\WindowsApps\pwsh.exe" set "SIGER_PWSH=%LOCALAPPDATA%\Microsoft\WindowsApps\pwsh.exe"
if not defined SIGER_PWSH (
  echo No se encontro PowerShell 7.4 o superior instalado en Windows.
  echo Instala Microsoft PowerShell 7 y vuelve a ejecutar FORMULARIO.cmd.
  exit /b 1
)
"%SIGER_PWSH%" -NoLogo -NoProfile -File "%~dp0formulario-publico\scripts\Formulario.ps1" %*
set "SIGER_RESULT=%ERRORLEVEL%"
if not "%SIGER_RESULT%"=="0" if "%~1"=="" pause
exit /b %SIGER_RESULT%
