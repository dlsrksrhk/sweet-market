[CmdletBinding()]
param(
    [ValidateNotNullOrEmpty()]
    [string]$EnvFile = '.env.integration'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (Test-Path variable:PSNativeCommandUseErrorActionPreference) {
    $PSNativeCommandUseErrorActionPreference = $false
}

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$composeFile = Join-Path $repositoryRoot 'docker-compose.integration.yml'
$resolvedEnvFile = if ([IO.Path]::IsPathRooted($EnvFile)) {
    $EnvFile
} else {
    Join-Path $repositoryRoot $EnvFile
}
$requiredVariables = @(
    'JWT_SECRET',
    'MARKET_DB_NAME', 'MARKET_DB_USERNAME', 'MARKET_DB_PASSWORD',
    'PAYMENT_GATEWAY_DB_NAME', 'PAYMENT_GATEWAY_DB_USERNAME', 'PAYMENT_GATEWAY_DB_PASSWORD',
    'DELIVERY_PROVIDER_DB_NAME', 'DELIVERY_PROVIDER_DB_USERNAME', 'DELIVERY_PROVIDER_DB_PASSWORD',
    'MARKET_PAYMENT_GATEWAY_API_KEY', 'MARKET_PAYMENT_GATEWAY_HMAC_KEY_ID',
    'MARKET_PAYMENT_GATEWAY_HMAC_SECRET', 'MARKET_PAYMENT_GATEWAY_HMAC_NEXT_KEY_ID',
    'MARKET_PAYMENT_GATEWAY_HMAC_NEXT_SECRET', 'PAYMENT_GATEWAY_WEBHOOK_API_KEY',
    'PAYMENT_GATEWAY_WEBHOOK_CURRENT_KEY_ID', 'PAYMENT_GATEWAY_WEBHOOK_CURRENT_SECRET',
    'PAYMENT_GATEWAY_WEBHOOK_NEXT_KEY_ID', 'PAYMENT_GATEWAY_WEBHOOK_NEXT_SECRET',
    'MARKET_DELIVERY_PROVIDER_API_KEY', 'MARKET_DELIVERY_PROVIDER_HMAC_KEY_ID',
    'MARKET_DELIVERY_PROVIDER_HMAC_SECRET', 'MARKET_DELIVERY_PROVIDER_HMAC_NEXT_KEY_ID',
    'MARKET_DELIVERY_PROVIDER_HMAC_NEXT_SECRET', 'DELIVERY_PROVIDER_WEBHOOK_API_KEY',
    'DELIVERY_PROVIDER_WEBHOOK_CURRENT_KEY_ID', 'DELIVERY_PROVIDER_WEBHOOK_CURRENT_SECRET',
    'DELIVERY_PROVIDER_WEBHOOK_NEXT_KEY_ID', 'DELIVERY_PROVIDER_WEBHOOK_NEXT_SECRET'
)

function Read-EnvironmentFile {
    param([Parameter(Mandatory)] [string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "environment file is missing: $([IO.Path]::GetFileName($Path))"
    }
    $environmentValues = [Collections.Generic.Dictionary[string, string]]::new([StringComparer]::OrdinalIgnoreCase)
    $lineNumber = 0
    foreach ($rawLine in [IO.File]::ReadLines($Path)) {
        $lineNumber++
        $line = $rawLine.Trim()
        if ($line.Length -eq 0 -or $line.StartsWith('#')) {
            continue
        }
        $separator = $line.IndexOf('=')
        if ($separator -lt 1) {
            throw "invalid environment entry at line $lineNumber"
        }
        $name = $line.Substring(0, $separator).Trim()
        if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
            throw "invalid environment name at line $lineNumber"
        }
        if ($environmentValues.ContainsKey($name)) {
            throw "duplicate environment name at line $lineNumber"
        }
        $environmentValues.Add($name, $line.Substring($separator + 1))
    }
    return $environmentValues
}

function Get-RequiredConfiguration {
    param(
        [Parameter(Mandatory)] [Collections.Generic.Dictionary[string, string]]$EnvironmentValues
    )

    $configuration = [Collections.Generic.Dictionary[string, string]]::new([StringComparer]::Ordinal)
    foreach ($name in $requiredVariables) {
        $processValue = [Environment]::GetEnvironmentVariable($name, [EnvironmentVariableTarget]::Process)
        $value = if ($null -ne $processValue) {
            $processValue
        } elseif ($EnvironmentValues.ContainsKey($name)) {
            $EnvironmentValues[$name]
        } else {
            $null
        }
        if ([string]::IsNullOrWhiteSpace($value)) {
            throw "required environment variable is missing or empty: $name"
        }
        $configuration.Add($name, $value)
    }
    return $configuration
}

function New-SignedHeaders {
    param(
        [Parameter(Mandatory)] [string]$ApiKey,
        [Parameter(Mandatory)] [string]$KeyId,
        [Parameter(Mandatory)] [string]$Secret,
        [Parameter(Mandatory)] [string]$Method,
        [Parameter(Mandatory)] [string]$RawTarget,
        [Parameter(Mandatory)] [byte[]]$Body,
        [Parameter(Mandatory)] [Guid]$RequestId,
        [Parameter(Mandatory)] [DateTimeOffset]$Timestamp,
        [Parameter(Mandatory)] [Guid]$CorrelationId
    )

    $bodyHash = [Convert]::ToHexString(
        [Security.Cryptography.SHA256]::HashData($Body)
    ).ToLowerInvariant()
    $canonical = @(
        'v1'
        $KeyId
        $Timestamp.ToUnixTimeSeconds().ToString([Globalization.CultureInfo]::InvariantCulture)
        $RequestId.ToString()
        $Method.ToUpperInvariant()
        $RawTarget
        $bodyHash
    ) -join "`n"
    $hmac = [Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($Secret))
    try {
        $signature = [Convert]::ToHexString(
            $hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($canonical))
        ).ToLowerInvariant()
    } finally {
        $hmac.Dispose()
    }

    return @{
        'X-Api-Key' = $ApiKey
        'X-Key-Id' = $KeyId
        'X-Request-Id' = $RequestId.ToString()
        'X-Timestamp' = $Timestamp.ToUnixTimeSeconds().ToString([Globalization.CultureInfo]::InvariantCulture)
        'X-Signature' = $signature
        'X-Correlation-Id' = $CorrelationId.ToString()
        'Content-Type' = 'application/json'
    }
}

