#Requires -Version 7.4
[CmdletBinding()]
param([ValidateSet('iniciar','publico','siger','estado','detener','detener-publico','detener-siger','pruebas','demostrar','demostrar-derivacion','acceso','compilar')][string]$Accion='estado',[switch]$SinClaveUnica,[string]$OrigenFuncionario=$env:NUCLEO_LOCAL_ORIGIN)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if($Accion -eq 'compilar'){& (Join-Path $PSScriptRoot 'Build-Todo.ps1');return}
if($Accion -eq 'acceso'){
 $path=Join-Path $repo '.local/nna/nucleo-password.clixml'
 if(!(Test-Path -LiteralPath $path)){throw 'Inicie primero el núcleo de laboratorio.'}
 Write-Host 'Solo identidades sintéticas locales. Contraseña compartida de laboratorio:'
 Write-Host ([Net.NetworkCredential]::new('',(Import-Clixml -LiteralPath $path)).Password)
 Write-Host 'Usuarios: demo.distribucion; demo.jefatura.upr.a; demo.profesional.upr.a; demo.profesional2.upr.a; demo.jefatura.upd.a; demo.profesional.upd.a; demo.jefatura.uep.b; demo.profesional.uep.b; demo.jefatura.ugi.b; demo.profesional.ugi.b'
 return
}
if($Accion -in @('demostrar','demostrar-derivacion')){
 $previousCA=$env:NODE_EXTRA_CA_CERTS; $previousStaff=$env:NUCLEO_LOCAL_ORIGIN
 try {
  # Primero prueba toda la custodia pública, pull HTTPS, ACK y firma. Solo datos sintéticos.
  & (Join-Path $PSScriptRoot 'Nna-Local.ps1') demostrar
  $output=(Get-Content (Join-Path $repo '.local/nna/ultima-prueba.txt') -Raw).Trim()
  # Toma nuevas huellas después del ACK, antes de la gestión, para verificar inmutabilidad.
  $before=Join-Path $output 'gestion';New-Item -ItemType Directory -Path $before -Force|Out-Null
  Copy-Item -LiteralPath (Join-Path $output 'resultado-http.json') -Destination $before
  & (Join-Path $PSScriptRoot 'Verificar-Nna-MySQL.ps1') antes -Evidencias $before
  $env:NODE_EXTRA_CA_CERTS=Join-Path $repo '.local/https/ca/rootCA.pem'
  $env:NUCLEO_LOCAL_ORIGIN=(& (Join-Path $PSScriptRoot 'Origen-Funcionario.ps1') -Origen $OrigenFuncionario).Origen
  $env:NUCLEO_DEMO_PASSWORD=[Net.NetworkCredential]::new('',(Import-Clixml (Join-Path $repo '.local/nna/nucleo-password.clixml'))).Password
  $demoScript=if($Accion -eq 'demostrar-derivacion'){'Probar-Derivacion.mjs'}else{'Probar-Nucleo.mjs'}
  & node (Join-Path $PSScriptRoot $demoScript) $output
  if($LASTEXITCODE -ne 0){throw 'Demostración institucional incompleta. Consulte las evidencias locales.'}
  & (Join-Path $PSScriptRoot 'Verificar-Nna-MySQL.ps1') despues -Evidencias $before
  & (Join-Path $PSScriptRoot 'Test-Sesiones-Separadas.ps1') -OrigenFuncionario $OrigenFuncionario -Evidencias $output | Out-Null
  Write-Host ('Demostración completa: '+$output)
 }finally{$env:NUCLEO_LOCAL_ORIGIN=$previousStaff;$env:NUCLEO_DEMO_PASSWORD=$null;$env:NODE_EXTRA_CA_CERTS=$previousCA}
 return
}
& (Join-Path $PSScriptRoot 'Nna-Local.ps1') $Accion -NucleoInstitucional -SinClaveUnica:$SinClaveUnica -OrigenFuncionario $OrigenFuncionario



