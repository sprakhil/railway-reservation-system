# Run from any working directory. Keep this terminal open; Ctrl+C stops this run.
[CmdletBinding()]
param(
    [ValidateRange(30,3600)][int]$StartupTimeoutSeconds = 300,
    [switch]$CheckOnly
)
$ErrorActionPreference = 'Stop'
$taskProcesses = [Collections.Generic.List[object]]::new()
$taskOriginalEnvironment = @{}

function Test-LocalPort([int]$Port) {
    $client = [Net.Sockets.TcpClient]::new()
    try {
        $pending = $client.BeginConnect('localhost', $Port, $null, $null)
        if (-not $pending.AsyncWaitHandle.WaitOne(500)) { return $false }
        $client.EndConnect($pending)
        return $true
    } catch { return $false } finally { $client.Dispose() }
}

function Assert-Running {
    foreach ($entry in $taskProcesses) {
        $entry.Process.Refresh()
        if ($entry.Process.HasExited) {
            throw "$($entry.Name) stopped (exit $($entry.Process.ExitCode)). Inspect $($entry.LogPrefix).out.log and .err.log."
        }
    }
}

function Wait-Ready([string]$Name, [scriptblock]$Probe) {
    $deadline = [DateTime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    $nextNotice = [DateTime]::UtcNow.AddSeconds(15)
    while ([DateTime]::UtcNow -lt $deadline) {
        Assert-Running
        $ready = $false
        try { $ready = & $Probe } catch { } # Transient connection failures are expected during startup.
        if ($ready) { Write-Host "Ready: $Name" -ForegroundColor Green; return }
        if ([DateTime]::UtcNow -ge $nextNotice) {
            Write-Host "Waiting for $Name ... (logs: $taskLogDirectory)"
            $nextNotice = [DateTime]::UtcNow.AddSeconds(15)
        }
        Start-Sleep -Seconds 2
    }
    throw "Timed out waiting for $Name after $StartupTimeoutSeconds seconds. Inspect logs in $taskLogDirectory. Fix the cause and rerun; use -StartupTimeoutSeconds 600 for slow first-time Maven downloads."
}

function Start-BackendService([string]$Name, [int]$Port, [string]$DocsPath) {
    # Recheck immediately before launch: never reuse or terminate an existing instance.
    if (Test-LocalPort $Port) { throw "Port $Port is already occupied; cannot launch $Name." }
    Write-Host "Starting $Name on port $Port ..." -ForegroundColor Cyan
    $runner = (Join-Path $PSScriptRoot 'Run-Service.ps1').Replace("'", "''")
    $command = "try { & '$runner' -Service '$Name'; exit 0 } catch { [Console]::Error.WriteLine(`$_.Exception.Message); exit 1 }"
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($command))
    $logPrefix = Join-Path $taskLogDirectory $Name
    $process = Start-Process -FilePath $taskPowerShell -ArgumentList @('-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-EncodedCommand',$encoded) -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -RedirectStandardOutput "$logPrefix.out.log" -RedirectStandardError "$logPrefix.err.log" -PassThru
    $taskProcesses.Add([pscustomobject]@{Name=$Name; Process=$process; LogPrefix=$logPrefix})
    if ($Name -eq 'config-server') {
        Wait-Ready $Name {
            $config = Invoke-RestMethod -Uri 'http://localhost:8888/eureka-server/default' -Headers $taskConfigHeaders -TimeoutSec 5
            return $config.name -eq 'eureka-server' -and @($config.propertySources).Count -gt 0
        }
    } elseif ($Name -eq 'eureka-server') {
        Wait-Ready $Name {
            $registry = Invoke-RestMethod -Uri 'http://localhost:8761/eureka/apps' -Headers $taskEurekaHeaders -TimeoutSec 5
            return $null -ne $registry.applications
        }
    } else {
        Wait-Ready $Name {
            $document = Invoke-RestMethod -Uri "http://localhost:$Port$DocsPath" -TimeoutSec 5
            if ($Name -eq 'api-gateway') {
                if (-not $document.urls) { return $false }
            } elseif (-not $document.openapi) { return $false }
            $registration = Invoke-RestMethod -Uri "http://localhost:8761/eureka/apps/$($Name.ToUpperInvariant())" -Headers $taskEurekaHeaders -TimeoutSec 5
            return @($registration.application.instance | Where-Object { $_.status -eq 'UP' -and [int]$_.port.'$' -eq $Port }).Count -gt 0
        }
    }
}