function Invoke-SignedRequest {
    param(
        [Parameter(Mandatory)] [string]$BaseUrl,
        [Parameter(Mandatory)] [string]$RawTarget,
        [Parameter(Mandatory)] [byte[]]$Body,
        [byte[]]$SigningBody = $Body,
        [Parameter(Mandatory)] [string]$ApiKey,
        [Parameter(Mandatory)] [string]$KeyId,
        [Parameter(Mandatory)] [string]$Secret,
        [Parameter(Mandatory)] [Guid]$RequestId,
        [Parameter(Mandatory)] [DateTimeOffset]$Timestamp,
        [Parameter(Mandatory)] [Guid]$CorrelationId,
        [Parameter(Mandatory)] [int]$ExpectedStatus
    )

    $headers = New-SignedHeaders -ApiKey $ApiKey -KeyId $KeyId -Secret $Secret `
        -Method 'POST' -RawTarget $RawTarget -Body $SigningBody -RequestId $RequestId `
        -Timestamp $Timestamp -CorrelationId $CorrelationId
    $client = [Net.Http.HttpClient]::new()
    $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Post, "$BaseUrl$RawTarget")
    $content = [Net.Http.ByteArrayContent]::new($Body)
    $content.Headers.ContentType = [Net.Http.Headers.MediaTypeHeaderValue]::new('application/json')
    $request.Content = $content
    foreach ($entry in $headers.GetEnumerator()) {
        if ($entry.Key -ne 'Content-Type') {
            [void]$request.Headers.TryAddWithoutValidation($entry.Key, $entry.Value)
        }
    }
    $response = $null
    try {
        $response = $client.Send($request)
        $responseBytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
        $status = [int]$response.StatusCode
        if ($status -ne $ExpectedStatus) {
            throw "signed request returned HTTP $status; expected $ExpectedStatus"
        }
        $responseText = [Text.Encoding]::UTF8.GetString($responseBytes)
        $json = if ($responseText.Length -gt 0 -and $response.Content.Headers.ContentType.MediaType -eq 'application/json') {
            $responseText | ConvertFrom-Json
        } else {
            $null
        }
        return [pscustomobject]@{ StatusCode = $status; Json = $json }
    } finally {
        if ($null -ne $response) { $response.Dispose() }
        $request.Dispose()
        $client.Dispose()
    }
}

