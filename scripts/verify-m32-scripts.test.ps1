[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$script:Failures = [System.Collections.Generic.List[string]]::new()
$script:Checks = 0
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$integrationPath = Join-Path $PSScriptRoot 'verify-m32-integration.ps1'
$boundaryPath = Join-Path $PSScriptRoot 'verify-m32-boundaries.ps1'

function Assert-True {
    param(
        [Parameter(Mandatory)] [bool]$Condition,
        [Parameter(Mandatory)] [string]$Message
    )

    $script:Checks++
    if (-not $Condition) {
        $script:Failures.Add($Message)
    }
}

function Assert-Match {
    param(
        [Parameter(Mandatory)] [string]$Text,
        [Parameter(Mandatory)] [string]$Pattern,
        [Parameter(Mandatory)] [string]$Message
    )

    Assert-True ([regex]::IsMatch($Text, $Pattern, [Text.RegularExpressions.RegexOptions]::Multiline)) $Message
}

function Read-ParsedScript {
    param([Parameter(Mandatory)] [string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Assert-True $false "required script is absent: $([IO.Path]::GetFileName($Path))"
        return $null
    }

    $tokens = $null
    $parseErrors = $null
    $ast = [Management.Automation.Language.Parser]::ParseFile($Path, [ref]$tokens, [ref]$parseErrors)
    Assert-True ($parseErrors.Count -eq 0) "$([IO.Path]::GetFileName($Path)) has PowerShell parse errors"
    return [pscustomobject]@{
        Ast = $ast
        Tokens = $tokens
        Source = [IO.File]::ReadAllText($Path)
    }
}

function Get-Commands {
    param([Parameter(Mandatory)] $Parsed)

    return @($Parsed.Ast.FindAll({
        param($node)
        $node -is [Management.Automation.Language.CommandAst]
    }, $true))
}

function Get-FunctionNames {
    param([Parameter(Mandatory)] $Parsed)

    return @($Parsed.Ast.FindAll({
        param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst]
    }, $true) | ForEach-Object Name)
}

function Assert-OrderedPatterns {
    param(
        [Parameter(Mandatory)] [string]$Text,
        [Parameter(Mandatory)] [Collections.Specialized.OrderedDictionary]$Patterns
    )

    $previous = -1
    foreach ($entry in $Patterns.GetEnumerator()) {
        $match = [regex]::Match($Text, $entry.Value, [Text.RegularExpressions.RegexOptions]::Multiline)
        Assert-True $match.Success "verification flow is missing step: $($entry.Key)"
        if ($match.Success) {
            Assert-True ($match.Index -gt $previous) "verification step is out of order: $($entry.Key)"
            $previous = $match.Index
        }
    }
}

$integration = Read-ParsedScript $integrationPath
$boundary = Read-ParsedScript $boundaryPath

if ($null -ne $boundary) {
    $functions = Get-FunctionNames $boundary
    $commands = Get-Commands $boundary
    Assert-True ($functions -contains 'Find-ProhibitedImports') 'boundary audit must define an import scanner'
    Assert-True ($functions -contains 'Get-ConfiguredDatabaseName') 'boundary audit must parse configured JDBC database names'
    Assert-True (@($commands | Where-Object { $_.GetCommandName() -eq 'Get-ChildItem' }).Count -gt 0) 'boundary audit must enumerate source files'
    Assert-True (@($commands | Where-Object { $_.GetCommandName() -eq 'Select-String' }).Count -gt 0) 'boundary audit must inspect imports with .NET regex matching'
    Assert-Match $boundary.Source "import com\\\.sweet\\\.market\\\.(?!gateway)" 'payment gateway boundary regex is missing'
    Assert-Match $boundary.Source "import com\\\.sweet\\\.market\\\.(?!provider)" 'delivery provider boundary regex is missing'
    Assert-True ($boundary.Source.Contains("'com\.sweet\.market\.(gateway|provider)'")) 'backend simulator-import boundary regex is missing'
    Assert-Match $boundary.Source 'application\.yaml' 'boundary audit must inspect application YAML files'
    Assert-Match $boundary.Source '(?s)Group-Object.+Count\s+-gt\s+1' 'boundary audit must fail duplicate database names'
    Assert-Match $boundary.Source '(?s)(Name|rule).*(Path|file)' 'boundary findings must identify only rule and file information'
    Assert-True (-not [regex]::IsMatch($boundary.Source, '(?i)Write-(Host|Output).*(password|secret|api.?key)')) 'boundary audit output must not print credentials'
}

if ($null -ne $integration) {
    $functions = Get-FunctionNames $integration
    $commands = Get-Commands $integration
    $requiredFunctions = @(
        'Read-EnvironmentFile', 'Get-RequiredConfiguration', 'New-SignedHeaders',
        'Invoke-SignedRequest', 'Wait-ComposeHealth', 'Invoke-ChunkedOversizeRequest',
        'Assert-DatabaseIsolation', 'Assert-LogsContainNoSecrets', 'Invoke-Compose'
    )
    foreach ($name in $requiredFunctions) {
        Assert-True ($functions -contains $name) "integration script must define $name"
    }

    $parameters = @($integration.Ast.ParamBlock.Parameters | ForEach-Object { $_.Name.VariablePath.UserPath })
    Assert-True ($parameters.Count -eq 1 -and $parameters[0] -eq 'EnvFile') 'integration script command line must accept only -EnvFile'
    Assert-True (-not ($parameters -match '(?i)secret|password|api.?key|key.?id')) 'integration script must not accept credentials on the command line'

    Assert-Match $integration.Source '(?s)ContainsKey\(.+throw.+duplicate' 'environment parser must reject duplicate names'
    Assert-Match $integration.Source '(?s)GetEnvironmentVariable.+environmentValues' 'process environment must be checked before file values'
    Assert-Match $integration.Source '(?s)requiredVariables.+IsNullOrWhiteSpace.+missing|required.*empty' 'required values must reject missing or empty entries'
    Assert-True (-not [regex]::IsMatch($integration.Source, '(?i)Write-(Host|Output).*(Secret|Password|ApiKey|KeyId)\b')) 'configured credential values must never be written to output'
    $outputCommands = @($commands | Where-Object { $_.GetCommandName() -in @('Write-Host', 'Write-Output', 'Write-Error') })
    foreach ($outputCommand in $outputCommands) {
        Assert-True (-not [regex]::IsMatch(
            $outputCommand.Extent.Text,
            '(?i)\$(configuration|environmentValues|secret|apiKey|password|logs|response|output)\b'
        )) 'output commands must not receive credential-bearing variables'
    }
    foreach ($functionName in @('Read-EnvironmentFile', 'Get-RequiredConfiguration')) {
        $functionAst = @($integration.Ast.FindAll({
            param($node)
            $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $functionName
        }, $true) | Select-Object -First 1)
        Invoke-Expression $functionAst[0].Extent.Text
    }
    $duplicateTestPath = Join-Path ([IO.Path]::GetTempPath()) "m32-env-duplicate-$([Guid]::NewGuid().ToString('N')).env"
    $validTestPath = Join-Path ([IO.Path]::GetTempPath()) "m32-env-valid-$([Guid]::NewGuid().ToString('N')).env"
    $testVariableName = 'M32_STATIC_REQUIRED_VALUE'
    $originalTestValue = [Environment]::GetEnvironmentVariable($testVariableName, [EnvironmentVariableTarget]::Process)
    $requiredVariables = @($testVariableName)
    try {
        [IO.File]::WriteAllText($duplicateTestPath, "$testVariableName=file-value`n$($testVariableName.ToLowerInvariant())=duplicate-value`n")
        $duplicateRejected = $false
        try { [void](Read-EnvironmentFile $duplicateTestPath) } catch { $duplicateRejected = $_.Exception.Message.Contains('duplicate') }
        Assert-True $duplicateRejected 'environment parser must behaviorally reject case-insensitive duplicate names'

        [IO.File]::WriteAllText($validTestPath, "$testVariableName=file-value`n")
        $parsedValues = Read-EnvironmentFile $validTestPath
        [Environment]::SetEnvironmentVariable($testVariableName, 'process-value', [EnvironmentVariableTarget]::Process)
        Assert-True ((Get-RequiredConfiguration $parsedValues)[$testVariableName] -ceq 'process-value') 'process environment must behaviorally override the file value'

        [Environment]::SetEnvironmentVariable($testVariableName, '', [EnvironmentVariableTarget]::Process)
        $emptyRejected = $false
        try { [void](Get-RequiredConfiguration $parsedValues) } catch { $emptyRejected = $_.Exception.Message.Contains($testVariableName) }
        Assert-True $emptyRejected 'an explicitly empty process value must be rejected instead of falling back to the file'

        [Environment]::SetEnvironmentVariable($testVariableName, $null, [EnvironmentVariableTarget]::Process)
        $missingValues = [Collections.Generic.Dictionary[string, string]]::new([StringComparer]::OrdinalIgnoreCase)
        $missingRejected = $false
        try { [void](Get-RequiredConfiguration $missingValues) } catch { $missingRejected = $_.Exception.Message.Contains($testVariableName) }
        Assert-True $missingRejected 'missing required values must be behaviorally rejected with the variable name only'
    } finally {
        [Environment]::SetEnvironmentVariable($testVariableName, $originalTestValue, [EnvironmentVariableTarget]::Process)
        Remove-Item -LiteralPath $duplicateTestPath, $validTestPath -Force -ErrorAction SilentlyContinue
    }

    Assert-Match $integration.Source '\[Security\.Cryptography\.SHA256\]::HashData\(' 'body hash must use SHA-256 over bytes'
    Assert-Match $integration.Source 'Security\.Cryptography\.HMACSHA256' 'signature must use HMAC-SHA256'
    Assert-Match $integration.Source '(?s)''v1''.+KeyId.+ToUnixTimeSeconds.+RequestId.+ToUpperInvariant.+RawTarget.+bodyHash.+-join\s+"`n"' 'canonical payload must contain the exact seven ordered lines'
    Assert-Match $integration.Source '(?s)X-Api-Key.+X-Key-Id.+X-Request-Id.+X-Timestamp.+X-Signature.+X-Correlation-Id.+Content-Type' 'signed headers must include all seven protocol headers'
    Assert-Match $integration.Source 'ToLowerInvariant\(\)' 'hex output must be lowercase'
    $signingFunction = @($integration.Ast.FindAll({
        param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'New-SignedHeaders'
    }, $true) | Select-Object -First 1)
    Invoke-Expression $signingFunction[0].Extent.Text
    $vector = Get-Content -LiteralPath (Join-Path $repositoryRoot 'contracts\hmac-v1-test-vectors.json') -Raw | ConvertFrom-Json
    $vectorHeaders = New-SignedHeaders -ApiKey $vector.apiKey -KeyId $vector.keyId -Secret $vector.secret `
        -Method $vector.method -RawTarget $vector.rawTarget -Body ([Text.Encoding]::UTF8.GetBytes($vector.bodyUtf8)) `
        -RequestId ([Guid]::Parse($vector.requestId)) `
        -Timestamp ([DateTimeOffset]::FromUnixTimeSeconds($vector.timestamp)) `
        -CorrelationId ([Guid]::Parse('42429beb-5d60-47b7-a3da-540cf1619d3b'))
    Assert-True ($vectorHeaders['X-Signature'] -ceq $vector.signature) 'New-SignedHeaders must reproduce the checked-in byte-exact HMAC vector'
    Assert-True ($vectorHeaders['X-Timestamp'] -ceq [string]$vector.timestamp) 'New-SignedHeaders must emit epoch seconds exactly'
    Assert-True ($vectorHeaders['Content-Type'] -ceq 'application/json') 'New-SignedHeaders must emit application/json'

    foreach ($path in @(
        '/api/v1/probes',
        '/api/integrations/payment-gateway/v1/probes',
        '/api/integrations/delivery-provider/v1/probes'
    )) {
        Assert-True ($integration.Source.Contains($path)) "integration script must use raw target $path"
    }
    Assert-True ([regex]::Matches($integration.Source, '\[Guid\]::NewGuid\(\)').Count -ge 4) 'independent probes and negative cases must create unique request IDs/correlations'

    Assert-Match $integration.Source '(?s)StreamContent.+TransferEncodingChunked\s*=\s*\$true' 'oversize probe must use chunked transfer encoding'
    Assert-Match $integration.Source '1_048_577|1048577' 'chunked negative body must be exactly 1 MiB plus one byte'
    Assert-Match $integration.Source '(?s)restart.+mock-payment-gateway.+Wait-ComposeHealth.+409' 'payment replay must be rechecked after simulator restart'
    Assert-Match $integration.Source '(?s)restart.+mock-delivery-provider.+Wait-ComposeHealth.+409' 'delivery replay must be rechecked after simulator restart'

    $finallyBlocks = @($integration.Ast.FindAll({
        param($node)
        $node -is [Management.Automation.Language.TryStatementAst] -and $null -ne $node.Finally
    }, $true))
    Assert-True ($finallyBlocks.Count -gt 0) 'integration script must guarantee cleanup with finally'
    Assert-Match $integration.Source '(?s)finally\s*\{.+Invoke-Compose.+''down''' 'finally cleanup must invoke docker compose down'
    Assert-Match $integration.Source '(?s)Wait-ComposeHealth.+Deadline|deadline.+Wait-ComposeHealth' 'health polling must use a bounded deadline'
    Assert-True (-not [regex]::IsMatch($integration.Source, '(?i)while\s*\(\s*\$true\s*\)')) 'health polling must not use an unbounded loop'

    Assert-Match $integration.Source '(?s)docker.+compose|&\s+docker\s+compose' 'integration script must invoke Docker Compose'
    foreach ($composeAction in @('config', 'build', 'up', 'restart', 'logs', 'down')) {
        Assert-True ($integration.Source.Contains("'$composeAction'")) "integration script must invoke docker compose $composeAction"
    }
    Assert-Match $integration.Source '(?m)^\s*&\s+node\s+--test\s+contracts/contracts\.test\.mjs' 'Node contract tests must execute through node --test'
    Assert-True ([regex]::Matches($integration.Source, '(?m)^\s*&\s+\.\\gradlew\.bat\s+-p\s+').Count -eq 3) 'exactly three Gradle builds must be invoked'
    Assert-Match $integration.Source 'JAVA_HOME.+C:\\java\\jdk-21' 'backend tests must explicitly use JDK 21'
    Assert-Match $integration.Source '(?s)JWT_SECRET.+Get-RequiredConfiguration' 'backend tests must receive the resolved JWT secret'

    $orderedSteps = [ordered]@{
        'Node contract tests' = "Invoke-CheckedStep\s+'Node contract tests'"
        'PowerShell parser/static tests' = "Invoke-CheckedStep\s+'PowerShell parser/static tests'"
        'boundary audit' = "Invoke-CheckedStep\s+'boundary audit'"
        'Payment Gateway complete tests' = "Invoke-CheckedStep\s+'Payment Gateway complete tests'"
        'Delivery Provider complete tests' = "Invoke-CheckedStep\s+'Delivery Provider complete tests'"
        'Sweet Market complete backend tests' = "Invoke-CheckedStep\s+'Sweet Market complete backend tests'"
        'Compose config and build' = "Invoke-CheckedStep\s+'Compose config and build'"
        'Compose up and health' = "Invoke-CheckedStep\s+'Compose up and health'"
        'signed Payment Gateway probe' = "Invoke-CheckedStep\s+'signed Payment Gateway probe'"
        'signed Delivery Provider probe' = "Invoke-CheckedStep\s+'signed Delivery Provider probe'"
        'signed payment webhook probe' = "Invoke-CheckedStep\s+'signed payment webhook probe'"
        'signed delivery webhook probe' = "Invoke-CheckedStep\s+'signed delivery webhook probe'"
        'replay negative' = "Invoke-CheckedStep\s+'replay negative'"
        'mutated body negative' = "Invoke-CheckedStep\s+'mutated body negative'"
        'expired timestamp negative' = "Invoke-CheckedStep\s+'expired timestamp negative'"
        'chunked oversize negative' = "Invoke-CheckedStep\s+'chunked oversize negative'"
        'simulator restart replay persistence' = "Invoke-CheckedStep\s+'simulator restart replay persistence'"
        'database isolation audit' = "Invoke-CheckedStep\s+'database isolation audit'"
        'Compose logs secret scan' = "Invoke-CheckedStep\s+'Compose logs secret scan'"
    }
    Assert-OrderedPatterns $integration.Source $orderedSteps

    foreach ($status in @(200, 204, 409, 401)) {
        Assert-Match $integration.Source "ExpectedStatus\s+$status\b" "integration flow must assert HTTP $status"
    }
    Assert-Match $integration.Source '(?s)Invoke-ChunkedOversizeRequest.+expected 413' 'integration flow must assert HTTP 413 for the chunked negative case'
    $echoFunction = @($integration.Ast.FindAll({
        param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Assert-ProbeEcho'
    }, $true) | Select-Object -First 1)
    foreach ($field in @('service', 'message', 'requestId', 'correlationId')) {
        Assert-True ($echoFunction[0].Extent.Text.Contains("Response.Json.$field")) "successful simulator probes must validate exact $field"
    }
    Assert-Match $integration.Source '(?s)Assert-LogsContainNoSecrets.+Get-RequiredConfiguration' 'log scan must compare output against configured credentials without printing them'
}

if ($script:Failures.Count -gt 0) {
    Write-Error ("M32 script verification failed ({0}/{1} checks):`n - {2}" -f $script:Failures.Count, $script:Checks, ($script:Failures -join "`n - "))
    exit 1
}

Write-Output "M32 script verification passed ($script:Checks checks)."
