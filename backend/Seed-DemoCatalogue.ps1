param([string]$Gateway='http://localhost:8080',[string]$AccessToken=$env:RAILWAY_ADMIN_ACCESS_TOKEN)
$ErrorActionPreference='Stop'
if([string]::IsNullOrWhiteSpace($AccessToken)){throw 'Sign in as an administrator and supply AccessToken or RAILWAY_ADMIN_ACCESS_TOKEN.'}
$taskHeaders=@{Authorization="Bearer $AccessToken"}
function Invoke-DemoApi([string]$Method,[string]$Path,$Body=$null){
    $taskArgs=@{Method=$Method;Uri=($Gateway.TrimEnd('/')+$Path);Headers=$taskHeaders}
    if($null -ne $Body){$taskArgs.ContentType='application/json';$taskArgs.Body=ConvertTo-Json -InputObject $Body -Depth 8}
    Invoke-RestMethod @taskArgs
}
$taskStations=@()
foreach($taskSpec in @(@{stationCode='DSTA';stationName='Demo Origin'},@{stationCode='DSTB';stationName='Demo Junction'},@{stationCode='DSTC';stationName='Demo Destination'})){
    try{$taskStation=Invoke-DemoApi 'GET' ('/api/stations/code/'+$taskSpec.stationCode)}
    catch{if([int]$_.Exception.Response.StatusCode -ne 404){throw};$taskStation=Invoke-DemoApi 'POST' '/api/stations' $taskSpec}
    $taskStations+=$taskStation
}
foreach($taskTrain in @(@{number='DM001';name='Demo Small Capacity Express';seats=2},@{number='DM002';name='Demo Regular Express';seats=20})){
    $taskTrainBody=@{
        trainNumber=$taskTrain.number;trainName=$taskTrain.name;category='EXPRESS'
        sourceStationId=$taskStations[0].stationId;destinationStationId=$taskStations[2].stationId
        seatClasses=@('SL');operatingDays=@('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')
        classConfigurations=@(@{seatClass='SL';coachCount=1;seatsPerCoach=$taskTrain.seats;baseFare=20;ratePerKm=0.5;minimumFare=40})
    }
    try{$null=Invoke-DemoApi 'GET' ('/api/trains/'+$taskTrain.number)}
    catch{if([int]$_.Exception.Response.StatusCode -ne 404){throw};$null=Invoke-DemoApi 'POST' '/api/trains' $taskTrainBody}
    $taskRoute=@(
        @{stationCode='DSTA';stopSequence=1;arrivalTime=$null;departureTime='10:05:00';dayNumber=1;departureDayNumber=1;distanceKm=0},
        @{stationCode='DSTB';stopSequence=2;arrivalTime='11:00:00';departureTime='11:05:00';dayNumber=1;departureDayNumber=1;distanceKm=100},
        @{stationCode='DSTC';stopSequence=3;arrivalTime='12:00:00';departureTime=$null;dayNumber=1;departureDayNumber=1;distanceKm=200}
    )
    $null=Invoke-DemoApi 'POST' ('/api/schedules/'+$taskTrain.number) $taskRoute
    Write-Output ('Ready: '+$taskTrain.number+' '+$taskTrain.name)
}
Write-Output 'Demo routes are DSTA → DSTB → DSTC. Use a future date. DM001 has two seats for whole-booking WL demonstrations.'
