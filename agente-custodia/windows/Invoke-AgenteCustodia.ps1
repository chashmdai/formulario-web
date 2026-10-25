[CmdletBinding()]
param([Parameter(Mandatory)][string]$Jar,[Parameter(Mandatory)][string]$Config,[ValidateSet('Inspeccionar','VerificarTLS','Ensayo','Enviar')][string]$Modo='Inspeccionar',[string]$Id,[string]$Cola='COMPLETO',[string]$CredentialPath='C:\agente\config\mysql-lab-app.credential.xml',[string]$Java='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe',[string]$LogDirectory='C:\agente\logs',[ValidateRange(5,600)][int]$TimeoutSeconds=120)
$ErrorActionPreference='Stop'
$process=$null; $credential=$null; $frame=$null; $result=20
try {
    if(-not [IO.Path]::IsPathFullyQualified($Java) -or -not (Test-Path -LiteralPath $Java -PathType Leaf)){throw 'Java absoluto requerido'}
    foreach($path in @($Jar,$Config)){if(-not (Test-Path -LiteralPath $path -PathType Leaf)){throw 'Archivo requerido ausente'}}
    if(-not (Test-Path -LiteralPath $LogDirectory -PathType Container)){throw 'Directorio de logs preparado requerido'}
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
} catch {Write-Output '{"codigo":20,"resultado":"AGENTE_FALLO_SEGURO: revisar rutas, ACL, identidad DPAPI y configuración; no se muestran secretos"}'} finally {$frame=$null;$credential=$null;if($process){$process.Dispose()}}
exit $result