[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$BaseUrl,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$AdminToken,

    [Parameter(Mandatory = $true)]
    [ValidateRange(1, [long]::MaxValue)]
    [long]$ProductId,

    [Parameter(Mandatory = $true)]
    [ValidateSet('OFF', 'ON')]
    [string]$Mode,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
$resolvedBaseUrl = $BaseUrl.TrimEnd('/')
$cacheMode = $Mode.ToUpperInvariant()
$modeName = $cacheMode.ToLowerInvariant()
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$scenarioPath = Join-Path $repositoryRoot 'performance\m30-catalog-reads.js'
$resolvedOutputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)
$summaryPath = Join-Path $resolvedOutputDirectory "k6-$modeName.json"
$metricsPath = Join-Path $resolvedOutputDirectory "metrics-$modeName.json"
$rawPath = Join-Path ([System.IO.Path]::GetTempPath()) "sweet-market-m30-$modeName-$([guid]::NewGuid().ToString('N')).json"
$headers = @{ Authorization = "Bearer $AdminToken" }
$endpoints = @('catalog', 'events', 'popularity', 'detail')
$measuredScenario = 'measured_catalog_reads'
$measuredSeconds = 300.0

New-Item -ItemType Directory -Force -Path $resolvedOutputDirectory | Out-Null

function Invoke-ActuatorMetric {
    param(
        [Parameter(Mandatory = $true)]
        [string]$MetricName,
        [string[]]$Tags = @(),
        [switch]$AllowMissing
    )

    $query = if ($Tags.Count -eq 0) {
        ''
    } else {
        '?' + (($Tags | ForEach-Object { 'tag=' + [uri]::EscapeDataString($_) }) -join '&')
    }
    try {
        return Invoke-RestMethod "$resolvedBaseUrl/actuator/metrics/$MetricName$query" -Headers $headers
    } catch {
        if ($AllowMissing -and $_.Exception.Response.StatusCode.value__ -eq 404) {
            return $null
        }
        throw
    }
}

function Get-MeasurementValue {
    param(
        $Metric,
        [Parameter(Mandatory = $true)]
        [string]$Statistic
    )

    if ($null -eq $Metric) {
        return 0.0
    }
    $measurement = $Metric.measurements | Where-Object { $_.statistic -eq $Statistic } | Select-Object -First 1
    if ($null -eq $measurement) {
        return 0.0
    }
    return [double]$measurement.value
}

function Get-CacheSnapshot {
    if ($cacheMode -ne 'ON') {
        return $null
    }
    return [ordered]@{
        hits = Invoke-ActuatorMetric 'cache.gets' @('cache:discovery.active-events', 'result:hit') -AllowMissing
        misses = Invoke-ActuatorMetric 'cache.gets' @('cache:discovery.active-events', 'result:miss') -AllowMissing
        evictions = Invoke-ActuatorMetric 'cache.evictions' @('cache:discovery.active-events') -AllowMissing
    }
}

function Get-ActuatorSnapshot {
    return [ordered]@{
        jdbcStatements = Invoke-ActuatorMetric 'discovery.jdbc.statements'
        readDuration = Invoke-ActuatorMetric 'discovery.read.duration' -AllowMissing
        readDurationByEndpoint = [ordered]@{
            catalog = Invoke-ActuatorMetric 'discovery.read.duration' @('endpoint:catalog') -AllowMissing
            events = Invoke-ActuatorMetric 'discovery.read.duration' @('endpoint:events') -AllowMissing
            popularity = Invoke-ActuatorMetric 'discovery.read.duration' @('endpoint:popularity') -AllowMissing
            detail = Invoke-ActuatorMetric 'discovery.read.duration' @('endpoint:detail') -AllowMissing
        }
        cache = Get-CacheSnapshot
    }
}

function Get-Percentile {
    param(
        [double[]]$Values,
        [double]$Percentile
    )

    if ($Values.Count -eq 0) {
        return 0.0
    }
    [Array]::Sort($Values)
    $position = ($Values.Count - 1) * $Percentile
    $lower = [Math]::Floor($position)
    $upper = [Math]::Ceiling($position)
    if ($lower -eq $upper) {
        return $Values[$lower]
    }
    return $Values[$lower] + (($Values[$upper] - $Values[$lower]) * ($position - $lower))
}

