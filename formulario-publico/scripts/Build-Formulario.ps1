$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '../front')
try {
    & npm ci
    if ($LASTEXITCODE -ne 0) { throw 'npm ci falló' }
    & npm run lint
    if ($LASTEXITCODE -ne 0) { throw 'Lint falló' }
    & npm test
    if ($LASTEXITCODE -ne 0) { throw 'Pruebas frontend fallaron' }
    & npm run build
    if ($LASTEXITCODE -ne 0) { throw 'Build frontend falló' }
} finally { Pop-Location }
Push-Location (Join-Path $PSScriptRoot '../backend')
try {
    & mvn -B -ntp clean verify
    if ($LASTEXITCODE -ne 0) { throw 'Verificación backend falló' }
} finally { Pop-Location }
