param([switch]$Integration)
$ErrorActionPreference='Stop'
$taskIntegrationSuites=@{
    'auth-service'='OtpSecurityIntegrationTest,SessionInvitationTest,SwaggerDocumentationIntegrationTest'
    'user-service'='MasterListApiIntegrationTest,SwaggerDocumentationIntegrationTest'
    'train-service'='StationApiIntegrationTest,CataloguePagingTest,SwaggerDocumentationIntegrationTest'
    'schedule-service'='ScheduleApiIntegrationTest,SwaggerDocumentationIntegrationTest'
    'reservation-service'='BookingHttpIntegrationTest,BookingLifecycleTest,SwaggerDocumentationIntegrationTest'
    'payment-service'='PaymentFlowIntegrationTest,SwaggerDocumentationIntegrationTest'
    'notification-service'='NotificationInboxTest,SwaggerDocumentationIntegrationTest'
    'pnr-service'='SwaggerDocumentationIntegrationTest'
    'api-gateway'='SwaggerGatewayIntegrationTest'
    'chatbot-service'='ChatHttpIntegrationTest,AccountChatTest'
}
$taskServices=@('auth-service','user-service','train-service','schedule-service','reservation-service','payment-service','api-gateway','pnr-service','notification-service','config-server','eureka-server','chatbot-service')
foreach($taskService in $taskServices){
    if($Integration -and -not $taskIntegrationSuites.ContainsKey($taskService)){continue}
    Write-Output "Testing $taskService"
    $taskArguments=@('test')
    if($Integration){$taskArguments+=('-Dtest='+$taskIntegrationSuites[$taskService])}
    Push-Location (Join-Path $PSScriptRoot $taskService)
    try { & mvn.cmd @taskArguments; if($LASTEXITCODE -ne 0){throw "Tests failed in $taskService"} }
    finally {Pop-Location}
}
Write-Output 'Selected backend test suites passed. Tests use isolated storage and mocked/stubbed external services; no .env or live provider credentials are loaded.'