function Invoke-ChunkedOversizeRequest {
    param([Parameter(Mandatory)] [string]$Url)

    $payload = [byte[]]::new(1048577)
    $stream = [IO.MemoryStream]::new($payload, $false)
    $content = [Net.Http.StreamContent]::new($stream)
    $content.Headers.ContentType = [Net.Http.Headers.MediaTypeHeaderValue]::new('application/json')
    $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Post, $Url)
    $request.Version = [Version]::new(1, 1)
    $request.VersionPolicy = [Net.Http.HttpVersionPolicy]::RequestVersionExact
    $request.Headers.TransferEncodingChunked = $true
    $request.Content = $content
    $client = [Net.Http.HttpClient]::new()
    $response = $null
    try {
        $response = $client.Send($request)
        if ([int]$response.StatusCode -ne 413) {
            throw "chunked oversize request returned HTTP $([int]$response.StatusCode); expected 413"
        }
    } finally {
        if ($null -ne $response) { $response.Dispose() }
        $request.Dispose()
        $client.Dispose()
        $stream.Dispose()
    }
}

function Invoke-Compose {
    param(
        [Parameter(Mandatory)] [string[]]$Arguments,
        [switch]$Capture
    )

    $output = @(& docker compose -f $composeFile @Arguments 2>&1)
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "docker compose $($Arguments[0]) failed with exit code $exitCode"
    }
    if ($Capture) {
        return $output | ForEach-Object ToString
    }
}

function Wait-ComposeHealth {
    param(
        [Parameter(Mandatory)] [string[]]$Services,
        [Parameter(Mandatory)] [DateTimeOffset]$Deadline
    )

    do {
        $allHealthy = $true
        foreach ($service in $Services) {
            $containerId = (Invoke-Compose -Arguments @('ps', '-q', $service) -Capture | Select-Object -First 1).Trim()
            if ([string]::IsNullOrWhiteSpace($containerId)) {
                $allHealthy = $false
                continue
            }
            $state = @(& docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $containerId 2>&1)
            if ($LASTEXITCODE -ne 0 -or ($state -join '').Trim() -ne 'healthy') {
                $allHealthy = $false
            }
        }
        if ($allHealthy) {
            return
        }
        if ([DateTimeOffset]::UtcNow -lt $Deadline) {
            Start-Sleep -Seconds 2
        }
    } while ([DateTimeOffset]::UtcNow -lt $Deadline)

    throw 'compose services did not become healthy before the deadline'
}

function Assert-ProbeEcho {
    param(
        [Parameter(Mandatory)] $Response,
        [Parameter(Mandatory)] [string]$Service,
        [Parameter(Mandatory)] [string]$Message,
        [Parameter(Mandatory)] [Guid]$RequestId,
        [Parameter(Mandatory)] [Guid]$CorrelationId
    )

    if ($null -eq $Response.Json -or
        [string]$Response.Json.service -cne $Service -or
        [string]$Response.Json.message -cne $Message -or
        [string]$Response.Json.requestId -cne $RequestId.ToString() -or
        [string]$Response.Json.correlationId -cne $CorrelationId.ToString()) {
        throw 'signed simulator probe did not return the exact expected echo and correlation'
    }
}

