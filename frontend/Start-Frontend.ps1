param(
    [ValidateSet('Start', 'Build', 'Test', 'BrowserTests')]
    [string]$Action = 'Start',
    [switch]$Install
)

$ErrorActionPreference = 'Stop'
$nodeCommand = Get-Command node -CommandType Application -ErrorAction Stop | Select-Object -First 1
$nodeExecutable = $nodeCommand.Source
$npmCli = Join-Path (Split-Path $nodeExecutable -Parent) 'node_modules/npm/bin/npm-cli.js'

Push-Location -LiteralPath $PSScriptRoot
try {
    if ($Install -or -not (Test-Path -LiteralPath 'node_modules/@angular/cli/bin/ng.js')) {
        if (-not (Test-Path -LiteralPath $npmCli)) {
            throw "Bundled npm was not found beside Node. Repair your Node.js installation, or run npm ci manually."
        }
        & $nodeExecutable $npmCli ci
        if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed.' }
    }

    switch ($Action) {
        'Start' {
            Write-Host 'Railwize: http://localhost:4200 | API gateway: http://localhost:8080'
            & $nodeExecutable 'node_modules/@angular/cli/bin/ng.js' serve --host localhost --port 4200
        }
        'Build' { & $nodeExecutable 'node_modules/@angular/cli/bin/ng.js' build }
        'Test' { & $nodeExecutable 'scripts/test-jasmine.cjs' }
        'BrowserTests' { & $nodeExecutable 'node_modules/@playwright/test/cli.js' test }
    }
    if ($LASTEXITCODE -ne 0) { throw "$Action exited with code $LASTEXITCODE." }
} finally {
    Pop-Location
}
