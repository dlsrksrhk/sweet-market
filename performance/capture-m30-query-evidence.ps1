[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$BaseUrl,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$AdminToken,

    [Parameter(Mandatory = $true)]
    [ValidateSet('OFF', 'ON')]
    [string]$Mode,

    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 2)]
    [int]$CaptureSequence,

    [Parameter(Mandatory = $true)]
    [ValidateScript({ Test-Path $_ -PathType Leaf })]
    [string]$TraceLogPath,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$OutputPath,

    [string]$DockerContainer = 'market-postgres',
    [string]$DatabaseUser = 'market',
    [string]$DatabaseName = 'market'
)

$ErrorActionPreference = 'Stop'
$cacheMode = $Mode.ToUpperInvariant()
$resolvedBaseUrl = $BaseUrl.TrimEnd('/')
$headers = @{ Authorization = "Bearer $AdminToken" }
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$parserPath = Join-Path $repositoryRoot 'performance\parse-m30-jdbc-trace.mjs'
$resolvedTraceLogPath = (Resolve-Path $TraceLogPath).Path

function Get-SanitizedServerInfo {
    $response = Invoke-WebRequest "$resolvedBaseUrl/actuator/info" -Headers $headers
    $content = if ($response.Content -is [byte[]]) {
        [System.Text.Encoding]::UTF8.GetString($response.Content)
    } else {
        [string]$response.Content
    }
    $experiment = ($content | ConvertFrom-Json -DateKind String).m30Experiment
    if ($null -eq $experiment) {
        throw 'authenticated server info is missing m30Experiment'
    }
    return [ordered]@{
        serverProcessId = [long]$experiment.serverProcessId
        activeProfiles = @($experiment.activeProfiles)
        fixedClock = [string]$experiment.fixedNow
        cacheMode = [string]$experiment.cacheMode
    }
}

if ($cacheMode -eq 'ON') {
    Start-Sleep -Seconds 31
}

$serverInfo = Get-SanitizedServerInfo
$health = Invoke-RestMethod "$resolvedBaseUrl/actuator/health" -Headers $headers
if ($serverInfo.cacheMode -ne $cacheMode) {
    throw "running server cache mode is $($serverInfo.cacheMode), expected $cacheMode"
}
if ($health.status -ne 'UP') {
    throw "running server health is $($health.status), expected UP"
}

$loggerUri = "$resolvedBaseUrl/actuator/loggers/org.springframework.jdbc.core"
$trace = $null
try {
    $traceBody = @{ configuredLevel = 'TRACE' } | ConvertTo-Json
    Invoke-RestMethod $loggerUri -Method Post -Headers $headers -ContentType 'application/json' -Body $traceBody | Out-Null
    Start-Sleep -Milliseconds 500

    $traceStartOffset = (Get-Item -LiteralPath $resolvedTraceLogPath).Length
    Invoke-RestMethod "$resolvedBaseUrl/api/catalog/products?size=20" -Headers $headers | Out-Null
    Invoke-RestMethod "$resolvedBaseUrl/api/stores/1/catalog/products?size=20" -Headers $headers | Out-Null
    Invoke-RestMethod "$resolvedBaseUrl/api/discovery/events" -Headers $headers | Out-Null
    Invoke-RestMethod "$resolvedBaseUrl/api/discovery/popular-products" -Headers $headers | Out-Null
    Start-Sleep -Seconds 1
    $traceEndOffset = (Get-Item -LiteralPath $resolvedTraceLogPath).Length

    $parserLines = & node $parserPath `
        --trace-log $resolvedTraceLogPath `
        --start-offset $traceStartOffset `
        --end-offset $traceEndOffset
    if ($LASTEXITCODE -ne 0) {
        throw "live JDBC trace parser exited with code $LASTEXITCODE"
    }
    $trace = (($parserLines -join [Environment]::NewLine) | ConvertFrom-Json -DateKind String)
} finally {
    $resetBody = @{ configuredLevel = $null } | ConvertTo-Json
    Invoke-RestMethod $loggerUri -Method Post -Headers $headers -ContentType 'application/json' -Body $resetBody | Out-Null
}

if ($null -eq $trace -or @($trace.statements).Count -ne 4) {
    throw 'live JDBC trace did not produce exactly four statements'
}

$evidence = foreach ($statement in $trace.statements) {
    $exactSql = [string]$statement.exactSql
    $explainSql = "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) $exactSql"
    $planLines = & docker exec $DockerContainer psql -X -q -U $DatabaseUser -d $DatabaseName -t -A -c $explainSql
    if ($LASTEXITCODE -ne 0) {
        throw "EXPLAIN failed for $($statement.queryShape)"
    }
    $fullPlan = $planLines -join "`n"
    $planRoot = @(ConvertFrom-Json $fullPlan)[0]
    $plan = $planRoot.Plan
    [ordered]@{
        cacheMode = $cacheMode
        queryShape = [string]$statement.queryShape
        requestPath = [string]$statement.requestPath
        traceThread = [string]$statement.threadName
        preparedSql = [string]$statement.preparedSql
        capturedBinds = @($statement.capturedBinds)
        bindSummary = [string]$statement.bindSummary
        exactSql = $exactSql
        planSummary = "$($plan.'Node Type'); Planning Time: $($planRoot.'Planning Time') ms; Execution Time: $($planRoot.'Execution Time') ms"
        executionMillis = [double]$planRoot.'Execution Time'
        actualRows = [long]$plan.'Actual Rows'
        sharedHitBlocks = [long]$plan.'Shared Hit Blocks'
        sharedReadBlocks = [long]$plan.'Shared Read Blocks'
        fullPlan = $fullPlan
    }
}

$capture = [ordered]@{
    capturedAt = [DateTimeOffset]::UtcNow.ToString('o')
    provenance = [ordered]@{
        serverInfo = $serverInfo
        healthStatus = [string]$health.status
        captureSequence = $CaptureSequence
        source = 'authenticated server info plus task-local live Spring JDBC TRACE SQL/binds followed by PostgreSQL EXPLAIN'
        trace = [ordered]@{
            sourceLog = [System.IO.Path]::GetFileName($resolvedTraceLogPath)
            logger = 'org.springframework.jdbc.core=TRACE'
            startOffset = [long]$traceStartOffset
            endOffset = [long]$traceEndOffset
            traceSegmentSha256 = [string]$trace.traceSegmentSha256
            sanitizedTraceSha256 = [string]$trace.sanitizedTraceSha256
            statementCount = @($trace.statements).Count
        }
    }
    evidence = @($evidence)
}

$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($resolvedOutput)) | Out-Null
[System.IO.File]::WriteAllText(
    $resolvedOutput,
    ($capture | ConvertTo-Json -Depth 100),
    [System.Text.UTF8Encoding]::new($false)
)
Write-Output $resolvedOutput