try {
    if ($env:OS -ne 'Windows_NT') { throw 'This launcher is for Windows PowerShell / PowerShell on Windows.' }
    $taskPowerShell = (Get-Process -Id $PID).Path
    foreach ($commandName in @('java','mvn.cmd','taskkill.exe')) {
        if (-not (Get-Command $commandName -ErrorAction SilentlyContinue)) { throw "Install/configure $commandName on PATH before starting the backend." }
    }
    $taskEnvPath = Join-Path $PSScriptRoot '.env'
    if (-not (Test-Path -LiteralPath $taskEnvPath)) { throw 'Create backend/.env from .env.example and fill in your local credentials first.' }
    foreach ($line in [IO.File]::ReadAllLines($taskEnvPath)) {
        if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
            $name = $Matches[1]
            if (-not $taskOriginalEnvironment.ContainsKey($name)) { $taskOriginalEnvironment[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
            [Environment]::SetEnvironmentVariable($name,$Matches[2].Trim(),'Process')
        }
    }
    foreach ($name in @('CONFIG_SERVER_PASSWORD','EUREKA_PASSWORD','JWT_SECRET','RAILWAY_INTERNAL_TOKEN')) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name,'Process'))) { throw "Missing local setting: $name" }
    }
    if ($env:RAILWAY_INTERNAL_TOKEN.Length -lt 32 -or [Text.Encoding]::UTF8.GetByteCount($env:JWT_SECRET) -lt 32) { throw 'JWT_SECRET and RAILWAY_INTERNAL_TOKEN must meet the minimum lengths required by Run-Service.ps1 (32 bytes/characters).' }
    if ($env:RAZORPAY_TEST_KEY_ID -and -not $env:RAZORPAY_TEST_KEY_ID.StartsWith('rzp_test_')) { throw 'Only Razorpay TEST keys are allowed.' }
    if ($env:CONFIG_SERVER_URL -and $env:CONFIG_SERVER_URL.TrimEnd('/') -ne 'http://localhost:8888') { throw 'This complete local launcher expects CONFIG_SERVER_URL=http://localhost:8888.' }
    if ($env:EUREKA_URL) { throw 'Remove the EUREKA_URL override to use this launcher with the local Eureka server and its .env credentials.' }
    $taskChatPort = 8089
    if ($env:CHATBOT_PORT -and (-not [int]::TryParse($env:CHATBOT_PORT,[ref]$taskChatPort) -or $taskChatPort -lt 1 -or $taskChatPort -gt 65535)) { throw 'CHATBOT_PORT must be a valid port number.' }
    $taskPlan = @(
        @{Name='config-server'; Port=8888; DocsPath=''},
        @{Name='eureka-server'; Port=8761; DocsPath=''},
        @{Name='auth-service'; Port=8081; DocsPath='/api/auth/v3/api-docs'},
        @{Name='user-service'; Port=8082; DocsPath='/api/users/v3/api-docs'},
        @{Name='train-service'; Port=8083; DocsPath='/api/trains/v3/api-docs'},
        @{Name='schedule-service'; Port=8084; DocsPath='/api/schedules/v3/api-docs'},
        @{Name='pnr-service'; Port=8086; DocsPath='/api/pnr/v3/api-docs'},
        @{Name='reservation-service'; Port=8085; DocsPath='/api/reservations/v3/api-docs'},
        @{Name='payment-service'; Port=8087; DocsPath='/api/payments/v3/api-docs'},
        @{Name='notification-service'; Port=8088; DocsPath='/api/notifications/v3/api-docs'},
        @{Name='chatbot-service'; Port=$taskChatPort; DocsPath='/api/chat/v3/api-docs'},
        @{Name='api-gateway'; Port=8080; DocsPath='/v3/api-docs/swagger-config'}
    )
    if (@($taskPlan.Port | Select-Object -Unique).Count -ne $taskPlan.Count) { throw 'CHATBOT_PORT conflicts with another backend service.' }
    foreach ($service in $taskPlan) {
        if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot "$($service.Name)/pom.xml"))) { throw "Missing Maven project: $($service.Name)" }
        if (Test-LocalPort $service.Port) { throw "Port $($service.Port) is occupied. Stop the existing $($service.Name) instance (or the conflicting application), then rerun. Nothing has been started." }
    }
    $configUser = if ($env:CONFIG_SERVER_USERNAME) { $env:CONFIG_SERVER_USERNAME } else { 'config_admin' }
    $eurekaUser = if ($env:EUREKA_USERNAME) { $env:EUREKA_USERNAME } else { 'eureka_admin' }
    $taskConfigHeaders = @{Authorization='Basic '+[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${configUser}:$env:CONFIG_SERVER_PASSWORD"))}
    $taskEurekaHeaders = @{Accept='application/json'; Authorization='Basic '+[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${eurekaUser}:$env:EUREKA_PASSWORD"))}
    Write-Host 'Preflight passed. MySQL, the project databases and RabbitMQ must already be available.'
    if ($CheckOnly) { Write-Host 'CheckOnly: no services started. Database/provider credentials have not been tested.'; return }
    $taskLogDirectory = Join-Path $PSScriptRoot ('logs/backend-startup/'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+$PID)
    New-Item -ItemType Directory -Path $taskLogDirectory -Force | Out-Null
    foreach ($service in $taskPlan) { Start-BackendService @service }
    # The gateway has its own discovery cache. Verify a routed public endpoint too.
    Wait-Ready 'gateway routing to auth-service' {
        $document = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/v3/api-docs' -TimeoutSec 5
        return [bool]$document.openapi
    }
    Write-Host "Backend ready. Swagger: http://localhost:8080/swagger-ui/index.html`nLogs: $taskLogDirectory`nKeep this terminal open. Press Ctrl+C to stop these services." -ForegroundColor Green
    while ($true) { Assert-Running; Start-Sleep -Seconds 2 }
} finally {
    # Reverse startup order keeps infrastructure alive until its clients have stopped.
    # Only process trees created by this invocation are targeted; never kill by port/name.
    for ($index=$taskProcesses.Count-1; $index -ge 0; $index--) {
        $entry=$taskProcesses[$index]
        try {
            $entry.Process.Refresh()
            if (-not $entry.Process.HasExited) {
                Write-Host "Stopping $($entry.Name) ..."
                & taskkill.exe /PID $entry.Process.Id /T /F 2>$null | Out-Null
            }
        } catch { Write-Warning "Could not stop $($entry.Name); check its process tree manually." }
    }
    foreach ($name in $taskOriginalEnvironment.Keys) {
        if ($null -eq $taskOriginalEnvironment[$name]) { Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue }
        else { [Environment]::SetEnvironmentVariable($name,$taskOriginalEnvironment[$name],'Process') }
    }
}
