[CmdletBinding()]
param([switch]$Remove)
$ErrorActionPreference = 'Stop'
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
if (!([Security.Principal.WindowsPrincipal]::new($identity)).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Solo este script requiere administrador: agregar o retirar el bloque SIGER de hosts y vaciar caché DNS.'
}
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$localDirectory = Join-Path $workspace '../.local/https'
$hostsPath = Join-Path $env:windir 'System32/drivers/etc/hosts'
$domain = 'claveunica.formularioadulto.defensorianinez.gob.cl'
$marker = '# BEGIN SIGER LOCAL SANDBOX'
$blockText = "`r`n$marker`r`n127.0.0.1 $domain`r`n# END SIGER LOCAL SANDBOX`r`n"
$blockBytes = [Text.Encoding]::UTF8.GetBytes($blockText)
New-Item -ItemType Directory -Force $localDirectory | Out-Null
$stream = [IO.File]::Open($hostsPath, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
try {
    $bytes = [byte[]]::new($stream.Length)
    $read = $stream.Read($bytes, 0, $bytes.Length)
    if ($read -ne $bytes.Length) { throw 'No se pudo leer hosts íntegramente.' }
    if ($bytes.Length -ge 2 -and (($bytes[0] -eq 255 -and $bytes[1] -eq 254) -or ($bytes[0] -eq 254 -and $bytes[1] -eq 255))) {
        throw 'hosts usa UTF-16; no se cambiará su codificación automáticamente.'
    }
    $text = [Text.Encoding]::UTF8.GetString($bytes)
    if ($Remove) {
        # Remove exactly our byte sequence, preserving unrelated entries and later changes.
        $offset = -1
        for ($i = 0; $i -le $bytes.Length - $blockBytes.Length; $i++) {
            $match = $true
            for ($j = 0; $j -lt $blockBytes.Length; $j++) {
                if ($bytes[$i + $j] -ne $blockBytes[$j]) { $match = $false; break }
            }
            if ($match) { $offset = $i; break }
        }
        if ($offset -lt 0) {
            if ($text.Contains($marker)) { throw 'El bloque SIGER fue editado; revisar manualmente antes de retirar.' }
            Write-Output 'No hay bloque SIGER que retirar.'
        } else {
            $updated = [byte[]]::new($bytes.Length - $blockBytes.Length)
            [Array]::Copy($bytes, 0, $updated, 0, $offset)
            [Array]::Copy($bytes, $offset + $blockBytes.Length, $updated, $offset, $bytes.Length - $offset - $blockBytes.Length)
            $stream.Position = 0; $stream.Write($updated, 0, $updated.Length); $stream.SetLength($updated.Length)
            Write-Output 'Retirado únicamente el bloque SIGER de hosts.'
        }
    } else {
        $entries = @($text -split '\r?\n' | ForEach-Object {
            $parts = ($_ -split '#', 2)[0].Trim() -split '\s+'
            if ($parts.Length -gt 1 -and $parts[1..($parts.Length - 1)] -contains $domain) { $parts[0] }
        })
        if ($entries.Count -gt 0) {
            if ($entries | Where-Object { $_ -ne '127.0.0.1' }) { throw 'Existe una entrada diferente para el dominio; no se sobrescribirá.' }
            Write-Output 'El dominio ya tiene resolución local; no se modificó hosts.'
        } else {
            $backup = Join-Path $localDirectory ('hosts-before-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.bin')
            [IO.File]::WriteAllBytes($backup, $bytes)
            $stream.Position = $stream.Length; $stream.Write($blockBytes, 0, $blockBytes.Length)
            [pscustomobject]@{ Domain=$domain; Mapping='127.0.0.1'; Backup=$backup; AddedAt=(Get-Date).ToString('o') } |
                ConvertTo-Json | Set-Content -LiteralPath (Join-Path $localDirectory 'hosts-change.json')
            Write-Output "Agregada únicamente: 127.0.0.1 $domain"
        }
    }
    $stream.Flush()
} finally { $stream.Dispose() }
Clear-DnsClientCache
Write-Output 'Caché DNS local vaciada. DNS público e infraestructura institucional sin cambios.'
