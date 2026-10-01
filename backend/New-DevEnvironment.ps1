$ErrorActionPreference='Stop'
$taskEnvPath=Join-Path $PSScriptRoot '.env'
if(Test-Path -LiteralPath $taskEnvPath){throw '.env already exists; it has not been overwritten.'}
$taskText=[IO.File]::ReadAllText((Join-Path $PSScriptRoot '.env.example'))
foreach($taskKey in @('CONFIG_SERVER_PASSWORD','EUREKA_PASSWORD','JWT_SECRET','RAILWAY_INTERNAL_TOKEN')) {
    $taskSecret=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()
    $taskText=[regex]::Replace($taskText,'(?m)^'+$taskKey+'=\r?$',$taskKey+'='+$taskSecret)
}
[IO.File]::WriteAllText($taskEnvPath,$taskText,[Text.UTF8Encoding]::new($false))
Write-Output 'Created .env with random local service secrets. Add your database, email, Google OAuth and Razorpay TEST settings. Keep this file private.'
