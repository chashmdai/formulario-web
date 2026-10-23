#Requires -Version 7.4
[CmdletBinding()]
param([ValidateSet('H2','MySQL')][string]$Base='H2')
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$saved=@{};Get-ChildItem Env:ROBUST_DB_*|ForEach-Object{$saved[$_.Name]=$_.Value}
$report=Join-Path $repo '.local/hijas-evidencias';New-Item -ItemType Directory -Path $report -Force|Out-Null
try{
 Get-ChildItem Env:ROBUST_DB_*|Remove-Item
 if($Base -eq 'MySQL'){
  & (Join-Path $PSScriptRoot 'Local-MySQL.ps1') iniciar
  $secret=Import-Clixml -LiteralPath (Join-Path $repo '.local/robustez/mysql/credentials.clixml')
  $env:ROBUST_DB_URL='jdbc:mysql://127.0.0.1:13306/robust_inst_test?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=3000&socketTimeout=5000'
  $env:ROBUST_DB_USER='institutionApp';$env:ROBUST_DB_PASSWORD=[Net.NetworkCredential]::new('',$secret.institutionApp).Password
  $env:ROBUST_DB_MIGRATION_USER='institutionMigration';$env:ROBUST_DB_MIGRATION_PASSWORD=[Net.NetworkCredential]::new('',$secret.institutionMigration).Password
 }
 & mvn -B -ntp -f (Join-Path $repo 'siger-institucional/backend/pom.xml') test
 if($LASTEXITCODE -ne 0){throw ('Falló la batería institucional '+$Base)}
 $results=@(Get-ChildItem (Join-Path $repo 'siger-institucional/backend/target/surefire-reports/TEST-*.xml')|ForEach-Object{
  $xml=[xml](Get-Content -LiteralPath $_.FullName -Raw)
  [pscustomobject]@{clase=$xml.testsuite.name;pruebas=[int]$xml.testsuite.tests;fallos=[int]$xml.testsuite.failures;errores=[int]$xml.testsuite.errors;omitidas=[int]$xml.testsuite.skipped;escenarios=@($xml.testsuite.testcase|ForEach-Object{$_.name})}
 })
 $results|ConvertTo-Json -Depth 6|Set-Content (Join-Path $report ('junit-'+$Base.ToLowerInvariant()+'.json'))
}finally{Get-ChildItem Env:ROBUST_DB_*|Remove-Item;foreach($entry in $saved.GetEnumerator()){[Environment]::SetEnvironmentVariable($entry.Key,$entry.Value,'Process')};$secret=$null}
