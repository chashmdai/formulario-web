# Solo runners efímeros de GitHub; nunca ejecutarlo para preparar el servidor institucional.
$ErrorActionPreference='Stop'
if($env:GITHUB_ACTIONS -ne 'true'){throw 'Exclusivo CI efímera'}
if(Test-Path -LiteralPath 'C:\agente'){throw 'No reutilizar una instalación existente'}
foreach($path in @('C:\agente','C:\agente\testdata','C:\agente\scripts')){
 $acl=[Security.AccessControl.DirectorySecurity]::new();$acl.SetAccessRuleProtection($true,$false)
 foreach($sid in @([Security.Principal.WindowsIdentity]::GetCurrent().User,[Security.Principal.SecurityIdentifier]::new('S-1-5-18'),[Security.Principal.SecurityIdentifier]::new('S-1-5-32-544'))){$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid,'FullControl','ContainerInherit,ObjectInherit','None','Allow'))}
 [IO.FileSystemAclExtensions]::CreateDirectory($acl,$path)|Out-Null
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'New-LinkFixture.ps1') -Destination 'C:\agente\scripts\New-LinkFixture.ps1'