function Assert-DatabaseIsolation {
    param([Parameter(Mandatory)] [Collections.Generic.Dictionary[string, string]]$Configuration)

    $expected = [ordered]@{
        'market-postgres' = $Configuration['MARKET_DB_NAME']
        'payment-postgres' = $Configuration['PAYMENT_GATEWAY_DB_NAME']
        'delivery-postgres' = $Configuration['DELIVERY_PROVIDER_DB_NAME']
    }
    $duplicates = @($expected.Values | Group-Object | Where-Object { $_.Count -gt 1 })
    if ($duplicates.Count -gt 0) {
        throw 'database isolation audit found duplicate configured database names'
    }
    foreach ($service in $expected.Keys) {
        $actual = Invoke-Compose -Arguments @(
            'exec', '-T', $service, 'sh', '-c',
            'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc "select current_database()"'
        ) -Capture
        if (($actual -join '').Trim() -cne $expected[$service]) {
            throw "database isolation audit failed for service $service"
        }
    }
}

function Assert-LogsContainNoSecrets {
    param([Parameter(Mandatory)] [Collections.Generic.Dictionary[string, string]]$Configuration)

    $logs = (Invoke-Compose -Arguments @('logs', '--no-color') -Capture) -join "`n"
    $credentialNames = @($requiredVariables | Where-Object { $_ -match '(SECRET|PASSWORD|API_KEY)$' })
    foreach ($name in $credentialNames) {
        if ($logs.Contains((Get-RequiredConfiguration -EnvironmentValues $script:environmentValues)[$name])) {
            throw "compose log secret scan found configured credential: $name"
        }
    }
}

function Invoke-CheckedStep {
    param(
        [Parameter(Mandatory, Position = 0)] [string]$Name,
        [Parameter(Mandatory, Position = 1)] [scriptblock]$Action
    )

    $startedAt = [Diagnostics.Stopwatch]::StartNew()
    & $Action
    $startedAt.Stop()
    Write-Output ("PASS {0} ({1:n1}s)" -f $Name, $startedAt.Elapsed.TotalSeconds)
}

function Invoke-GradleChecked {
    param([Parameter(Mandatory)] [scriptblock]$Command)

    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed with exit code $LASTEXITCODE"
    }
}

function Restore-ProcessEnvironment {
    param(
        [Parameter(Mandatory)] [hashtable]$OriginalEnvironment,
        [Parameter(Mandatory)] [string[]]$Names,
        [AllowNull()] [string]$OriginalJavaHome,
        [AllowNull()] [string]$OriginalPath
    )

    $valuesToRestore = @{}
    foreach ($name in $Names) {
        $valuesToRestore[$name] = $OriginalEnvironment[$name]
    }
    $valuesToRestore['JAVA_HOME'] = $OriginalJavaHome
    $valuesToRestore['PATH'] = $OriginalPath
    foreach ($entry in $valuesToRestore.GetEnumerator()) {
        if ($null -eq $entry.Value) {
            Remove-Item -LiteralPath "Env:$($entry.Key)" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable(
                $entry.Key, $entry.Value, [EnvironmentVariableTarget]::Process)
        }
    }
}

$originalEnvironment = @{}
foreach ($name in $requiredVariables) {
    $originalEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, [EnvironmentVariableTarget]::Process)
}
$originalJavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', [EnvironmentVariableTarget]::Process)
$originalPath = [Environment]::GetEnvironmentVariable('PATH', [EnvironmentVariableTarget]::Process)
$primaryError = $null
$cleanupError = $null

