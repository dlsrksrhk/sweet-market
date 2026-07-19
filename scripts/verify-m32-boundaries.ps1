[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$findings = [System.Collections.Generic.List[object]]::new()

function Find-ProhibitedImports {
    param(
        [Parameter(Mandatory)] [string]$RelativeSourceRoot,
        [Parameter(Mandatory)] [string]$Pattern,
        [Parameter(Mandatory)] [string]$Rule
    )

    $sourceRoot = Join-Path $repositoryRoot $RelativeSourceRoot
    Get-ChildItem -LiteralPath $sourceRoot -Recurse -File -Filter '*.java' |
        Select-String -Pattern $Pattern -CaseSensitive | ForEach-Object {
            $findings.Add([pscustomobject]@{
                Rule = $Rule
                File = [IO.Path]::GetFileName($_.Path)
            })
        }
}

function Get-ConfiguredDatabaseName {
    param(
        [Parameter(Mandatory)] [string]$RelativeApplicationPath,
        [Parameter(Mandatory)] [string]$Rule
    )

    $applicationPath = Join-Path $repositoryRoot $RelativeApplicationPath
    $jdbcLine = Select-String -LiteralPath $applicationPath -Pattern '^\s*url:\s*jdbc:postgresql:' |
        Select-Object -First 1
    if ($null -eq $jdbcLine) {
        $findings.Add([pscustomobject]@{ Rule = $Rule; File = [IO.Path]::GetFileName($applicationPath) })
        return $null
    }

    $match = [regex]::Match($jdbcLine.Line, '/\$\{[A-Z0-9_]+:([^}]+)\}\s*$')
    if (-not $match.Success -or [string]::IsNullOrWhiteSpace($match.Groups[1].Value)) {
        $findings.Add([pscustomobject]@{ Rule = $Rule; File = [IO.Path]::GetFileName($applicationPath) })
        return $null
    }
    return $match.Groups[1].Value
}

Find-ProhibitedImports 'mock-payment-gateway\src' 'import com\.sweet\.market\.(?!gateway)' 'payment-gateway-package-boundary'
Find-ProhibitedImports 'mock-delivery-provider\src' 'import com\.sweet\.market\.(?!provider)' 'delivery-provider-package-boundary'
Find-ProhibitedImports 'backend\src' 'com\.sweet\.market\.(gateway|provider)' 'backend-simulator-package-boundary'

$databases = @(
    [pscustomobject]@{
        Name = Get-ConfiguredDatabaseName 'backend\src\main\resources\application.yaml' 'market-database-configuration'
        File = 'application.yaml'
    }
    [pscustomobject]@{
        Name = Get-ConfiguredDatabaseName 'mock-payment-gateway\src\main\resources\application.yaml' 'payment-database-configuration'
        File = 'application.yaml'
    }
    [pscustomobject]@{
        Name = Get-ConfiguredDatabaseName 'mock-delivery-provider\src\main\resources\application.yaml' 'delivery-database-configuration'
        File = 'application.yaml'
    }
) | Where-Object { $null -ne $_.Name }

$databases | Group-Object -Property Name | Where-Object { $_.Count -gt 1 } | ForEach-Object {
    foreach ($database in $_.Group) {
        $findings.Add([pscustomobject]@{
            Rule = 'database-name-isolation'
            File = $database.File
        })
    }
}

if ($findings.Count -gt 0) {
    foreach ($finding in $findings) {
        Write-Output ("boundary violation: rule={0}; file={1}" -f $finding.Rule, $finding.File)
    }
    exit 1
}

Write-Output 'M32 boundary audit passed.'
