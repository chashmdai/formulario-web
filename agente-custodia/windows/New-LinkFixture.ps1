param([Parameter(Mandatory)][string]$Link,[Parameter(Mandatory)][string]$Target)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath('C:\agente\testdata\');foreach($path in @($Link,$Target)){if(-not [IO.Path]::GetFullPath($path).StartsWith($root,[StringComparison]::OrdinalIgnoreCase)){throw 'Solo fixtures bajo testdata'}}
if(Test-Path -LiteralPath $Link){throw 'No sobrescribir fixture'}
$null=New-Item -ItemType Junction -Path $Link -Target $Target