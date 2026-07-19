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

function Get-CommandParameterValueText {
    param(
        [Parameter(Mandatory)] [Management.Automation.Language.CommandAst]$Command,
        [Parameter(Mandatory)] [string]$Name
    )

    for ($index = 0; $index -lt $Command.CommandElements.Count; $index++) {
        $element = $Command.CommandElements[$index]
        if ($element -is [Management.Automation.Language.CommandParameterAst] -and
            $element.ParameterName -eq $Name -and $index + 1 -lt $Command.CommandElements.Count) {
            return $Command.CommandElements[$index + 1].Extent.Text
        }
    }
    return $null
}

function Test-HasFunctionAncestor {
    param([Parameter(Mandatory)] [Management.Automation.Language.Ast]$Node)

    $parent = $Node.Parent
    while ($null -ne $parent) {
        if ($parent -is [Management.Automation.Language.FunctionDefinitionAst]) {
            return $true
        }
        $parent = $parent.Parent
    }
    return $false
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
    Assert-True ($functions -contains 'ConvertTo-JavaImportView') 'boundary audit must define a line-preserving Java lexical masker'
    Assert-True ($functions -contains 'Get-JavaLogicalLines') 'boundary audit must enumerate CRLF, lone CR, and lone LF logical lines'
    Assert-True ($functions -contains 'Get-ConfiguredDatabaseName') 'boundary audit must parse configured JDBC database names'
    Assert-True (@($commands | Where-Object { $_.GetCommandName() -eq 'Get-ChildItem' }).Count -gt 0) 'boundary audit must enumerate source files'
    Assert-True (@($commands | Where-Object { $_.GetCommandName() -eq 'Select-String' }).Count -gt 0) 'boundary audit must inspect imports with .NET regex matching'
    Assert-Match $boundary.Source '\(\?:static\\s\+\)\?' 'boundary regexes must support optional static imports'
    Assert-Match $boundary.Source '\^\\s\*import\\s\+' 'boundary regexes must be anchored to Java import statements'
    Assert-Match $boundary.Source 'gateway\(\?:\\\.\|;\)' 'gateway namespace matching must require a dot or semicolon delimiter'
    Assert-Match $boundary.Source 'provider\(\?:\\\.\|;\)' 'provider namespace matching must require a dot or semicolon delimiter'
    Assert-Match $boundary.Source 'application\.yaml' 'boundary audit must inspect application YAML files'
    Assert-Match $boundary.Source '(?s)Group-Object.+Count\s+-gt\s+1' 'boundary audit must fail duplicate database names'
    Assert-Match $boundary.Source '(?s)(Name|rule).*(Path|file)' 'boundary findings must identify only rule and file information'
    Assert-True (-not [regex]::IsMatch($boundary.Source, '(?i)Write-(Host|Output).*(password|secret|api.?key)')) 'boundary audit output must not print credentials'

    $boundaryCalls = @($commands | Where-Object { $_.GetCommandName() -eq 'Find-ProhibitedImports' })
    $paymentCall = $boundaryCalls | Where-Object { $_.CommandElements[1].SafeGetValue() -eq 'mock-payment-gateway\src' } | Select-Object -First 1
    $providerCall = $boundaryCalls | Where-Object { $_.CommandElements[1].SafeGetValue() -eq 'mock-delivery-provider\src' } | Select-Object -First 1
    $backendCall = $boundaryCalls | Where-Object { $_.CommandElements[1].SafeGetValue() -eq 'backend\src' } | Select-Object -First 1
    $paymentPattern = [string]$paymentCall.CommandElements[2].SafeGetValue()
    $providerPattern = [string]$providerCall.CommandElements[2].SafeGetValue()
    $backendPattern = [string]$backendCall.CommandElements[2].SafeGetValue()

    foreach ($allowedImport in @(
        'import com.sweet.market.gateway.probe.ProbeController;',
        'import static com.sweet.market.gateway.security.Headers.SIGNATURE;'
    )) {
        Assert-True (-not [regex]::IsMatch($allowedImport, $paymentPattern)) 'payment boundary must allow only its exact gateway namespace'
    }
    foreach ($prohibitedImport in @(
        'import com.sweet.market.gatewayevil.Escape;',
        'import static com.sweet.market.provider.security.Headers.SIGNATURE;'
    )) {
        Assert-True ([regex]::IsMatch($prohibitedImport, $paymentPattern)) 'payment boundary must reject prefixed or static foreign namespaces'
    }
    foreach ($allowedImport in @(
        'import com.sweet.market.provider.probe.ProbeController;',
        'import static com.sweet.market.provider.security.Headers.SIGNATURE;'
    )) {
        Assert-True (-not [regex]::IsMatch($allowedImport, $providerPattern)) 'provider boundary must allow only its exact provider namespace'
    }
    foreach ($prohibitedImport in @(
        'import com.sweet.market.providerfake.Escape;',
        'import static com.sweet.market.gateway.security.Headers.SIGNATURE;'
    )) {
        Assert-True ([regex]::IsMatch($prohibitedImport, $providerPattern)) 'provider boundary must reject prefixed or static foreign namespaces'
    }
    foreach ($prohibitedImport in @(
        'import com.sweet.market.gateway.probe.ProbeController;',
        'import static com.sweet.market.provider.security.Headers.SIGNATURE;'
    )) {
        Assert-True ([regex]::IsMatch($prohibitedImport, $backendPattern)) 'backend boundary must reject exact simulator namespaces including static imports'
    }
    foreach ($nonImport in @(
        '// import com.sweet.market.gateway.probe.ProbeController;',
        '/* import com.sweet.market.provider.probe.ProbeController; */',
        'String sample = "import com.sweet.market.gateway.probe.ProbeController;";',
        'import com.sweet.market.gatewayevil.Escape;'
    )) {
        Assert-True (-not [regex]::IsMatch($nonImport, $backendPattern)) 'backend boundary must ignore comments, strings, and prefixed non-simulator namespaces'
    }

    $scannerFunction = @($boundary.Ast.FindAll({
        param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Find-ProhibitedImports'
    }, $true) | Select-Object -First 1)
    $maskerFunction = @($boundary.Ast.FindAll({
        param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'ConvertTo-JavaImportView'
    }, $true) | Select-Object -First 1)
    $logicalLinesFunction = @($boundary.Ast.FindAll({
        param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Get-JavaLogicalLines'
    }, $true) | Select-Object -First 1)
    Invoke-Expression $scannerFunction[0].Extent.Text
    if ($maskerFunction.Count -eq 1) {
        Invoke-Expression $maskerFunction[0].Extent.Text
    }
    if ($logicalLinesFunction.Count -eq 1) {
        Invoke-Expression $logicalLinesFunction[0].Extent.Text
    }

    $fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) "m32-java-boundaries-$([Guid]::NewGuid().ToString('N'))"
    $originalRepositoryRoot = $repositoryRoot
    try {
        $paymentFixtureRoot = Join-Path $fixtureRoot 'payment-fixtures'
        $backendFixtureRoot = Join-Path $fixtureRoot 'backend-fixtures'
        [void][IO.Directory]::CreateDirectory($paymentFixtureRoot)
        [void][IO.Directory]::CreateDirectory($backendFixtureRoot)

        $paymentFixtures = [ordered]@{
            'Commented.java' = @'
package fixture;
/*
import com.sweet.market.provider.hidden.CommentOnly;
*/
final class Commented {}
'@
            'TextBlock.java' = @'
package fixture;
final class TextBlock {
    String sample = """
import static com.sweet.market.provider.hidden.TextOnly.VALUE;
""";
    String normal = "/* import com.sweet.market.provider.hidden.StringOnly; */";
    char quote = '"';
}
'@
            'TrailingLine.java' = @'
package fixture;
import com.sweet.market.provider.actual.LineComment; // real prohibited import
final class TrailingLine {}
'@
            'TrailingBlock.java' = @'
package fixture;
import static com.sweet.market.provider.actual.BlockComment.VALUE; /* real prohibited import */
final class TrailingBlock {}
'@
            'PrefixCollision.java' = @'
package fixture;
import com.sweet.market.gatewayevil.Collision;
final class PrefixCollision {}
'@
            'AllowedGateway.java' = @'
package fixture;
import com.sweet.market.gateway.probe.ProbeController;
import static com.sweet.market.gateway.security.Headers.SIGNATURE;
final class AllowedGateway {}
'@
            'CrOnly.java' = (@(
                'package fixture;', "`r",
                '// import com.sweet.market.provider.hidden.LineCommentOnly;', "`r",
                '/*', "`r",
                'import com.sweet.market.provider.hidden.BlockCommentOnly;', "`r",
                '*/', "`r",
                'import com.sweet.market.gateway.probe.AllowedGateway;', "`r",
                'import com.sweet.market.provider.actual.CrOnly; // real prohibited import', "`r",
                'final class CrOnly {}'
            ) -join '')
            'MixedTerminators.java' = (@(
                'package fixture;', "`r",
                'final class MixedTerminators { String sample = """', "`n",
                'import com.sweet.market.provider.hidden.TextOnly;', "`r`n",
                '""";', "`r",
                'import static com.sweet.market.gateway.security.Headers.SIGNATURE;', "`n",
                'import static com.sweet.market.provider.actual.Mixed.VALUE; /* real prohibited import */', "`r`n",
                '}'
            ) -join '')
        }
        foreach ($fixture in $paymentFixtures.GetEnumerator()) {
            [IO.File]::WriteAllText((Join-Path $paymentFixtureRoot $fixture.Key), $fixture.Value)
        }

        $backendFixtures = [ordered]@{
            'BackendCommented.java' = @'
package fixture;
/*
import com.sweet.market.gateway.hidden.CommentOnly;
import static com.sweet.market.provider.hidden.CommentOnly.VALUE;
*/
final class BackendCommented {}
'@
            'BackendTextBlock.java' = @'
package fixture;
final class BackendTextBlock {
    String sample = """
import com.sweet.market.gateway.hidden.TextOnly;
""";
    String normal = "import com.sweet.market.provider.hidden.StringOnly;";
    char slash = '/';
}
'@
            'BackendGateway.java' = @'
package fixture;
import com.sweet.market.gateway.actual.GatewayType; // real prohibited import
final class BackendGateway {}
'@
            'BackendProvider.java' = @'
package fixture;
import static com.sweet.market.provider.actual.ProviderType.VALUE; /* real prohibited import */
final class BackendProvider {}
'@
            'BackendPrefix.java' = @'
package fixture;
import com.sweet.market.gatewayevil.NotSimulator;
import com.sweet.market.providerfake.NotSimulatorEither;
final class BackendPrefix {}
'@
            'BackendCrOnly.java' = (@(
                'package fixture;', "`r",
                '// import com.sweet.market.gateway.hidden.LineCommentOnly;', "`r",
                '/*', "`r",
                'import static com.sweet.market.provider.hidden.BlockCommentOnly.VALUE;', "`r",
                '*/', "`r",
                'import com.sweet.market.integration.AllowedBackendType;', "`r",
                'import com.sweet.market.gateway.actual.CrOnly; // real prohibited import', "`r",
                'final class BackendCrOnly {}'
            ) -join '')
            'BackendMixedTerminators.java' = (@(
                'package fixture;', "`r",
                'final class BackendMixedTerminators { String sample = """', "`n",
                'import com.sweet.market.gateway.hidden.TextOnly;', "`r`n",
                '""";', "`r",
                'import com.sweet.market.integration.AllowedBackendType;', "`n",
                'import static com.sweet.market.provider.actual.Mixed.VALUE; /* real prohibited import */', "`r`n",
                '}'
            ) -join '')
        }
        foreach ($fixture in $backendFixtures.GetEnumerator()) {
            [IO.File]::WriteAllText((Join-Path $backendFixtureRoot $fixture.Key), $fixture.Value)
        }

        $repositoryRoot = $fixtureRoot
        $findings = [Collections.Generic.List[object]]::new()
        Find-ProhibitedImports 'payment-fixtures' $paymentPattern 'payment-fixture-rule'
        $paymentFindingFiles = @($findings | ForEach-Object File | Sort-Object)
        $expectedPaymentLines = @{
            'PrefixCollision.java' = 2
            'TrailingBlock.java' = 2
            'TrailingLine.java' = 2
            'CrOnly.java' = 7
            'MixedTerminators.java' = 6
        }
        $expectedPaymentFiles = @($expectedPaymentLines.Keys | Sort-Object)
        Assert-True (($paymentFindingFiles -join "`n") -ceq ($expectedPaymentFiles -join "`n")) 'payment lexical audit must ignore comments/text/string/char content and detect real trailing-comment imports plus prefix collisions'
        foreach ($finding in $findings) {
            $lineProperty = $finding.PSObject.Properties['Line']
            Assert-True ($null -ne $lineProperty -and $lineProperty.Value -eq $expectedPaymentLines[$finding.File]) 'payment lexical findings must preserve CRLF, lone CR, and lone LF source line association'
        }

        $findings = [Collections.Generic.List[object]]::new()
        Find-ProhibitedImports 'backend-fixtures' $backendPattern 'backend-fixture-rule'
        $backendFindingFiles = @($findings | ForEach-Object File | Sort-Object)
        $expectedBackendLines = @{
            'BackendGateway.java' = 2
            'BackendProvider.java' = 2
            'BackendCrOnly.java' = 7
            'BackendMixedTerminators.java' = 6
        }
        $expectedBackendFiles = @($expectedBackendLines.Keys | Sort-Object)
        Assert-True (($backendFindingFiles -join "`n") -ceq ($expectedBackendFiles -join "`n")) 'backend lexical audit must ignore multiline lexical content, reject exact simulator imports with trailing comments, and allow prefix namespaces'
        foreach ($finding in $findings) {
            $lineProperty = $finding.PSObject.Properties['Line']
            Assert-True ($null -ne $lineProperty -and $lineProperty.Value -eq $expectedBackendLines[$finding.File]) 'backend lexical findings must preserve CRLF, lone CR, and lone LF source line association'
        }
    } finally {
        $repositoryRoot = $originalRepositoryRoot
        Remove-Item -LiteralPath $fixtureRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

if ($null -ne $integration) {
    $functions = Get-FunctionNames $integration
    $commands = Get-Commands $integration
    $requiredFunctions = @(
        'Read-EnvironmentFile', 'Get-RequiredConfiguration', 'New-SignedHeaders',
        'Invoke-SignedRequest', 'Wait-ComposeHealth', 'Invoke-ChunkedOversizeRequest',
        'Assert-DatabaseIsolation', 'Assert-LogsContainNoSecrets', 'Invoke-Compose',
        'Restore-ProcessEnvironment'
    )
    foreach ($name in $requiredFunctions) {
        Assert-True ($functions -contains $name) "integration script must define $name"
    }
    $restoreFunction = @($integration.Ast.FindAll({
        param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Restore-ProcessEnvironment'
    }, $true) | Select-Object -First 1)
    if ($restoreFunction.Count -eq 1) {
        Invoke-Expression $restoreFunction[0].Extent.Text
        $restoreTestName = 'M32_ABSENT_RESTORE_TEST'
        Remove-Item -LiteralPath "Env:$restoreTestName" -ErrorAction SilentlyContinue
        $restoreRequiredVariables = @($restoreTestName)
        $restoreSnapshot = @{ $restoreTestName = $null }
        [Environment]::SetEnvironmentVariable($restoreTestName, 'temporary-placeholder', [EnvironmentVariableTarget]::Process)
        Restore-ProcessEnvironment -OriginalEnvironment $restoreSnapshot -Names $restoreRequiredVariables `
            -OriginalJavaHome ([Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')) `
            -OriginalPath ([Environment]::GetEnvironmentVariable('PATH', 'Process'))
        Assert-True (-not (Test-Path -LiteralPath "Env:$restoreTestName")) 'environment restoration must remove variables that were originally absent'
    }

    $databaseFunction = @($integration.Ast.FindAll({
        param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Assert-DatabaseIsolation'
    }, $true) | Select-Object -First 1)
    if ($databaseFunction.Count -eq 1) {
        Invoke-Expression $databaseFunction[0].Extent.Text
        $databaseConfiguration = [Collections.Generic.Dictionary[string, string]]::new([StringComparer]::Ordinal)
        $databaseConfiguration['MARKET_DB_NAME'] = 'market'
        $databaseConfiguration['PAYMENT_GATEWAY_DB_NAME'] = 'payment_gateway'
        $databaseConfiguration['DELIVERY_PROVIDER_DB_NAME'] = 'delivery_provider'
        $databaseConfiguration['MARKET_DB_PASSWORD'] = 'database-audit-secret-must-not-leak'
        $databaseAuditRequirements = [ordered]@{
            'market-postgres' = [pscustomobject]@{
                Database = 'market'
                Required = 'external_integration_request_replays'
                Forbidden = @('integration_request_replays')
            }
            'payment-postgres' = [pscustomobject]@{
                Database = 'payment_gateway'
                Required = 'integration_request_replays'
                Forbidden = @('external_integration_request_replays', 'members', 'products', 'orders', 'stores')
            }
            'delivery-postgres' = [pscustomobject]@{
                Database = 'delivery_provider'
                Required = 'integration_request_replays'
                Forbidden = @('external_integration_request_replays', 'members', 'products', 'orders', 'stores')
            }
        }
        $script:databaseAuditCalls = [Collections.Generic.List[object]]::new()
        $script:databaseAuditFailureService = $null
        $script:databaseAuditFailureTable = $null
        $script:databaseAuditWrongDatabaseService = $null
        $script:databaseAuditMalformedResultService = $null
        function Invoke-Compose {
            param(
                [Parameter(Mandatory)] [string[]]$Arguments,
                [switch]$Capture
            )

            $service = $Arguments[2]
            $requirement = $databaseAuditRequirements[$service]
            $forbiddenTablesAbsent = [ordered]@{}
            foreach ($table in $requirement.Forbidden) {
                $forbiddenTablesAbsent[$table] = -not (
                    $script:databaseAuditFailureService -ceq $service -and
                    $script:databaseAuditFailureTable -ceq $table
                )
            }
            $result = [ordered]@{
                database = if ($script:databaseAuditWrongDatabaseService -ceq $service) {
                    'unexpected_database'
                } else {
                    $requirement.Database
                }
                requiredTableExists = -not (
                    $script:databaseAuditFailureService -ceq $service -and
                    $script:databaseAuditFailureTable -ceq $requirement.Required
                )
                forbiddenTablesAbsent = $forbiddenTablesAbsent
            } | ConvertTo-Json -Compress
            $script:databaseAuditCalls.Add([pscustomobject]@{
                Arguments = $Arguments
                Capture = $Capture.IsPresent
            })
            if ($script:databaseAuditMalformedResultService -ceq $service) {
                return 'database-audit-secret-must-not-leak'
            }
            return $result
        }

        $successError = $null
        try { Assert-DatabaseIsolation $databaseConfiguration } catch { $successError = $_ }
        Assert-True ($null -eq $successError) 'database isolation must accept the owning database with its required table and no forbidden tables'
        Assert-True ($script:databaseAuditCalls.Count -eq 3) 'database isolation must execute one runtime catalog audit per owning database'
        foreach ($requirementEntry in $databaseAuditRequirements.GetEnumerator()) {
            $service = $requirementEntry.Key
            $requirement = $requirementEntry.Value
            $call = @($script:databaseAuditCalls | Where-Object { $_.Arguments[2] -ceq $service } | Select-Object -First 1)
            Assert-True ($call.Count -eq 1 -and $call[0].Capture) "database isolation must capture the runtime audit for $service"
            if ($call.Count -eq 1) {
                $query = $call[0].Arguments[-1]
                Assert-True ($query.Contains('current_database()')) "database isolation must query current_database for $service"
                Assert-True ($query.Contains("to_regclass('public.$($requirement.Required)')")) "database isolation must query the required table for $service"
                foreach ($table in $requirement.Forbidden) {
                    Assert-True ($query.Contains("to_regclass('public.$table')")) "database isolation must query forbidden table $table for $service"
                }
            }
        }

        $databaseAuditErrors = [Collections.Generic.List[string]]::new()
        foreach ($requirementEntry in $databaseAuditRequirements.GetEnumerator()) {
            $service = $requirementEntry.Key
            $requirement = $requirementEntry.Value
            foreach ($table in @($requirement.Required) + @($requirement.Forbidden)) {
                $script:databaseAuditFailureService = $service
                $script:databaseAuditFailureTable = $table
                $failure = $null
                try { Assert-DatabaseIsolation $databaseConfiguration } catch { $failure = $_ }
                Assert-True ($null -ne $failure) "database isolation must reject invalid table ownership for $service/$table"
                if ($null -ne $failure) {
                    $databaseAuditErrors.Add($failure.Exception.Message)
                }
            }
        }
        $script:databaseAuditFailureService = $null
        $script:databaseAuditFailureTable = $null

        $script:databaseAuditWrongDatabaseService = 'market-postgres'
        $identityFailure = $null
        try { Assert-DatabaseIsolation $databaseConfiguration } catch { $identityFailure = $_ }
        Assert-True ($null -ne $identityFailure) 'database isolation must reject a runtime database identity mismatch'
        if ($null -ne $identityFailure) {
            $databaseAuditErrors.Add($identityFailure.Exception.Message)
        }
        $script:databaseAuditWrongDatabaseService = $null

        $script:databaseAuditMalformedResultService = 'market-postgres'
        $malformedFailure = $null
        try { Assert-DatabaseIsolation $databaseConfiguration } catch { $malformedFailure = $_ }
        Assert-True ($null -ne $malformedFailure) 'database isolation must reject malformed runtime catalog output'
        if ($null -ne $malformedFailure) {
            $databaseAuditErrors.Add($malformedFailure.Exception.Message)
            Assert-True (-not $malformedFailure.Exception.Message.Contains('database-audit-secret-must-not-leak')) 'malformed catalog failures must not expose raw database output'
        }
        $script:databaseAuditMalformedResultService = $null

        Assert-True (-not (($databaseAuditErrors -join "`n").Contains($databaseConfiguration['MARKET_DB_PASSWORD']))) 'database isolation failures must not expose database credentials'
        Assert-True (-not $databaseFunction[0].Extent.Text.Contains('Write-Output')) 'database isolation must not print raw database audit results'
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
    $cleanupTry = @($finallyBlocks | Where-Object { $_.Finally.Extent.Text.Contains("'down'") } | Select-Object -First 1)
    Assert-True ($cleanupTry.Count -eq 1) 'the executable outer cleanup scope must contain compose down'
    $readEnvironmentCalls = @($commands | Where-Object {
        $_.GetCommandName() -eq 'Read-EnvironmentFile' -and -not (Test-HasFunctionAncestor $_)
    })
    Assert-True ($readEnvironmentCalls.Count -eq 1 -and
        $readEnvironmentCalls[0].Extent.StartOffset -gt $cleanupTry[0].Body.Extent.StartOffset -and
        $readEnvironmentCalls[0].Extent.EndOffset -lt $cleanupTry[0].Body.Extent.EndOffset) 'environment parsing must execute inside the outer cleanup try body'
    $processEnvironmentMutations = @($integration.Ast.FindAll({
        param($node)
        $node -is [Management.Automation.Language.InvokeMemberExpressionAst] -and
            $node.Member.Value -eq 'SetEnvironmentVariable' -and -not (Test-HasFunctionAncestor $node)
    }, $true))
    foreach ($mutation in $processEnvironmentMutations) {
        Assert-True ($mutation.Extent.StartOffset -gt $cleanupTry[0].Extent.StartOffset -and
            $mutation.Extent.EndOffset -lt $cleanupTry[0].Extent.EndOffset) 'process environment mutations must execute inside the outer cleanup scope'
    }
    $snapshotAssignment = @($integration.Ast.FindAll({
        param($node)
        $node -is [Management.Automation.Language.AssignmentStatementAst] -and
            $node.Left.Extent.Text -eq '$originalEnvironment'
    }, $true) | Select-Object -First 1)
    Assert-True ($snapshotAssignment.Count -eq 1 -and
        $snapshotAssignment[0].Extent.StartOffset -lt $cleanupTry[0].Extent.StartOffset) 'environment snapshot storage must be created before the cleanup scope mutates anything'
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

    $executableSteps = @($commands | Where-Object {
        $_.GetCommandName() -eq 'Invoke-CheckedStep' -and -not (Test-HasFunctionAncestor $_)
    } | Sort-Object { $_.Extent.StartOffset })
    $actualStepNames = @($executableSteps | ForEach-Object { [string]$_.CommandElements[1].SafeGetValue() })
    Assert-True ($actualStepNames.Count -eq $orderedSteps.Count) 'the executable main flow must contain exactly the required checked steps'
    Assert-True (($actualStepNames -join "`n") -ceq (($orderedSteps.Keys) -join "`n")) 'the executable main flow must use the exact required step order'

    $stepByName = @{}
    foreach ($step in $executableSteps) {
        $stepByName[[string]$step.CommandElements[1].SafeGetValue()] = $step
    }
    $replayRequest = @($stepByName['replay negative'].FindAll({
        param($node) $node -is [Management.Automation.Language.CommandAst] -and $node.GetCommandName() -eq 'Invoke-SignedRequest'
    }, $true))
    Assert-True ($replayRequest.Count -eq 1) 'replay negative must execute exactly one signed request'
    Assert-True ((Get-CommandParameterValueText $replayRequest[0] 'ExpectedStatus') -eq '409') 'replay negative must assert HTTP 409 on its executable request'
    Assert-True ((Get-CommandParameterValueText $replayRequest[0] 'RequestId') -eq '$paymentRequestId') 'replay negative must reuse the original payment request ID'
    Assert-True ((Get-CommandParameterValueText $replayRequest[0] 'Body') -eq '$paymentBody') 'replay negative must reuse the original payment body bytes'
    Assert-True ((Get-CommandParameterValueText $replayRequest[0] 'Timestamp') -eq '$paymentTimestamp') 'replay negative must reuse the original payment timestamp'

    $mutatedRequest = @($stepByName['mutated body negative'].FindAll({
        param($node) $node -is [Management.Automation.Language.CommandAst] -and $node.GetCommandName() -eq 'Invoke-SignedRequest'
    }, $true))
    Assert-True ($mutatedRequest.Count -eq 1) 'mutated-body negative must execute exactly one signed request'
    Assert-True ((Get-CommandParameterValueText $mutatedRequest[0] 'ExpectedStatus') -eq '401') 'mutated-body negative must assert HTTP 401'
    Assert-True ((Get-CommandParameterValueText $mutatedRequest[0] 'Body') -eq '$mutatedBody') 'mutated-body negative must send the mutated bytes'
    Assert-True ((Get-CommandParameterValueText $mutatedRequest[0] 'SigningBody') -eq '$signedBody') 'mutated-body negative must sign different original bytes'

    $expiredRequest = @($stepByName['expired timestamp negative'].FindAll({
        param($node) $node -is [Management.Automation.Language.CommandAst] -and $node.GetCommandName() -eq 'Invoke-SignedRequest'
    }, $true))
    Assert-True ($expiredRequest.Count -eq 1) 'expired negative must execute exactly one signed request'
    Assert-True ((Get-CommandParameterValueText $expiredRequest[0] 'ExpectedStatus') -eq '401') 'expired negative must assert HTTP 401'
    Assert-True ((Get-CommandParameterValueText $expiredRequest[0] 'Timestamp') -match 'AddSeconds\(-301\)') 'expired negative must send a timestamp 301 seconds old'

    $oversizeCalls = @($stepByName['chunked oversize negative'].FindAll({
        param($node) $node -is [Management.Automation.Language.CommandAst] -and $node.GetCommandName() -eq 'Invoke-ChunkedOversizeRequest'
    }, $true))
    Assert-True ($oversizeCalls.Count -eq 1) 'oversize negative must execute the chunked 1 MiB plus one request helper'

    $restartRequests = @($stepByName['simulator restart replay persistence'].FindAll({
        param($node) $node -is [Management.Automation.Language.CommandAst] -and $node.GetCommandName() -eq 'Invoke-SignedRequest'
    }, $true))
    Assert-True ($restartRequests.Count -eq 2) 'restart persistence must execute one replay request per simulator'
    Assert-True ((Get-CommandParameterValueText $restartRequests[0] 'ExpectedStatus') -eq '409' -and
        (Get-CommandParameterValueText $restartRequests[1] 'ExpectedStatus') -eq '409') 'restart replay requests must both assert HTTP 409'
    Assert-True ((Get-CommandParameterValueText $restartRequests[0] 'RequestId') -eq '$paymentRequestId' -and
        (Get-CommandParameterValueText $restartRequests[1] 'RequestId') -eq '$deliveryRequestId') 'restart replay requests must reuse both original request IDs'
    Assert-True ((Get-CommandParameterValueText $restartRequests[0] 'Body') -eq '$paymentBody' -and
        (Get-CommandParameterValueText $restartRequests[1] 'Body') -eq '$deliveryBody') 'restart replay requests must reuse both original bodies'

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

    $behaviorRoot = Join-Path ([IO.Path]::GetTempPath()) "m32-script-behavior-$([Guid]::NewGuid().ToString('N'))"
    $mockBin = Join-Path $behaviorRoot 'bin'
    $invalidEnvPath = Join-Path $behaviorRoot 'invalid.env'
    $dockerCallLog = Join-Path $behaviorRoot 'docker-calls.log'
    $downHarnessPath = Join-Path $behaviorRoot 'down-failure.ps1'
    $originalBehaviorPath = [Environment]::GetEnvironmentVariable('PATH', [EnvironmentVariableTarget]::Process)
    $originalCallLog = [Environment]::GetEnvironmentVariable('M32_DOCKER_CALL_LOG', [EnvironmentVariableTarget]::Process)
    $originalMockExit = [Environment]::GetEnvironmentVariable('M32_MOCK_DOCKER_EXIT', [EnvironmentVariableTarget]::Process)
    try {
        [void][IO.Directory]::CreateDirectory($mockBin)
        $dockerMock = @'
@echo off
echo %*>>"%M32_DOCKER_CALL_LOG%"
exit /b %M32_MOCK_DOCKER_EXIT%
'@
        [IO.File]::WriteAllText((Join-Path $mockBin 'docker.cmd'), $dockerMock)
        [IO.File]::WriteAllText($invalidEnvPath, "JWT_SECRET=first`nJWT_SECRET=duplicate`n")
        [Environment]::SetEnvironmentVariable('PATH', "$mockBin;$originalBehaviorPath", [EnvironmentVariableTarget]::Process)
        [Environment]::SetEnvironmentVariable('M32_DOCKER_CALL_LOG', $dockerCallLog, [EnvironmentVariableTarget]::Process)
        [Environment]::SetEnvironmentVariable('M32_MOCK_DOCKER_EXIT', '0', [EnvironmentVariableTarget]::Process)
        $invalidOutput = @(& pwsh -NoProfile -File $integrationPath -EnvFile $invalidEnvPath 2>&1)
        $invalidExit = $LASTEXITCODE
        $dockerCalls = if (Test-Path -LiteralPath $dockerCallLog) { [IO.File]::ReadAllText($dockerCallLog) } else { '' }
        Assert-True ($invalidExit -ne 0) 'invalid environment input must exit nonzero'
        Assert-True ((($invalidOutput | ForEach-Object ToString) -join "`n").Contains('duplicate environment name')) 'invalid input must preserve its primary validation failure after cleanup'
        Assert-True ($dockerCalls -match '(?m)\bcompose\b.*\bdown\b') 'invalid environment input must still invoke docker compose down'

        $downCommand = @($cleanupTry[0].Finally.FindAll({
            param($node) $node -is [Management.Automation.Language.CommandAst] -and $node.GetCommandName() -eq 'Invoke-Compose'
        }, $true) | Select-Object -First 1)
        $composeFunction = @($integration.Ast.FindAll({
            param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Invoke-Compose'
        }, $true) | Select-Object -First 1)
        $harness = @"
`$ErrorActionPreference = 'Stop'
`$composeFile = 'mock-compose.yml'
$($composeFunction[0].Extent.Text)
function docker { `$global:LASTEXITCODE = 7 }
$($downCommand[0].Extent.Text)
"@
        [IO.File]::WriteAllText($downHarnessPath, $harness)
        & pwsh -NoProfile -File $downHarnessPath *> $null
        Assert-True ($LASTEXITCODE -ne 0) 'docker compose down exit 7 must make the cleanup harness exit nonzero'
    } finally {
        [Environment]::SetEnvironmentVariable('PATH', $originalBehaviorPath, [EnvironmentVariableTarget]::Process)
        [Environment]::SetEnvironmentVariable('M32_DOCKER_CALL_LOG', $originalCallLog, [EnvironmentVariableTarget]::Process)
        [Environment]::SetEnvironmentVariable('M32_MOCK_DOCKER_EXIT', $originalMockExit, [EnvironmentVariableTarget]::Process)
        Remove-Item -LiteralPath $behaviorRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

if ($script:Failures.Count -gt 0) {
    Write-Error ("M32 script verification failed ({0}/{1} checks):`n - {2}" -f $script:Failures.Count, $script:Checks, ($script:Failures -join "`n - "))
    exit 1
}

Write-Output "M32 script verification passed ($script:Checks checks)."
