param([Parameter(Mandatory=$true)][ValidateSet('config-server','eureka-server','api-gateway','auth-service','user-service','train-service','schedule-service','reservation-service','pnr-service','payment-service','notification-service','chatbot-service')][string]$Service)
$ErrorActionPreference='Stop'
$taskEnvPath=Join-Path $PSScriptRoot '.env'
if(-not (Test-Path -LiteralPath $taskEnvPath)){throw 'Create .env using New-DevEnvironment.ps1 and fill in your local settings first.'}
foreach($taskLine in [IO.File]::ReadAllLines($taskEnvPath)){
    if($taskLine -match '^([A-Z][A-Z0-9_]*)=(.*)$') {[Environment]::SetEnvironmentVariable($Matches[1],$Matches[2].Trim(),'Process')}
}
if($env:RAZORPAY_TEST_KEY_ID -and -not $env:RAZORPAY_TEST_KEY_ID.StartsWith('rzp_test_')) {throw 'Only Razorpay TEST keys are allowed.'}
foreach($taskName in @('CONFIG_SERVER_PASSWORD','EUREKA_PASSWORD','JWT_SECRET','RAILWAY_INTERNAL_TOKEN')) {
    $taskValue=[Environment]::GetEnvironmentVariable($taskName,'Process')
    if([string]::IsNullOrWhiteSpace($taskValue)){throw "Missing local setting: $taskName"}
}
if($env:RAILWAY_INTERNAL_TOKEN.Length -lt 32){throw 'RAILWAY_INTERNAL_TOKEN must have at least 32 characters.'}
if([Text.Encoding]::UTF8.GetByteCount($env:JWT_SECRET) -lt 32){throw 'JWT_SECRET must contain at least 32 UTF-8 bytes.'}
Push-Location (Join-Path $PSScriptRoot $Service)
try { & mvn.cmd spring-boot:run; if($LASTEXITCODE -ne 0){throw "$Service exited with code $LASTEXITCODE"} }
finally {Pop-Location}
