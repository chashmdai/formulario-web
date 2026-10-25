[CmdletBinding()]
param([Parameter(Mandatory)][string]$Jar,[Parameter(Mandatory)][string]$Config,[ValidateSet('Inspeccionar','VerificarTLS','Ensayo','Enviar')][string]$Modo='Inspeccionar',[string]$Id,[string]$Cola='COMPLETO',[string]$CredentialPath='C:\agente\config\mysql-lab-app.credential.xml',[string]$Java='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe',[string]$LogDirectory='C:\agente\logs',[ValidateRange(5,600)][int]$TimeoutSeconds=120,[ValidateRange(1,90)][int]$LogRetentionDays=14,[ValidateRange(100,10000)][int]$MaxLogFiles=1000,[ValidateRange(1048576,67108864)][long]$MaxLogBytes=16777216)
$ErrorActionPreference='Stop'
$process=$null; $credential=$null; $frame=$null; $result=20
try {
    if(-not [IO.Path]::IsPathFullyQualified($Java) -or -not (Test-Path -LiteralPath $Java -PathType Leaf)){throw 'Java absoluto requerido'}
    foreach($path in @($Jar,$Config)){if(-not (Test-Path -LiteralPath $path -PathType Leaf)){throw 'Archivo requerido ausente'}}
    if(-not (Test-Path -LiteralPath $LogDirectory -PathType Container)){throw 'Directorio de logs preparado requerido'}
    if((Get-Item -LiteralPath $LogDirectory).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Logs sin reparse requerido'}
    $info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=$Java;$info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardInput=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true;$info.StandardInputEncoding=[Text.UTF8Encoding]::new($false)
    foreach($arg in @('--enable-native-access=ALL-UNNAMED','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-jar',$Jar,'--config',$Config)){$info.ArgumentList.Add($arg)}
    switch($Modo){'Inspeccionar'{$info.ArgumentList.Add('--inspeccionar')};'VerificarTLS'{$info.ArgumentList.Add('--verificar-tls')};'Enviar'{$info.ArgumentList.Add('--enviar')};'Ensayo'{if(-not $Id){throw 'ID sintético requerido'};$parsed=[guid]::Parse($Id);foreach($arg in @('--ensayo','--id',$parsed.ToString(),'--cola',$Cola)){$info.ArgumentList.Add($arg)}}}
    if($Modo -ne 'Inspeccionar'){
        $credential=Import-Clixml -LiteralPath $CredentialPath
        if($credential -isnot [Management.Automation.PSCredential] -or $credential.UserName -match '(^|\\)root$'){throw 'Credencial de aplicación requerida'}
        $frame=@{usuario=$credential.UserName;password=$credential.GetNetworkCredential().Password}|ConvertTo-Json -Compress
        $info.ArgumentList.Add('--secretos-stdin')
    }
    $process=[Diagnostics.Process]::new();$process.StartInfo=$info;[void]$process.Start()
    $out=$process.StandardOutput.ReadToEndAsync();$err=$process.StandardError.ReadToEndAsync()
    if($frame){$process.StandardInput.Write($frame)};$process.StandardInput.Close();$frame=$null;$credential=$null
    if(-not $process.WaitForExit($TimeoutSeconds*1000)){$process.Kill($true);[void]$process.WaitForExit(5000);$result=30;$message='AGENTE_TIMEOUT: proceso terminado; reintentar sin borrar paquetes'}else{$result=$process.ExitCode;$message=$out.GetAwaiter().GetResult().Trim();$errorText=$err.GetAwaiter().GetResult().Trim();if($errorText){$message+=' ; diagnóstico Java disponible sin registrar argumentos'}}
    $record=[ordered]@{fecha=[DateTimeOffset]::UtcNow.ToString('o');modo=$Modo;codigo=$result;resultado=$message.Substring(0,[Math]::Min(8192,$message.Length))}
    $log=Join-Path $LogDirectory ('agente-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmssfff')+'-'+[guid]::NewGuid().ToString('N')+'.json');[IO.File]::WriteAllText($log,($record|ConvertTo-Json -Compress),[Text.UTF8Encoding]::new($false));$record|ConvertTo-Json -Compress
    $logRoot=Get-Item -LiteralPath $LogDirectory;if($logRoot.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Logs sin reparse requerido'}
    $files=@(Get-ChildItem -LiteralPath $LogDirectory -File | Where-Object {$_.Name -match '^agente-\d{8}-\d{9}-[a-f0-9]{32}\.json$' -and -not ($_.Attributes -band [IO.FileAttributes]::ReparsePoint)} | Sort-Object LastWriteTimeUtc)
    $count=$files.Count;$total=[long](($files|Measure-Object Length -Sum).Sum)
    foreach($f in $files){if($f.LastWriteTimeUtc -lt [DateTime]::UtcNow.AddDays(-1) -and ($f.LastWriteTimeUtc -lt [DateTime]::UtcNow.AddDays(-$LogRetentionDays) -or $count -gt $MaxLogFiles -or $total -gt $MaxLogBytes)){Remove-Item -LiteralPath $f.FullName;$count--;$total-=$f.Length}}
} catch {$result=20;Write-Output '{"codigo":20,"resultado":"AGENTE_FALLO_SEGURO: revisar rutas, ACL, identidad DPAPI y configuración; no se muestran secretos"}'} finally {$frame=$null;$credential=$null;if($process){try{if(-not $process.HasExited){$process.Kill($true);[void]$process.WaitForExit(5000)}}finally{$process.Dispose()}}}
exit $result