try {
    $script:environmentValues = Read-EnvironmentFile $resolvedEnvFile
    $configuration = Get-RequiredConfiguration $script:environmentValues
    foreach ($name in $requiredVariables) {
        [Environment]::SetEnvironmentVariable($name, $configuration[$name], [EnvironmentVariableTarget]::Process)
    }
    [Environment]::SetEnvironmentVariable('JAVA_HOME', 'C:\java\jdk-21', [EnvironmentVariableTarget]::Process)
    [Environment]::SetEnvironmentVariable('PATH', "C:\java\jdk-21\bin;$originalPath", [EnvironmentVariableTarget]::Process)

    Push-Location $repositoryRoot
    try {
        Invoke-CheckedStep 'Node contract tests' {
            & node --test contracts/contracts.test.mjs *> $null
            if ($LASTEXITCODE -ne 0) { throw 'Node contract tests failed' }
        }
        Invoke-CheckedStep 'PowerShell parser/static tests' {
            & pwsh -NoProfile -File scripts/verify-m32-scripts.test.ps1 *> $null
            if ($LASTEXITCODE -ne 0) { throw 'PowerShell parser/static tests failed' }
        }
        Invoke-CheckedStep 'boundary audit' {
            & pwsh -NoProfile -File scripts/verify-m32-boundaries.ps1 *> $null
            if ($LASTEXITCODE -ne 0) { throw 'boundary audit failed' }
        }
        Invoke-CheckedStep 'Payment Gateway complete tests' {
            Push-Location backend
            try {
                Invoke-GradleChecked {
                    & .\gradlew.bat -p ..\mock-payment-gateway clean test --no-daemon *> $null
                }
            }
            finally { Pop-Location }
        }
        Invoke-CheckedStep 'Delivery Provider complete tests' {
            Push-Location backend
            try {
                Invoke-GradleChecked {
                    & .\gradlew.bat -p ..\mock-delivery-provider clean test --no-daemon *> $null
                }
            }
            finally { Pop-Location }
        }
        Invoke-CheckedStep 'Sweet Market complete backend tests' {
            [Environment]::SetEnvironmentVariable('JWT_SECRET', (Get-RequiredConfiguration $script:environmentValues)['JWT_SECRET'], [EnvironmentVariableTarget]::Process)
            Push-Location backend
            try {
                Invoke-GradleChecked {
                    & .\gradlew.bat -p . clean test --no-daemon *> $null
                }
            }
            finally { Pop-Location }
        }
        Invoke-CheckedStep 'Compose config and build' {
            Invoke-Compose -Arguments @('config', '--quiet')
            Invoke-Compose -Arguments @('build')
        }
        Invoke-CheckedStep 'Compose up and health' {
            Invoke-Compose -Arguments @('up', '-d')
            Wait-ComposeHealth -Services @('market', 'mock-payment-gateway', 'mock-delivery-provider') `
                -Deadline ([DateTimeOffset]::UtcNow.AddMinutes(4))
        }

        $utf8 = [Text.UTF8Encoding]::new($false)
        $paymentMessage = 'payment-integration-probe'
        $paymentBody = $utf8.GetBytes('{"message":"payment-integration-probe"}')
        $paymentRequestId = [Guid]::NewGuid()
        $paymentCorrelationId = [Guid]::NewGuid()
        $paymentTimestamp = [DateTimeOffset]::UtcNow
        Invoke-CheckedStep 'signed Payment Gateway probe' {
            $response = Invoke-SignedRequest -BaseUrl 'http://localhost:8081' -RawTarget '/api/v1/probes' `
                -Body $paymentBody -ApiKey $configuration['MARKET_PAYMENT_GATEWAY_API_KEY'] `
                -KeyId $configuration['MARKET_PAYMENT_GATEWAY_HMAC_KEY_ID'] `
                -Secret $configuration['MARKET_PAYMENT_GATEWAY_HMAC_SECRET'] `
                -RequestId $paymentRequestId -Timestamp $paymentTimestamp `
                -CorrelationId $paymentCorrelationId -ExpectedStatus 200
            Assert-ProbeEcho $response 'mock-payment-gateway' $paymentMessage $paymentRequestId $paymentCorrelationId
        }

        $deliveryMessage = 'delivery-integration-probe'
        $deliveryBody = $utf8.GetBytes('{"message":"delivery-integration-probe"}')
        $deliveryRequestId = [Guid]::NewGuid()
        $deliveryCorrelationId = [Guid]::NewGuid()
        $deliveryTimestamp = [DateTimeOffset]::UtcNow
        Invoke-CheckedStep 'signed Delivery Provider probe' {
            $response = Invoke-SignedRequest -BaseUrl 'http://localhost:8082' -RawTarget '/api/v1/probes' `
                -Body $deliveryBody -ApiKey $configuration['MARKET_DELIVERY_PROVIDER_API_KEY'] `
                -KeyId $configuration['MARKET_DELIVERY_PROVIDER_HMAC_KEY_ID'] `
                -Secret $configuration['MARKET_DELIVERY_PROVIDER_HMAC_SECRET'] `
                -RequestId $deliveryRequestId -Timestamp $deliveryTimestamp `
                -CorrelationId $deliveryCorrelationId -ExpectedStatus 200
            Assert-ProbeEcho $response 'mock-delivery-provider' $deliveryMessage $deliveryRequestId $deliveryCorrelationId
        }

        Invoke-CheckedStep 'signed payment webhook probe' {
            $requestId = [Guid]::NewGuid()
            $correlationId = [Guid]::NewGuid()
            $body = $utf8.GetBytes('{"source":"PAYMENT_GATEWAY","message":"payment-webhook-probe"}')
            [void](Invoke-SignedRequest -BaseUrl 'http://localhost:8080' `
                -RawTarget '/api/integrations/payment-gateway/v1/probes' -Body $body `
                -ApiKey $configuration['PAYMENT_GATEWAY_WEBHOOK_API_KEY'] `
                -KeyId $configuration['PAYMENT_GATEWAY_WEBHOOK_CURRENT_KEY_ID'] `
                -Secret $configuration['PAYMENT_GATEWAY_WEBHOOK_CURRENT_SECRET'] `
                -RequestId $requestId -Timestamp ([DateTimeOffset]::UtcNow) `
                -CorrelationId $correlationId -ExpectedStatus 204)
        }
        Invoke-CheckedStep 'signed delivery webhook probe' {
            $requestId = [Guid]::NewGuid()
            $correlationId = [Guid]::NewGuid()
            $body = $utf8.GetBytes('{"source":"DELIVERY_PROVIDER","message":"delivery-webhook-probe"}')
            [void](Invoke-SignedRequest -BaseUrl 'http://localhost:8080' `
                -RawTarget '/api/integrations/delivery-provider/v1/probes' -Body $body `
                -ApiKey $configuration['DELIVERY_PROVIDER_WEBHOOK_API_KEY'] `
                -KeyId $configuration['DELIVERY_PROVIDER_WEBHOOK_CURRENT_KEY_ID'] `
                -Secret $configuration['DELIVERY_PROVIDER_WEBHOOK_CURRENT_SECRET'] `
                -RequestId $requestId -Timestamp ([DateTimeOffset]::UtcNow) `
                -CorrelationId $correlationId -ExpectedStatus 204)
        }
        Invoke-CheckedStep 'replay negative' {
            [void](Invoke-SignedRequest -BaseUrl 'http://localhost:8081' -RawTarget '/api/v1/probes' `
                -Body $paymentBody -ApiKey $configuration['MARKET_PAYMENT_GATEWAY_API_KEY'] `
                -KeyId $configuration['MARKET_PAYMENT_GATEWAY_HMAC_KEY_ID'] `
                -Secret $configuration['MARKET_PAYMENT_GATEWAY_HMAC_SECRET'] `
                -RequestId $paymentRequestId -Timestamp $paymentTimestamp `
                -CorrelationId $paymentCorrelationId -ExpectedStatus 409)
        }
        Invoke-CheckedStep 'mutated body negative' {
            $signedBody = $utf8.GetBytes('{"message":"signed-original"}')
            $mutatedBody = $utf8.GetBytes('{"message":"mutated-after-signing"}')
            [void](Invoke-SignedRequest -BaseUrl 'http://localhost:8081' -RawTarget '/api/v1/probes' `
                -Body $mutatedBody -SigningBody $signedBody `
                -ApiKey $configuration['MARKET_PAYMENT_GATEWAY_API_KEY'] `
                -KeyId $configuration['MARKET_PAYMENT_GATEWAY_HMAC_KEY_ID'] `
                -Secret $configuration['MARKET_PAYMENT_GATEWAY_HMAC_SECRET'] `
                -RequestId ([Guid]::NewGuid()) -Timestamp ([DateTimeOffset]::UtcNow) `
                -CorrelationId ([Guid]::NewGuid()) -ExpectedStatus 401)
        }
        Invoke-CheckedStep 'expired timestamp negative' {
            [void](Invoke-SignedRequest -BaseUrl 'http://localhost:8082' -RawTarget '/api/v1/probes' `
                -Body $deliveryBody -ApiKey $configuration['MARKET_DELIVERY_PROVIDER_API_KEY'] `
                -KeyId $configuration['MARKET_DELIVERY_PROVIDER_HMAC_KEY_ID'] `
                -Secret $configuration['MARKET_DELIVERY_PROVIDER_HMAC_SECRET'] `
                -RequestId ([Guid]::NewGuid()) -Timestamp ([DateTimeOffset]::UtcNow.AddSeconds(-301)) `
                -CorrelationId ([Guid]::NewGuid()) -ExpectedStatus 401)
        }
        Invoke-CheckedStep 'chunked oversize negative' {
            Invoke-ChunkedOversizeRequest 'http://localhost:8080/api/integrations/payment-gateway/v1/probes'
        }
        Invoke-CheckedStep 'simulator restart replay persistence' {
            Invoke-Compose -Arguments @('restart', 'mock-payment-gateway')
            Wait-ComposeHealth -Services @('mock-payment-gateway') -Deadline ([DateTimeOffset]::UtcNow.AddMinutes(2))
            [void](Invoke-SignedRequest -BaseUrl 'http://localhost:8081' -RawTarget '/api/v1/probes' `
                -Body $paymentBody -ApiKey $configuration['MARKET_PAYMENT_GATEWAY_API_KEY'] `
                -KeyId $configuration['MARKET_PAYMENT_GATEWAY_HMAC_KEY_ID'] `
                -Secret $configuration['MARKET_PAYMENT_GATEWAY_HMAC_SECRET'] `
                -RequestId $paymentRequestId -Timestamp $paymentTimestamp `
                -CorrelationId $paymentCorrelationId -ExpectedStatus 409)
            Invoke-Compose -Arguments @('restart', 'mock-delivery-provider')
            Wait-ComposeHealth -Services @('mock-delivery-provider') -Deadline ([DateTimeOffset]::UtcNow.AddMinutes(2))
            [void](Invoke-SignedRequest -BaseUrl 'http://localhost:8082' -RawTarget '/api/v1/probes' `
                -Body $deliveryBody -ApiKey $configuration['MARKET_DELIVERY_PROVIDER_API_KEY'] `
                -KeyId $configuration['MARKET_DELIVERY_PROVIDER_HMAC_KEY_ID'] `
                -Secret $configuration['MARKET_DELIVERY_PROVIDER_HMAC_SECRET'] `
                -RequestId $deliveryRequestId -Timestamp $deliveryTimestamp `
                -CorrelationId $deliveryCorrelationId -ExpectedStatus 409)
        }
        Invoke-CheckedStep 'database isolation audit' {
            Assert-DatabaseIsolation $configuration
        }
        Invoke-CheckedStep 'Compose logs secret scan' {
            Assert-LogsContainNoSecrets $configuration
        }
    } finally {
        Pop-Location
    }
} catch {
    $primaryError = $_
} finally {
    try {
        foreach ($name in $requiredVariables) {
            $currentValue = [Environment]::GetEnvironmentVariable($name, [EnvironmentVariableTarget]::Process)
            if ([string]::IsNullOrWhiteSpace($currentValue)) {
                [Environment]::SetEnvironmentVariable(
                    $name, 'm32-cleanup-placeholder', [EnvironmentVariableTarget]::Process)
            }
        }
        Invoke-Compose -Arguments @('down', '--remove-orphans')
    } catch {
        $cleanupError = $_
    } finally {
        try {
            Restore-ProcessEnvironment -OriginalEnvironment $originalEnvironment -Names $requiredVariables `
                -OriginalJavaHome $originalJavaHome -OriginalPath $originalPath
        } catch {
            if ($null -eq $cleanupError) {
                $cleanupError = $_
            } else {
                Write-Error 'M32 verification environment restoration also failed.' -ErrorAction Continue
            }
        }
    }
}

if ($null -ne $primaryError) {
    if ($null -ne $cleanupError) {
        Write-Error 'M32 verification cleanup also failed.' -ErrorAction Continue
    }
    throw $primaryError
}
if ($null -ne $cleanupError) {
    throw 'M32 verification cleanup failed.'
}
