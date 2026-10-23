#Requires -Version 7.4
[CmdletBinding()]
param([ValidateSet('demostrar','comprobar','estado')][string]$Accion='estado',[string]$OrigenFuncionario=$env:NUCLEO_LOCAL_ORIGIN)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if($Accion -eq 'estado'){& (Join-Path $PSScriptRoot 'Nucleo-Local.ps1') estado -OrigenFuncionario $OrigenFuncionario;return}
# Fallar antes de enviar datos si se retiró la resolución local del nombre público.
$addresses=[Net.Dns]::GetHostAddresses('claveunica.formularioadulto.defensorianinez.gob.cl')
if(!$addresses.Count -or @($addresses|Where-Object{![Net.IPAddress]::IsLoopback($_)}).Count){throw 'La demo requiere resolución exclusivamente loopback. No se enviaron datos ni se modifica hosts.'}
$staff=& (Join-Path $PSScriptRoot 'Origen-Funcionario.ps1') -Origen $OrigenFuncionario
if($Accion -eq 'comprobar'){Write-Host 'Orígenes de demostración limitados a loopback; sin envío.';return}
$output=Join-Path $repo ('.local/nna/evidencias/antecedentes-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $output -Force|Out-Null
$savedCA=$env:NODE_EXTRA_CA_CERTS;$savedOrigin=$env:NUCLEO_LOCAL_ORIGIN
try {
 & (Join-Path $PSScriptRoot 'Verificar-Nna-MySQL.ps1') antes -Evidencias $output
 $registrations=@()
 # Seis ingresos NNA sintéticos reales: custodia, pull, registro, ACK y comprobante.
 foreach($n in 1..6){
  & (Join-Path $PSScriptRoot 'Nna-Local.ps1') demostrar
  $evidence=(Get-Content (Join-Path $repo '.local/nna/ultima-prueba.txt') -Raw).Trim()
  $r=(Get-Content (Join-Path $evidence 'resultado-http.json') -Raw|ConvertFrom-Json).registro
  $registrations+=$r
  Copy-Item -LiteralPath $evidence -Destination (Join-Path $output ('ingreso-'+$n)) -Recurse
 }
 $registrations|ConvertTo-Json -Depth 8|Set-Content (Join-Path $output 'ingresos.json')
 Copy-Item -LiteralPath (Join-Path $output 'ingreso-1/resultado-http.json') -Destination (Join-Path $output 'resultado-http.json')
 $env:NODE_EXTRA_CA_CERTS=Join-Path $repo '.local/https/ca/rootCA.pem'
 $env:NUCLEO_LOCAL_ORIGIN=$staff.Origen
 $env:NUCLEO_DEMO_PASSWORD=[Net.NetworkCredential]::new('',(Import-Clixml (Join-Path $repo '.local/nna/nucleo-password.clixml'))).Password
 & node (Join-Path $PSScriptRoot 'Probar-Antecedentes.mjs') $output
 if($LASTEXITCODE -ne 0){throw 'Demostración de antecedentes incompleta; revisar evidencias privadas.'}
 & (Join-Path $PSScriptRoot 'Verificar-Nna-MySQL.ps1') despues -Evidencias $output
 & (Join-Path $PSScriptRoot 'Test-Sesiones-Separadas.ps1') -OrigenFuncionario $OrigenFuncionario -Evidencias $output|Out-Null
 Set-Content (Join-Path $repo '.local/nna/ultima-prueba-antecedentes.txt') $output
 Write-Host ('Evidencias de antecedentes: '+$output)
} finally {$env:NUCLEO_DEMO_PASSWORD=$null;$env:NODE_EXTRA_CA_CERTS=$savedCA;$env:NUCLEO_LOCAL_ORIGIN=$savedOrigin}
