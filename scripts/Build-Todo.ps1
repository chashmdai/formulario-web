#Requires -Version 7.4
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$hash=(Get-FileHash -LiteralPath (Join-Path $repoRoot 'contratos/ejemplo-comunicacion-v1.json')).Hash
foreach ($app in @('formulario-publico','siger-institucional')) {
    if ((Get-FileHash -LiteralPath (Join-Path $repoRoot ($app+'/backend/src/test/resources/comunicacion-v1.json'))).Hash -ne $hash) {
        throw 'Los fixtures del contrato HTTP divergieron.'
    }
    foreach($file in @('registro-institucional-v3.json','institution-public-test-v3.json','comunicacion-identidad-v1.1.json','entrega-nna-v2.1.json','public-nna-test.json')){
        if((Get-FileHash -LiteralPath (Join-Path $repoRoot ('contratos/fixtures/'+$file))).Hash -ne
           (Get-FileHash -LiteralPath (Join-Path $repoRoot ($app+'/backend/src/test/resources/contratos/'+$file))).Hash){throw 'Los fixtures firmados del registro v3 divergieron.'}
    }
}
& (Join-Path $PSScriptRoot '../formulario-publico/scripts/Build-Formulario.ps1')
& (Join-Path $PSScriptRoot '../siger-institucional/scripts/Build-Siger.ps1')
Write-Host 'Las dos aplicaciones fueron verificadas y empaquetadas por separado.'
