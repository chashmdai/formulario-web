$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$statePath = Join-Path $workspace '../.local/https/processes.json'
if (!(Test-Path -LiteralPath $statePath)) { Write-Output 'No hay procesos registrados de este perfil.'; return }
$state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
foreach ($entry in @($state.Backend, $state.Proxy)) {
    if ($null -eq $entry) { continue }
    $process = Get-Process -Id $entry.Id -ErrorAction SilentlyContinue
    if ($process) {
        $expectedStart = ([DateTimeOffset]$entry.StartedUtc).UtcDateTime
        if ($process.StartTime.ToUniversalTime().Ticks -ne $expectedStart.Ticks) {
            Write-Output ('Descartando registro antiguo del PID ' + $entry.Id + '; no corresponde al proceso guardado y no se detendrá.')
            continue
        }
        $metadata = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $entry.Id)
        if (!$metadata.CommandLine -or !$metadata.CommandLine.Contains([string]$entry.ExpectedArgument)) {
            Write-Output ('Descartando registro antiguo del PID ' + $entry.Id + '; no se detendrá un proceso diferente.')
            continue
        }
        Stop-Process -Id $entry.Id
        $process.WaitForExit(10000) | Out-Null
    }
}
Remove-Item -LiteralPath $statePath
Write-Output 'Detenidos únicamente el backend y proxy registrados para SIGER local Sandbox.'
