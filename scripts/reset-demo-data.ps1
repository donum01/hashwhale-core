[CmdletBinding()]
param(
    [string]$Email = "demo@hashwhale.com"
)

$backendRoot = Split-Path -Parent $PSScriptRoot
$mavenWrapper = Join-Path $backendRoot "mvnw.cmd"

if (-not (Test-Path -LiteralPath $mavenWrapper)) {
    throw "Maven wrapper was not found at $mavenWrapper"
}

$mavenCommand = Get-Command "mvn.cmd" -ErrorAction SilentlyContinue
$mavenExecutable = if ($null -eq $mavenCommand) { $null } else { $mavenCommand.Source }
if ($null -eq $mavenExecutable) {
    $mavenUserHome = if ([string]::IsNullOrWhiteSpace($env:MAVEN_USER_HOME)) {
        Join-Path $env:USERPROFILE ".m2"
    }
    else {
        $env:MAVEN_USER_HOME
    }
    $wrapperDistributions = Join-Path $mavenUserHome "wrapper\dists"
    if (Test-Path -LiteralPath $wrapperDistributions) {
        $mavenCandidate = Get-ChildItem -LiteralPath $wrapperDistributions -Filter "mvn.cmd" -File -Recurse |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if ($null -ne $mavenCandidate) {
            $mavenExecutable = $mavenCandidate.FullName
        }
    }
}
if ($null -eq $mavenExecutable) {
    $mavenExecutable = $mavenWrapper
}

Write-Warning "This will permanently replace every user and all user-owned wallet, loan, Earn, and transaction data."
$confirmation = Read-Host "Type RESET to continue"
if ($confirmation -cne "RESET") {
    Write-Host "Demo reset cancelled. No data was changed."
    exit 0
}

$securePassword = Read-Host "Enter the password for $Email" -AsSecureString
$credential = [System.Management.Automation.PSCredential]::new($Email, $securePassword)
$plainPassword = $credential.GetNetworkCredential().Password
if ($plainPassword.Length -lt 8 -or $plainPassword.Length -gt 72) {
    throw "The demo password must contain between 8 and 72 characters."
}

$environmentNames = @(
    "DEMO_SEED_ENABLED",
    "DEMO_SEED_RESET",
    "DEMO_USER_EMAIL",
    "DEMO_USER_PASSWORD",
    "JWT_SECRET"
)
$previousEnvironment = @{}
foreach ($name in $environmentNames) {
    $previousEnvironment[$name] = [System.Environment]::GetEnvironmentVariable($name, "Process")
}
$locationPushed = $false

try {
    $env:DEMO_SEED_ENABLED = "true"
    $env:DEMO_SEED_RESET = "true"
    $env:DEMO_USER_EMAIL = $Email
    $env:DEMO_USER_PASSWORD = $plainPassword

    if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
        $jwtBytes = [byte[]]::new(32)
        [System.Security.Cryptography.RandomNumberGenerator]::Fill($jwtBytes)
        $env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
    }

    Write-Host "Starting HashWhale with the guarded demo reset enabled..."
    Push-Location -LiteralPath $backendRoot
    $locationPushed = $true
    & $mavenExecutable spring-boot:run "-Dspring-boot.run.profiles=demo"
    if ($LASTEXITCODE -ne 0) {
        throw "The backend exited with code $LASTEXITCODE. Review the Spring Boot output above."
    }
}
finally {
    if ($locationPushed) {
        Pop-Location
    }
    $plainPassword = $null
    $credential = $null
    $securePassword = $null

    foreach ($name in $environmentNames) {
        $previousValue = $previousEnvironment[$name]
        if ($null -eq $previousValue) {
            Remove-Item -Path "Env:$name" -ErrorAction SilentlyContinue
        }
        else {
            [System.Environment]::SetEnvironmentVariable($name, $previousValue, "Process")
        }
    }
}
