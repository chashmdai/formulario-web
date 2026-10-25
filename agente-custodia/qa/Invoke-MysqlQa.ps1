[CmdletBinding()]
param([Parameter(Mandatory)][string]$Jar,[Parameter(Mandatory)][string]$Url,[string]$Class='cl.nexbyte.siger.custodia.MysqlTlsLab',[string[]]$Arguments=@('inspect'),[switch]$Administrar,[string]$HostsFile)
$ErrorActionPreference='Stop'
$c=$null;$p=$null
try{
 if(-not (Test-Path -LiteralPath $Jar -PathType Leaf)){throw 'JAR verificado requerido'}
 $cred=if($Administrar){'C:\agente\config\mysql-lab-root.credential.xml'}else{'C:\agente\config\mysql-lab-app.credential.xml'}
 $c=Import-Clixml -LiteralPath $cred
 $i=[Diagnostics.ProcessStartInfo]::new();$i.FileName='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe';$i.UseShellExecute=$false;$i.CreateNoWindow=$true;$i.RedirectStandardInput=$true
 if($HostsFile){$i.ArgumentList.Add('-Djdk.net.hosts.file='+$HostsFile)}
 foreach($a in @('--enable-native-access=ALL-UNNAMED','-cp',($Jar+';C:\agente\testdata\qa-native\classes'),$Class)){$i.ArgumentList.Add($a)}
 $i.ArgumentList.Add($Arguments[0]);$i.ArgumentList.Add($Url);foreach($a in ($Arguments|Select-Object -Skip 1)){$i.ArgumentList.Add($a)}
 $p=[Diagnostics.Process]::Start($i)
 $p.StandardInput.Write((@{usuario=$c.UserName;password=$c.GetNetworkCredential().Password}|ConvertTo-Json -Compress));$p.StandardInput.Close();$c=$null
 if(-not $p.WaitForExit(60000)){$p.Kill($true);throw 'Timeout QA'}
 exit $p.ExitCode
}finally{$c=$null;if($p){try{if(-not $p.HasExited){$p.Kill($true)}}finally{$p.Dispose()}}}
