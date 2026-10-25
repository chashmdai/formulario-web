[CmdletBinding()]
param([Parameter(Mandatory)][string]$Jar,[Parameter(Mandatory)][string]$Config,[Parameter(Mandatory)][string]$Destination,[string]$PowerShell='C:\Program Files\PowerShell\7\pwsh.exe',[ValidateSet('Inspeccionar','VerificarTLS','Enviar')][string]$Modo='Inspeccionar',[ValidateSet('InteractiveToken','Password')][string]$LogonType='InteractiveToken')
$ErrorActionPreference='Stop'
foreach($path in @($Jar,$Config,$PowerShell)){if(-not(Test-Path -LiteralPath $path -PathType Leaf)){throw 'Recurso preparado requerido'};if($path.Contains('"')){throw 'Ruta inválida'}}
if(Test-Path -LiteralPath $Destination){throw 'No sobrescribir una definición histórica'}
$service=New-Object -ComObject 'Schedule.Service';$service.Connect();$definition=$service.NewTask(0)
$definition.RegistrationInfo.Description='Agente auxiliar de custodia: definición de laboratorio NO registrada, sin habilitación productiva'
$definition.Principal.UserId=[Security.Principal.WindowsIdentity]::GetCurrent().Name;$definition.Principal.LogonType=$(if($LogonType -eq "Password"){1}else{3});$definition.Principal.RunLevel=0
$definition.Settings.MultipleInstances=2;$definition.Settings.ExecutionTimeLimit='PT10M';$definition.Settings.RestartCount=3;$definition.Settings.RestartInterval='PT5M';$definition.Settings.Enabled=$false
$trigger=$definition.Triggers.Create(1);$trigger.StartBoundary=[DateTime]::Now.AddMinutes(15).ToString('s');$trigger.Repetition.Interval='PT15M';$trigger.Enabled=$false
$action=$definition.Actions.Create(0);$action.Path=$PowerShell;$action.WorkingDirectory='C:\agente\scripts';$action.Arguments='-NoProfile -NonInteractive -File "C:\agente\scripts\Invoke-AgenteCustodia.ps1" -Jar "'+$Jar+'" -Config "'+$Config+'" -Modo '+$Modo+' -TimeoutSeconds 540'
[IO.File]::WriteAllText($Destination,$definition.XmlText,[Text.UnicodeEncoding]::new($false,$true))
[pscustomobject]@{DefinicionPreparada=$true;Registrada=$false;Habilitada=$false;Identidad=$definition.Principal.UserId;Intervalo='PT15M';Reintentos=3;Timeout='PT10M'}|ConvertTo-Json -Compress
# No existe ninguna llamada RegisterTask/Register-ScheduledTask ni Start-ScheduledTask.