function Read-K6EndpointSamples {
    $durations = @{}
    $failed = @{}
    foreach ($endpoint in $endpoints) {
        $durations[$endpoint] = [System.Collections.Generic.List[double]]::new()
        $failed[$endpoint] = [System.Collections.Generic.List[double]]::new()
    }

    foreach ($line in [System.IO.File]::ReadLines($rawPath)) {
        if (-not ($line.Contains('"metric":"http_req_duration"') -or $line.Contains('"metric":"http_req_failed"'))) {
            continue
        }
        $sample = $line | ConvertFrom-Json
        if ($sample.data.tags.scenario -ne $measuredScenario) {
            continue
        }
        $route = [string]$sample.data.tags.route
        if (-not $durations.ContainsKey($route)) {
            continue
        }
        if ($sample.metric -eq 'http_req_duration') {
            $durations[$route].Add([double]$sample.data.value)
        } else {
            $failed[$route].Add([double]$sample.data.value)
        }
    }
    return [ordered]@{ durations = $durations; failed = $failed }
}

function Get-CacheDelta {
    param(
        $Before,
        $After,
        [string]$Name,
        [string]$Statistic
    )

    if ($cacheMode -ne 'ON') {
        return $null
    }
    return [long][Math]::Round(
        (Get-MeasurementValue $After.cache[$Name] $Statistic) -
        (Get-MeasurementValue $Before.cache[$Name] $Statistic)
    )
}

$before = Get-ActuatorSnapshot
$startedAt = [DateTimeOffset]::UtcNow
$k6ExitCode = -1
try {
    & k6 run --summary-export $summaryPath --out "json=$rawPath" `
        -e "BASE_URL=$resolvedBaseUrl" -e "PRODUCT_ID=$ProductId" $scenarioPath
    $k6ExitCode = $LASTEXITCODE
} finally {
    $completedAt = [DateTimeOffset]::UtcNow
    $after = Get-ActuatorSnapshot
}

if (-not (Test-Path $summaryPath)) {
    throw "k6 did not create $summaryPath (exit code $k6ExitCode)"
}
if (-not (Test-Path $rawPath)) {
    throw "k6 did not create its temporary raw sample output (exit code $k6ExitCode)"
}

$samples = Read-K6EndpointSamples
$jdbcStatementDelta = [long][Math]::Round(
    (Get-MeasurementValue $after.jdbcStatements 'COUNT') -
    (Get-MeasurementValue $before.jdbcStatements 'COUNT')
)
$cacheHits = Get-CacheDelta $before $after 'hits' 'COUNT'
$cacheMisses = Get-CacheDelta $before $after 'misses' 'COUNT'
$cacheEvictions = Get-CacheDelta $before $after 'evictions' 'COUNT'

$endpointMetrics = foreach ($endpoint in $endpoints) {
    [double[]]$endpointDurations = $samples.durations[$endpoint].ToArray()
    [double[]]$endpointFailures = $samples.failed[$endpoint].ToArray()
    $failureCount = ($endpointFailures | Measure-Object -Sum).Sum
    if ($null -eq $failureCount) {
        $failureCount = 0.0
    }
    $requestCount = $endpointDurations.Count
    [ordered]@{
        endpoint = $endpoint
        p50Millis = [Math]::Round((Get-Percentile $endpointDurations 0.50), 3)
        p95Millis = [Math]::Round((Get-Percentile $endpointDurations 0.95), 3)
        throughputPerSecond = [Math]::Round($requestCount / $measuredSeconds, 3)
        errorRate = if ($requestCount -eq 0) { 0.0 } else { [Math]::Round($failureCount / $requestCount, 7) }
        jdbcStatementCount = $jdbcStatementDelta
        cacheHitCount = if ($endpoint -eq 'events') { $cacheHits } else { $null }
        cacheMissCount = if ($endpoint -eq 'events') { $cacheMisses } else { $null }
        cacheEvictionCount = if ($endpoint -eq 'events') { $cacheEvictions } else { $null }
        measuredRequestCount = $requestCount
    }
}

$metrics = [ordered]@{
    cacheMode = $cacheMode
    startedAt = $startedAt.ToString('o')
    completedAt = $completedAt.ToString('o')
    k6ExitCode = $k6ExitCode
    measuredScenario = $measuredScenario
    measuredSeconds = [int]$measuredSeconds
    productId = $ProductId
    jdbcStatementCountMethod = 'authorized Actuator whole-run delta; the untagged real total is retained on each endpoint'
    jdbcStatementDelta = $jdbcStatementDelta
    endpointMetrics = $endpointMetrics
    actuator = [ordered]@{ before = $before; after = $after }
}

$metrics | ConvertTo-Json -Depth 30 | Set-Content -Path $metricsPath -Encoding utf8
Remove-Item -LiteralPath $rawPath -Force

if ($k6ExitCode -ne 0) {
    throw "k6 exited with code $k6ExitCode; summary and metrics snapshots were retained"
}

Write-Output $metricsPath
