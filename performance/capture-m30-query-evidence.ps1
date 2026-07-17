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
    [string]$TemplatePath,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$OutputPath,

    [string]$DockerContainer = 'market-postgres',
    [string]$DatabaseUser = 'market',
    [string]$DatabaseName = 'market'
)

$ErrorActionPreference = 'Stop'
$cacheMode = $Mode.ToUpperInvariant()
$modeName = $cacheMode.ToLowerInvariant()
$resolvedBaseUrl = $BaseUrl.TrimEnd('/')
$headers = @{ Authorization = "Bearer $AdminToken" }

$serverResponse = Invoke-WebRequest "$resolvedBaseUrl/actuator/info" -Headers $headers
$serverJson = [System.Text.Encoding]::UTF8.GetString([byte[]]$serverResponse.Content)
$server = $serverJson | ConvertFrom-Json -DateKind String
$health = Invoke-RestMethod "$resolvedBaseUrl/actuator/health" -Headers $headers
if ($server.m30Experiment.cacheMode -ne $cacheMode) {
    throw "running server cache mode is $($server.m30Experiment.cacheMode), expected $cacheMode"
}
if ($health.status -ne 'UP') {
    throw "running server health is $($health.status), expected UP"
}

Invoke-RestMethod "$resolvedBaseUrl/api/catalog/products?size=20" -Headers $headers | Out-Null
Invoke-RestMethod "$resolvedBaseUrl/api/stores/1/catalog/products?size=20" -Headers $headers | Out-Null
Invoke-RestMethod "$resolvedBaseUrl/api/discovery/events" -Headers $headers | Out-Null
Invoke-RestMethod "$resolvedBaseUrl/api/discovery/popular-products" -Headers $headers | Out-Null

$templateDocument = Get-Content $TemplatePath -Raw | ConvertFrom-Json
$templateMode = $templateDocument.$modeName
$templates = if ($templateMode -is [array]) { $templateMode } else { $templateMode.evidence }
if (@($templates).Count -ne 4) {
    throw "$cacheMode template must contain four query shapes"
}

$fixedNow = [string]$server.m30Experiment.fixedNow
$since = [DateTimeOffset]::Parse($fixedNow).AddDays(-7).ToString("yyyy-MM-ddTHH:mm:ss'Z'")
$fixedLiteral = "'$fixedNow'::timestamptz"
$sinceLiteral = "'$since'::timestamptz"
$timestampLiteralPattern = "(?i:(?:TIMESTAMPTZ\s*)?'\d{4}-\d{2}-\d{2}T[^']+'(?:::(?:timestamptz|timestamp(?: with time zone)?))?)"

function Resolve-ExactSql {
    param($Template)

    $sql = ([string]$Template.exactSql).Replace('CURRENT_TIMESTAMP', $fixedLiteral)
    $sql = [regex]::Replace($sql, $timestampLiteralPattern, $fixedLiteral)
    if ($Template.queryShape -eq 'POPULARITY') {
        $sql = [regex]::Replace($sql, "(?i)(created_at\s*>=\s*)$timestampLiteralPattern", "`$1$sinceLiteral")
        $sql = [regex]::Replace($sql, "(?i)(viewed_at\s*>=\s*)$timestampLiteralPattern", "`$1$sinceLiteral")
    }
    return $sql
}

function Get-BindSummary {
    param([string]$QueryShape)

    switch ($QueryShape) {
        'GLOBAL_CATALOG' { "now=$fixedNow, limitPlusOne=21" }
        'FIXED_STORE_CATALOG' { "now=$fixedNow, storeId=1, limitPlusOne=21" }
        'ACTIVE_EVENTS' { "now=$fixedNow" }
        'POPULARITY' { "since=$since, now=$fixedNow" }
    }
}

$evidence = foreach ($template in $templates) {
    $exactSql = Resolve-ExactSql $template
    $explainSql = "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) $exactSql"
    $planLines = & docker exec $DockerContainer psql -X -q -U $DatabaseUser -d $DatabaseName -t -A -c $explainSql
    if ($LASTEXITCODE -ne 0) {
        throw "EXPLAIN failed for $($template.queryShape)"
    }
    $fullPlan = $planLines -join "`n"
    $planRoot = @(ConvertFrom-Json $fullPlan)[0]
    $plan = $planRoot.Plan
    [ordered]@{
        cacheMode = $cacheMode
        queryShape = [string]$template.queryShape
        bindSummary = Get-BindSummary ([string]$template.queryShape)
        planSummary = "$($plan.'Node Type'); Planning Time: $($planRoot.'Planning Time') ms; Execution Time: $($planRoot.'Execution Time') ms"
        executionMillis = [double]$planRoot.'Execution Time'
        actualRows = [long]$plan.'Actual Rows'
        sharedHitBlocks = [long]$plan.'Shared Hit Blocks'
        sharedReadBlocks = [long]$plan.'Shared Read Blocks'
        exactSql = $exactSql
        fullPlan = $fullPlan
    }
}

$capture = [ordered]@{
    capturedAt = [DateTimeOffset]::UtcNow.ToString('o')
    provenance = [ordered]@{
        cacheMode = $cacheMode
        activeProfiles = @($server.m30Experiment.activeProfiles)
        fixedNow = $fixedNow
        serverProcessId = [long]$server.m30Experiment.serverProcessId
        healthStatus = [string]$health.status
        captureSequence = $CaptureSequence
        source = 'server-reported actuator info plus four HTTP query shapes followed by PostgreSQL EXPLAIN'
    }
    evidence = @($evidence)
}

$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($resolvedOutput)) | Out-Null
[System.IO.File]::WriteAllText($resolvedOutput, ($capture | ConvertTo-Json -Depth 100), [System.Text.UTF8Encoding]::new($false))
Write-Output $resolvedOutput
