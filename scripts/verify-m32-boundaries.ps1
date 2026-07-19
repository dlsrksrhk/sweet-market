[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$findings = [System.Collections.Generic.List[object]]::new()

function ConvertTo-JavaImportView {
    param(
        [Parameter(Mandatory)]
        [AllowEmptyString()]
        [string]$Source
    )

    $view = [Text.StringBuilder]::new($Source.Length)
    $state = 'Code'
    $escaped = $false
    $index = 0
    while ($index -lt $Source.Length) {
        $character = $Source[$index]
        $next = if ($index + 1 -lt $Source.Length) { $Source[$index + 1] } else { [char]0 }
        $hasTripleQuote = $index + 2 -lt $Source.Length -and
            $character -eq '"' -and $next -eq '"' -and $Source[$index + 2] -eq '"'

        if ($state -eq 'Code') {
            if ($character -eq '/' -and $next -eq '/') {
                [void]$view.Append('  ')
                $state = 'LineComment'
                $index += 2
                continue
            }
            if ($character -eq '/' -and $next -eq '*') {
                [void]$view.Append('  ')
                $state = 'BlockComment'
                $index += 2
                continue
            }
            if ($hasTripleQuote) {
                [void]$view.Append('   ')
                $state = 'TextBlock'
                $escaped = $false
                $index += 3
                continue
            }
            if ($character -eq '"') {
                [void]$view.Append(' ')
                $state = 'String'
                $escaped = $false
                $index++
                continue
            }
            if ($character -eq "'") {
                [void]$view.Append(' ')
                $state = 'Char'
                $escaped = $false
                $index++
                continue
            }
            [void]$view.Append($character)
            $index++
            continue
        }

        if ($state -eq 'LineComment') {
            if ($character -eq "`r" -or $character -eq "`n") {
                [void]$view.Append($character)
                $state = 'Code'
            } else {
                [void]$view.Append(' ')
            }
            $index++
            continue
        }

        if ($state -eq 'BlockComment') {
            if ($character -eq '*' -and $next -eq '/') {
                [void]$view.Append('  ')
                $state = 'Code'
                $index += 2
                continue
            }
            if ($character -eq "`r" -or $character -eq "`n") {
                [void]$view.Append($character)
            } else {
                [void]$view.Append(' ')
            }
            $index++
            continue
        }

        if ($state -eq 'TextBlock') {
            if ($escaped) {
                [void]$view.Append($(if ($character -eq "`r" -or $character -eq "`n") { $character } else { ' ' }))
                $escaped = $false
                $index++
                continue
            }
            if ($character -eq '\') {
                [void]$view.Append(' ')
                $escaped = $true
                $index++
                continue
            }
            if ($hasTripleQuote) {
                [void]$view.Append('   ')
                $state = 'Code'
                $index += 3
                continue
            }
            if ($character -eq "`r" -or $character -eq "`n") {
                [void]$view.Append($character)
            } else {
                [void]$view.Append(' ')
            }
            $index++
            continue
        }

        if ($character -eq "`r" -or $character -eq "`n") {
            [void]$view.Append($character)
            $state = 'Code'
            $escaped = $false
        } elseif ($escaped) {
            [void]$view.Append(' ')
            $escaped = $false
        } elseif ($character -eq '\') {
            [void]$view.Append(' ')
            $escaped = $true
        } elseif (($state -eq 'String' -and $character -eq '"') -or
            ($state -eq 'Char' -and $character -eq "'")) {
            [void]$view.Append(' ')
            $state = 'Code'
        } else {
            [void]$view.Append(' ')
        }
        $index++
    }
    return $view.ToString()
}

function Get-JavaLogicalLines {
    param(
        [Parameter(Mandatory)]
        [AllowEmptyString()]
        [string]$Source
    )

    $lines = [Collections.Generic.List[object]]::new()
    $lineStart = 0
    $lineNumber = 1
    $index = 0
    while ($index -lt $Source.Length) {
        $character = $Source[$index]
        if ($character -ne "`r" -and $character -ne "`n") {
            $index++
            continue
        }

        $lines.Add([pscustomobject]@{
            Text = $Source.Substring($lineStart, $index - $lineStart)
            Number = $lineNumber
        })
        if ($character -eq "`r" -and $index + 1 -lt $Source.Length -and $Source[$index + 1] -eq "`n") {
            $index++
        }
        $index++
        $lineStart = $index
        $lineNumber++
    }
    $lines.Add([pscustomobject]@{
        Text = $Source.Substring($lineStart)
        Number = $lineNumber
    })
    return $lines
}

function Find-ProhibitedImports {
    param(
        [Parameter(Mandatory)] [string]$RelativeSourceRoot,
        [Parameter(Mandatory)] [string]$Pattern,
        [Parameter(Mandatory)] [string]$Rule
    )

    $sourceRoot = Join-Path $repositoryRoot $RelativeSourceRoot
    Get-ChildItem -LiteralPath $sourceRoot -Recurse -File -Filter '*.java' | ForEach-Object {
        $sourceFile = $_
        $importView = ConvertTo-JavaImportView ([IO.File]::ReadAllText($sourceFile.FullName))
        foreach ($line in Get-JavaLogicalLines $importView) {
            if (-not [regex]::IsMatch($line.Text, $Pattern)) {
                continue
            }
            $findings.Add([pscustomobject]@{
                Rule = $Rule
                File = $sourceFile.Name
                Line = $line.Number
            })
        }
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

Find-ProhibitedImports 'mock-payment-gateway\src' '^\s*import\s+(?:static\s+)?com\.sweet\.market\.(?!gateway(?:\.|;))[A-Za-z_$][\w$]*(?:\.[A-Za-z_$*][\w$*]*)*;\s*$' 'payment-gateway-package-boundary'
Find-ProhibitedImports 'mock-delivery-provider\src' '^\s*import\s+(?:static\s+)?com\.sweet\.market\.(?!provider(?:\.|;))[A-Za-z_$][\w$]*(?:\.[A-Za-z_$*][\w$*]*)*;\s*$' 'delivery-provider-package-boundary'
Find-ProhibitedImports 'backend\src' '^\s*import\s+(?:static\s+)?com\.sweet\.market\.(?:gateway(?:\.|;)|provider(?:\.|;))(?:[A-Za-z_$*][\w$]*(?:\.[A-Za-z_$*][\w$]*)*;)?\s*$' 'backend-simulator-package-boundary'

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
