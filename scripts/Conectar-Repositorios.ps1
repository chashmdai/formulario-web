#Requires -Version 7.4
[CmdletBinding()]
param([Parameter(Mandatory)][string]$OtroRepositorio,[switch]$Comprobar)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$other=(Resolve-Path -LiteralPath $OtroRepositorio).Path
if($repo.TrimEnd('\') -eq $other.TrimEnd('\')){throw 'Indique el otro clon, no este repositorio.'}
$missing=@(@('formulario-publico','siger-institucional')|Where-Object{!(Test-Path -LiteralPath (Join-Path $repo ($_+'/backend/pom.xml')))})
if(!$missing){Write-Host 'Ambas aplicaciones ya están disponibles. No se modificó nada.';return}
if(@($missing).Count -ne 1){throw 'Este repositorio debe contener exactamente una aplicación antes de conectarlo.'}
$app=$missing[0]
$source=Join-Path $other $app
$target=Join-Path $repo $app
if(!(Test-Path -LiteralPath (Join-Path $source 'backend/pom.xml')) -or !(Test-Path -LiteralPath (Join-Path $source 'front/package.json'))){throw 'El otro clon no contiene la aplicación requerida.'}
if(Test-Path -LiteralPath $target){throw 'La ruta destino existe pero no es una aplicación válida. No se sobrescribe.'}
if($Comprobar){Write-Host ('Conexión válida, sin cambios: '+$app);return}
# Junction de directorio local: no duplica código, no mueve bases ni copia secretos.
# Cada repositorio ignora expresamente la carpeta de la otra aplicación.
New-Item -ItemType Junction -Path $target -Target $source|Out-Null
Write-Host ('Conectada '+$app+'. Ejecute los scripts integrados siempre desde esta raíz; su .local será el estado del laboratorio.')
